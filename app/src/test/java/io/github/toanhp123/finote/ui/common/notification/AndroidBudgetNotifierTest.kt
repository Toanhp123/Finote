package io.github.toanhp123.finote.ui.common.notification

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.domain.budget.model.BudgetAlert
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37])
class AndroidBudgetNotifierTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val notifier = AndroidBudgetNotifier(app)
    private val month = YearMonth.of(2026, 11)

    private fun system(mark: Int = 80) = BudgetAlert(month, "c1", "Food", "sys_food", mark)

    private fun posted(): List<Notification> = shadowOf(manager).allNotifications

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun resetLocale() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    // Below API 33 the app locale is a static the notifier reads; above it needs a live activity.
    @Test
    @Config(sdk = [32])
    fun `posts on the budget channel with the localized system name`() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("vi"))

        notifier.notify(system())

        val notification = posted().single()
        assertEquals("budget", notification.channelId)
        assertEquals(
            "Ăn uống đã dùng 80% ngân sách tháng",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    @Config(sdk = [32])
    fun `app locale wins over the system language`() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ja"))

        notifier.notify(system(mark = 100))

        assertEquals(
            "飲食は今月の予算の100%を使いました",
            posted()
                .single()
                .extras
                .getCharSequence(Notification.EXTRA_TEXT)
                .toString(),
        )
    }

    @Test
    fun `user category uses its own name`() {
        notifier.notify(BudgetAlert(month, "c2", "Cat food", null, 90))

        assertTrue(
            posted()
                .single()
                .extras
                .getCharSequence(Notification.EXTRA_TEXT)
                .toString()
                .startsWith("Cat food"),
        )
    }

    @Test
    fun `total budget uses the total title`() {
        notifier.notify(BudgetAlert(month, null, null, null, 100))

        assertTrue(
            posted()
                .single()
                .extras
                .getCharSequence(Notification.EXTRA_TEXT)
                .toString()
                .startsWith("Total budget"),
        )
    }

    @Test
    fun `two budgets get two notification ids`() {
        notifier.notify(system())
        notifier.notify(BudgetAlert(month, null, null, null, 80))

        assertEquals(2, posted().size)
    }

    @Test
    fun `lock screen visibility is private with a public version`() {
        notifier.notify(system())

        val notification = posted().single()
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertNotNull(notification.publicVersion)
    }

    @Test
    fun `tap opens the app at the budget tab`() {
        notifier.notify(system())

        val intent: Intent = shadowOf(posted().single().contentIntent).savedIntent
        assertEquals(OPEN_TAB_BUDGET, intent.getStringExtra(EXTRA_OPEN_TAB))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test
    fun `nothing is posted when notifications are off`() {
        shadowOf(manager).setNotificationsEnabled(false)

        notifier.notify(system())

        assertTrue(posted().isEmpty())
    }

    @Test
    fun `nothing is posted when the budget channel is off`() {
        manager.createNotificationChannel(NotificationChannel("budget", "Budget", NotificationManager.IMPORTANCE_NONE))

        notifier.notify(system())

        assertTrue(posted().isEmpty())
    }

    @Test
    fun `nothing is posted without the permission on 33`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifier.notify(system())

        assertTrue(posted().isEmpty())
    }
}
