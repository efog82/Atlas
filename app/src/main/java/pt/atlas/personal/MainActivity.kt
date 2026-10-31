package pt.atlas.personal

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import pt.atlas.personal.ui.*

class MainActivity : FragmentActivity() {
    private val model: AtlasViewModel by viewModels()
    private var unlocked by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        unlocked = !(application as AtlasApp).settings.state.value.lock
        setContent {
            val prefs by model.settings.collectAsState()
            val holder = rememberSaveableStateHolder()
            AtlasTheme(prefs.theme) {
                if (prefs.lock && !unlocked) LockScreen { authenticate { unlocked = true } }
                else holder.SaveableStateProvider("atlas") {
                    AtlasScreen(model) { enable ->
                        if (enable) authenticate { unlocked = true; model.app.settings.update(prefs.copy(lock = true)) }
                        else authenticate { model.app.settings.update(prefs.copy(lock = false)) }
                    }
                }
            }
        }
    }
    private fun authenticate(onSuccess: () -> Unit) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            model.tell("Configura primeiro um PIN, padrão, palavra-passe ou biometria nas definições Android.")
            android.widget.Toast.makeText(this, "Configura um bloqueio de ecrã nas definições Android.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                android.widget.Toast.makeText(this@MainActivity, errString, android.widget.Toast.LENGTH_SHORT).show()
            }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Atlas Personal")
            .setSubtitle("Desbloqueia os teus dados locais").setAllowedAuthenticators(authenticators).build())
    }
    override fun onStop() {
        model.stop()
        if ((application as AtlasApp).settings.state.value.lock) unlocked = false
        super.onStop()
    }
}
