package rocks.chirag.adwizz.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build

/**
 * Broadcasts the running service listens to: Spotify's playback reports (for muting its ads) and
 * AdWizz being uninstalled (then the add-on removes itself too).
 */
internal class AddonReceivers(
    private val context: Context,
    private val spotify: SpotifyAdDetector,
    private val onMainAppRemoved: () -> Unit,
) {
    private val spotifyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val now = System.currentTimeMillis()
            if (BuildConfig.DEBUG) android.util.Log.d("AdWizzSpotify", "${i.action} ${i.extras?.keySet()?.associateWith { i.extras?.get(it) }}")
            when (i.action) {
                AdRules.SPOTIFY_METADATA -> spotify.onMetadata(i.getStringExtra("id"), lengthOf(i), now)
                AdRules.SPOTIFY_PLAYBACK -> spotify.onPlayback(
                    i.getBooleanExtra("playing", false),
                    i.getIntExtra("playbackPosition", 0).toLong(),
                    i.getLongExtra("timeSent", now).takeIf { it > 0 } ?: now,
                    now,
                )
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.data?.schemeSpecificPart != BuildConfig.MAIN_APP_ID) return
            if (i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return   // an AdWizz update, not a removal
            onMainAppRemoved()
        }
    }

    private var spotifyOn = false

    fun start() {
        register(packageReceiver, IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply { addDataScheme("package") })
    }

    /** Spotify's broadcasts only while "Mute Spotify ads" is on. */
    fun listenToSpotify(on: Boolean) {
        if (on == spotifyOn) return
        spotifyOn = on
        if (on) register(spotifyReceiver, IntentFilter().apply {
            addAction(AdRules.SPOTIFY_METADATA); addAction(AdRules.SPOTIFY_PLAYBACK)
        }) else {
            runCatching { context.unregisterReceiver(spotifyReceiver) }
            spotify.stop()
        }
    }

    fun stop() {
        listenToSpotify(false)
        runCatching { context.unregisterReceiver(packageReceiver) }
    }

    private fun register(r: BroadcastReceiver, f: IntentFilter) {
        // Spotify is another app, so its broadcasts need an exported receiver.
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(r, f, Context.RECEIVER_EXPORTED)
        else context.registerReceiver(r, f)
    }

    private fun lengthOf(i: Intent): Long = i.extras?.get("length").let { (it as? Number)?.toLong() } ?: 0L

    companion object {
        /** Android's "Uninstall this app?" screen for the add-on itself. */
        fun uninstallSelf(context: Context): Intent =
            Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
