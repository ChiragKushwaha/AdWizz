package rocks.chirag.adwizz.ui

import android.content.Context
import android.content.Intent
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.net.toUri
import rocks.chirag.adwizz.data.FocusAddon
import rocks.chirag.adwizz.tr

/**
 * Removes AdWizz and its add-on in one go. Android asks to confirm each app. (If AdWizz is
 * removed some other way, the add-on notices and asks to be removed too.)
 */
@Composable
internal fun UninstallSection() {
    val context = LocalContext.current
    SectionTitle(tr("uninstall.title"))
    Note(tr("uninstall.desc"))
    OutlinedButton(onClick = { uninstallEverything(context) }, modifier = Modifier.testTag("uninstallAll")) {
        Text(tr("uninstall.button"))
    }
}

private fun uninstallEverything(context: Context) {
    fun delete(pkg: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_DELETE, "package:$pkg".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    // AdWizz's prompt first, then the add-on's on top of it: the user confirms the add-on first.
    delete(context.packageName)
    if (FocusAddon.isInstalled(context)) delete(FocusAddon.PACKAGE)
}
