package rocks.chirag.adwizz.data

import android.content.Context
import rocks.chirag.adwizz.focus.FocusStateProvider

/** "Pause for 15 minutes" for the ad blocker and for the add-on. Both resume on their own. */
object ProtectionPause {
    const val FOREVER = Long.MAX_VALUE

    /** Pauses filtering for [minutes] (null = until resumed). The VPN stays up, so nothing reconnects. */
    fun pauseBlocking(context: Context, minutes: Int?, now: Long = System.currentTimeMillis()) {
        val until = minutes?.let { now + it * 60_000L } ?: FOREVER
        Settings(context).pausedUntil = until
        VpnState.setPausedUntil(until)
    }

    fun resumeBlocking(context: Context) {
        Settings(context).pausedUntil = 0
        VpnState.setPausedUntil(0)
    }

    /** Pauses the add-on (it stops watching other apps) for [minutes], or until resumed when null. */
    fun pauseAddon(context: Context, minutes: Int?, now: Long = System.currentTimeMillis()) {
        Settings(context).addonPausedUntil = minutes?.let { now + it * 60_000L } ?: FOREVER
        FocusStateProvider.notifyChanged(context)
    }

    fun resumeAddon(context: Context) {
        Settings(context).addonPausedUntil = 0
        FocusStateProvider.notifyChanged(context)
    }

    /** Asks the add-on to switch its Accessibility service off (it can only be turned back on in Settings). */
    fun turnOffAddon(context: Context, now: Long = System.currentTimeMillis()) {
        Settings(context).addonOffRequest = now
        FocusStateProvider.notifyChanged(context)
    }

    fun isAddonPaused(context: Context, now: Long = System.currentTimeMillis()) = Settings(context).addonPausedUntil > now
}
