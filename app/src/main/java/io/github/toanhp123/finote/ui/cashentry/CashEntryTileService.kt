package io.github.toanhp123.finote.ui.cashentry

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.TileService
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat

class CashEntryTileService : TileService() {
    override fun onClick() {
        unlockAndRun { open() }
    }

    // The compat call picks the PendingIntent (API 34+) or Intent overload itself.
    private fun open() {
        val intent = Intent(this, CashEntryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val wrapper = PendingIntentActivityWrapper(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT, false)
        TileServiceCompat.startActivityAndCollapse(this, wrapper)
    }
}
