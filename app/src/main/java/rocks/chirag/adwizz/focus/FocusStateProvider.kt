package rocks.chirag.adwizz.focus

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import rocks.chirag.adwizz.data.Settings

/**
 * Tells the "AdWizz Reels Blocker" add-on whether "Block Reels & Shorts" is on: one row, one
 * column, 1 or 0. Read-only, and only apps signed with AdWizz's key may read it (see the manifest).
 */
class FocusStateProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val on = Settings(context!!).blockShortVideos
        return MatrixCursor(arrayOf("enabled"), 1).apply { addRow(arrayOf(if (on) 1 else 0)) }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    companion object {
        fun uri(context: Context): Uri = Uri.parse("content://${context.packageName}.focusstate/switch")

        /** Lets the add-on pick up a change to the switch right away. */
        fun notifyChanged(context: Context) {
            runCatching { context.contentResolver.notifyChange(uri(context), null) }
        }
    }
}
