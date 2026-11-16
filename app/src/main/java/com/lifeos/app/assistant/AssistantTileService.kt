package com.lifeos.app.assistant

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.lifeos.app.R
import com.lifeos.app.ui.MainActivity

/** Плитка «Позвать помощника» в шторке: работает даже тогда, когда голосом позвать не получается. */
class AssistantTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_assistant)
            updateTile()
        }
    }

    override fun onClick() {
        if (AssistantControl.hasMic(this)) {
            AssistantControl.summon(this)
            return
        }
        // Нет доступа к микрофону: открываем приложение, оно попросит разрешение
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
