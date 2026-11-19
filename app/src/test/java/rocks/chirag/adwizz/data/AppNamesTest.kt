package rocks.chirag.adwizz.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A shared app ID is named after its main package instead of showing up as "Unknown app". */
class AppNamesTest {
    @Test fun playServicesNamesTheGoogleSharedId() {
        assertEquals("com.google.android.gms", AppNames.mainPackage(listOf("com.google.android.gsf", "com.google.android.gms")))
    }

    @Test fun otherwiseFirstAlphabetically() {
        assertEquals("com.a.app", AppNames.mainPackage(listOf("com.b.helper", "com.a.app")))
    }

    @Test fun nothingToNameItAfter() {
        assertNull(AppNames.mainPackage(emptyList()))
    }
}
