package com.lumifold.notihold

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class NotiHoldTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile.state = Tile.STATE_ACTIVE
        qsTile.label = "NotiHoldを開く"
        qsTile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        
        // Open NotiHold app
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        
        // Update tile state
        qsTile.state = Tile.STATE_ACTIVE
        qsTile.updateTile()
    }
}
