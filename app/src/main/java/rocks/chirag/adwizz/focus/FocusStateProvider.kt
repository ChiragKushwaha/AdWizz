package rocks.chirag.adwizz.focus

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import rocks.chirag.adwizz.data.Settings

/**
 * Tells the AdWizz add-on what to do: one row with "Block Reels & Shorts" first (1 or 0, as in
 * add-on 1.0), then "Skip YouTube ads", "Mute Spotify ads" and when a pause ends (ms, 0 = not
 * paused). Read-only, and only apps signed with AdWizz's key may read it (see the manifest).
 */
class FocusStateProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val s = Settings(context!!)
        fun bit(b: Boolean) = if (b) 1 else 0
        return MatrixCursor(COLUMNS, 1).apply {
            addRow(arrayOf<Any>(bit(s.blockShortVideos), bit(s.skipYoutubeAds), bit(s.muteSpotifyAds), s.addonPausedUntil, s.addonOffRequest, bit(s.blockSponsored)))
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    companion object {
        val COLUMNS = arrayOf("enabled", "skip_youtube_ads", "mute_spotify_ads", "paused_until", "off_request", "hide_sponsored")

        fun uri(context: Context): Uri = Uri.parse("content://${context.packageName}.focusstate/switch")

        /** Lets the add-on pick up a change to the switch right away. */
        fun notifyChanged(context: Context) {
            runCatching { context.contentResolver.notifyChange(uri(context), null) }
        }
    }
}
