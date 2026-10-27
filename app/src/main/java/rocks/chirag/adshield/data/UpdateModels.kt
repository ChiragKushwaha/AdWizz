package rocks.chirag.adshield.data

import org.json.JSONObject
import java.io.File

/** What the release script publishes at [UpdateManifest.URL] (see scripts/release.sh). */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
    val sizeBytes: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    /** Downloaded and verified: one tap installs it. */
    data class Ready(val info: UpdateInfo, val apk: File) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val info: UpdateInfo?, val message: String) : UpdateState
}

/** The update the [UpdateState] is about, if any. */
val UpdateState.info: UpdateInfo?
    get() = when (this) {
        UpdateState.Idle -> null
        is UpdateState.Available -> info
        is UpdateState.Downloading -> info
        is UpdateState.Ready -> info
        is UpdateState.Installing -> info
        is UpdateState.Failed -> info
    }

object UpdateManifest {
    const val URL = "https://chiragkushwaha.github.io/AdWizz/update.json"
    private const val RELEASES = "https://github.com/ChiragKushwaha/AdWizz/releases/download/"
    const val MAX_APK_BYTES = 50L * 1024 * 1024

    /** Parses and validates the manifest. Null if anything is missing or unsafe. */
    fun parse(json: String): UpdateInfo? = runCatching {
        val o = JSONObject(json)
        val info = UpdateInfo(
            versionCode = o.getInt("versionCode"),
            versionName = o.getString("versionName"),
            apkUrl = o.getString("apkUrl"),
            sha256 = o.getString("sha256").lowercase(),
            notes = o.optString("notes", ""),
            sizeBytes = o.optLong("sizeBytes", 0),
        )
        // Only ever download our own release assets, over HTTPS.
        require(info.apkUrl.startsWith(RELEASES))
        require(info.sha256.matches(Regex("[0-9a-f]{64}")))
        require(info.versionCode > 0 && info.sizeBytes in 0..MAX_APK_BYTES)
        info
    }.getOrNull()
}
