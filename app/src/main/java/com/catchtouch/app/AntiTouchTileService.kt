package com.catchtouch.app

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.google.android.accessibility.selecttospeak.SelectToSpeakService

class AntiTouchTileService : TileService() {

    companion object {
        var instance: AntiTouchTileService? = null
    }

    override fun onStartListening() {
        super.onStartListening()
        instance = this
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        instance = null
    }

    override fun onClick() {
        super.onClick()
        val service = SelectToSpeakService.instance
        if (service == null) {
            updateTileInactive()
            return
        }

        if (SettingsManager.isEnabled(this)) {
            service.disableMask()
        } else {
            service.enableMask()
        }
        updateTile()
    }

    fun updateTile() {
        val enabled = SettingsManager.isEnabled(this)
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    private fun updateTileInactive() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
