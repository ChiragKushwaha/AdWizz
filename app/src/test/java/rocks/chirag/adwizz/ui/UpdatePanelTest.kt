package rocks.chirag.adwizz.ui

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rocks.chirag.adwizz.data.UpdateManifest
import rocks.chirag.adwizz.data.UpdateState
import rocks.chirag.adwizz.data.Updater
import java.io.File

/** The pixel "New spell book" panel on the home screen for each update state. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class UpdatePanelTest : UiTestBase() {
    private val info = UpdateManifest.parse(
        """{"versionCode":99,"versionName":"9.9.0","apkUrl":"https://github.com/ChiragKushwaha/AdWizz/releases/download/v9.9.0/AdWizz.apk",""" +
            """"sha256":"${"b".repeat(64)}","sizeBytes":1000,"notes":"Blocks more pop-ups"}"""
    )!!

    @org.junit.Before fun noDownloads() { Updater.autoDownload = false }

    @After fun reset() {
        Updater.setStateForTest(UpdateState.Idle)
        Updater.autoDownload = true
    }

    private fun show(state: UpdateState) {
        Updater.setStateForTest(state)
        launchApp()
    }

    @Test fun hiddenWhenThereIsNoUpdate() {
        show(UpdateState.Idle)
        rule.onNodeWithTag("updatePanel").assertDoesNotExist()
    }

    @Test fun readyUpdateOffersOneTapInstall() {
        show(UpdateState.Ready(info, File.createTempFile("AdWizz", ".apk").apply { deleteOnExit() }))
        rule.onNodeWithText("New spell book: v9.9.0").assertIsDisplayed()
        rule.onNodeWithText("Blocks more pop-ups").assertIsDisplayed()
        rule.onNodeWithText("INSTALL").assertIsDisplayed()
    }

    @Test fun availableUpdateSaysUpdate() {
        show(UpdateState.Available(info))
        rule.onNodeWithText("UPDATE").assertIsDisplayed()
    }

    @Test fun downloadingShowsProgressInsteadOfButtons() {
        show(UpdateState.Downloading(info, 0.4f))
        rule.onNodeWithTag("updateButton").assertDoesNotExist()
        rule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.4f, 0f..1f))).assertExists()
    }

    @Test fun failureShowsMessageAndRetry() {
        show(UpdateState.Failed(info, "Download was corrupted (checksum mismatch)"))
        rule.onNodeWithText("Download was corrupted (checksum mismatch)").assertIsDisplayed()
        rule.onNodeWithText("RETRY").assertIsDisplayed()
    }

    @Test fun laterHidesThePanel() {
        show(UpdateState.Available(info))
        rule.onNodeWithText("Later").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("updatePanel").assertDoesNotExist()
    }
}
