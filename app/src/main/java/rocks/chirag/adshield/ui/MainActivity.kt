package rocks.chirag.adshield.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import android.graphics.Color
import rocks.chirag.adshield.data.Settings
import rocks.chirag.adshield.data.Status
import rocks.chirag.adshield.data.VpnState
import rocks.chirag.adshield.vpn.AdBlockVpnService

class MainActivity : ComponentActivity() {

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            AdBlockVpnService.send(this, AdBlockVpnService.ACTION_START)
        } else {
            Toast.makeText(this, "VPN permission is needed to block ads", Toast.LENGTH_LONG).show()
        }
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.mode.value = ThemeMode.from(Settings(this).themeMode)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val dark = isAppInDarkTheme()
            // Keep status/nav bar icons readable when the app theme differs from the system one.
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(style, style)
            }
            ThemeRevealHost(onApply = { applyTheme(this, it) }) {
                AdShieldTheme {
                    AppScreen(onToggle = ::toggle)
                }
            }
        }
    }

    private fun toggle() {
        when (VpnState.status.value) {
            Status.RUNNING, Status.STARTING -> AdBlockVpnService.send(this, AdBlockVpnService.ACTION_STOP)
            else -> {
                val consent = VpnService.prepare(this)
                if (consent != null) vpnConsent.launch(consent)
                else AdBlockVpnService.send(this, AdBlockVpnService.ACTION_START)
            }
        }
    }
}
