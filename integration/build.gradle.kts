plugins { kotlin("jvm") version "2.1.20" }
kotlin { jvmToolchain(17) }
sourceSets {
    main {
        kotlin.srcDir("../app/src/main/java/pt/atlas/personal/ai")
        kotlin.include("ModelCompatibility.kt", "ModelOutput.kt", "ChatHistory.kt", "Turn.kt")
    }
    test {
        kotlin.srcDir("../app/src/test/java/pt/atlas/personal")
        kotlin.include("ModelProtocolTest.kt", "GeneratedModelIntegrationTest.kt")
    }
}
dependencies { testImplementation("junit:junit:4.13.2"); testImplementation("org.json:json:20240303") }
tasks.test {
    useJUnit()
    outputs.upToDateWhen { false }
    testLogging { events("passed", "skipped", "failed"); showStandardStreams = true }
}
