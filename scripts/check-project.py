#!/usr/bin/env python3
"""Offline source/configuration checks. These do NOT compile or execute Android/Kotlin."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"

class ProjectChecks(unittest.TestCase):
    def test_xml_resources_and_manifest_parse(self):
        paths = list((ROOT / "app/src/main").rglob("*.xml"))
        self.assertGreaterEqual(len(paths), 3)
        for path in paths:
            with self.subTest(path=path):
                ET.parse(path)

    def test_manifest_privacy_and_components(self):
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        permissions = {p.get(ANDROID + "name") for p in manifest.findall("uses-permission")}
        self.assertIn("android.permission.INTERNET", permissions)
        self.assertIn("android.permission.ACCESS_NETWORK_STATE", permissions)
        transport = (ROOT / "app/src/main/java/pt/atlas/personal/knowledge/WikiApi.kt").read_text()
        self.assertIn("check(permitted())", transport)
        self.assertIn("instanceFollowRedirects = false", transport)
        self.assertIn("https://pt.wikipedia.org/w/api.php", transport)
        self.assertNotIn("android.permission.READ_EXTERNAL_STORAGE", permissions)
        application = manifest.find("application")
        self.assertEqual("false", application.get(ANDROID + "allowBackup"))
        self.assertEqual("false", application.get(ANDROID + "usesCleartextTraffic"))
        self.assertEqual("true", application.find("activity").get(ANDROID + "exported"))
        for receiver in application.findall("receiver"):
            self.assertEqual("false", receiver.get(ANDROID + "exported"))

    def test_native_jni_methods_have_matching_implementations(self):
        kotlin = (ROOT / "app/src/main/java/pt/atlas/personal/ai/LocalEngine.kt").read_text()
        cpp = (ROOT / "app/src/main/cpp/atlas.cpp").read_text()
        methods = re.findall(r"external fun (native\w+)\(", kotlin)
        self.assertEqual(10, len(methods))
        for name in methods:
            self.assertIn("Java_pt_atlas_personal_ai_LocalEngine_" + name, cpp)
        self.assertIn("llama_model_load_from_file", cpp)
        self.assertIn("llama_decode", cpp)
        self.assertIn("llama_sampler_sample", cpp)

    def test_required_source_and_tests_exist(self):
        base = ROOT / "app/src/main/java/pt/atlas/personal"
        for path in ["MainActivity.kt", "data/AtlasDatabase.kt", "agent/AgentTools.kt", "security/BackupService.kt", "study/DocumentImporter.kt"]:
            self.assertTrue((base / path).is_file())
        self.assertGreaterEqual(len(list((ROOT / "app/src/test").rglob("*Test.kt"))), 3)
        self.assertGreaterEqual(len(list((ROOT / "app/src/androidTest").rglob("*Test.kt"))), 4)

    def test_android_version_abi_and_no_dynamic_dependency_versions(self):
        build = (ROOT / "app/build.gradle.kts").read_text()
        self.assertIn("minSdk = 33", build)
        self.assertIn('abiFilters += "arm64-v8a"', build)
        self.assertNotRegex(build, r':[\d.]+\+|SNAPSHOT|latest\.release')
        self.assertIn('"8.9.2"', (ROOT / "build.gradle.kts").read_text())

    def test_write_tool_entry_point_is_explicit_confirmation(self):
        base = ROOT / "app/src/main/java/pt/atlas/personal"
        vm = (base / "ui/AtlasViewModel.kt").read_text()
        ui = (base / "ui/ChatScreen.kt").read_text()
        self.assertEqual(1, vm.count("tools.confirm("))
        self.assertIn("fun confirmProposal()", vm)
        self.assertIn("onClick = vm::confirmProposal", ui)
        self.assertIn('require(call.tool !in ToolProtocol.writes)', (base / "agent/AgentTools.kt").read_text())

    def test_workflow_produces_debug_artifact(self):
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        self.assertIn("testDebugUnitTest lintDebug assembleDebug", workflow)
        self.assertIn("actions/upload-artifact@v4", workflow)
        self.assertIn("app/build/outputs/apk/debug/*.apk", workflow)

if __name__ == "__main__":
    unittest.main(verbosity=2)
