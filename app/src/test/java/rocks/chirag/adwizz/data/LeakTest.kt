package rocks.chirag.adwizz.data

import android.app.Activity
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.lang.ref.WeakReference

/** Objects that can outlive a screen must never hold on to the Activity. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LeakTest {
    private fun contextField(o: Any): Context =
        o.javaClass.declaredFields.first { Context::class.java.isAssignableFrom(it.type) }
            .also { it.isAccessible = true }.get(o) as Context

    @Test fun repositoryKeepsOnlyTheApplicationContext() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val repo = BlocklistRepository(activity)
        assertSame(activity.applicationContext, contextField(repo))
    }

    @Test fun activityCanBeCollectedWhileRepositoryLives() {
        var controller: org.robolectric.android.controller.ActivityController<Activity>? =
            Robolectric.buildActivity(Activity::class.java).setup()
        val ref = WeakReference(controller!!.get())
        val repo = BlocklistRepository(controller.get())    // e.g. a long download still running
        controller.pause().stop().destroy()
        controller = null
        repeat(10) { System.gc(); System.runFinalization(); if (ref.get() == null) return@repeat; Thread.sleep(50) }
        assertNull("Activity leaked through BlocklistRepository", ref.get())
        assertNotNull(repo)
    }

    @Test fun settingsUseApplicationContext() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        Settings(activity).allowlist = setOf("a.com")
        // SharedPreferences are fetched via applicationContext; nothing Activity-scoped is stored.
        val fields = Settings::class.java.declaredFields.filter { Context::class.java.isAssignableFrom(it.type) }
        assertTrue("Settings must not keep a Context field", fields.isEmpty())
    }
}
