package com.sameerasw.essentials.services.tiles

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import com.sameerasw.essentials.FeatureSettingsActivity
import com.sameerasw.essentials.R
import com.sameerasw.essentials.utils.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DemoModeTileService : BaseTileService() {
    override fun onClick() {
        if (!hasFeaturePermission()) {
            val intent =
                Intent(this, FeatureSettingsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("feature", "Quick settings tiles")
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent =
                    android.app.PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                    )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }
        super.onClick()
    }

    override fun getTileLabel(): String = getString(R.string.tile_demo_mode)

    override fun getTileSubtitle(): String =
        when {
            !hasFeaturePermission() -> getString(R.string.permission_missing)
            isDemoModeOn() -> getString(R.string.on)
            else -> getString(R.string.off)
        }

    override fun hasFeaturePermission(): Boolean = ShellUtils.isAvailable(this) && ShellUtils.hasPermission(this)

    override fun getTileIcon(): Icon = Icon.createWithResource(this, R.drawable.rounded_screenshot_region_24)

    override fun getTileState(): Int = if (isDemoModeOn()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

    override fun onTileClick() {
        val command = if (isDemoModeOn()) EXIT_COMMAND else ENTER_COMMAND
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                ShellUtils.runCommand(this@DemoModeTileService, command, getTileLabel())
            }
            updateTile()
        }
    }

    private fun isDemoModeOn(): Boolean =
        try {
            Settings.Global.getInt(contentResolver, DEMO_MODE_ON, 0) == 1
        } catch (_: Exception) {
            false
        }

    companion object {
        private const val DEMO_MODE_ALLOWED = "sysui_demo_allowed"
        private const val DEMO_MODE_ON = "sysui_tuner_demo_on"
        private const val DEMO = "am broadcast -a com.android.systemui.demo -e command"

        // Pixel-style "perfect" status bar: 12:00, full signal and battery, no notifications
        private val ENTER_COMMAND =
            listOf(
                "settings put global $DEMO_MODE_ALLOWED 1",
                "settings put global $DEMO_MODE_ON 1",
                "$DEMO enter",
                "$DEMO clock -e hhmm 1200",
                "$DEMO battery -e level 100 -e plugged false -e powersave false",
                "$DEMO network -e wifi show -e level 4 -e fully true",
                "$DEMO network -e mobile show -e datatype none -e level 4 -e fully true",
                "$DEMO network -e airplane hide -e nosim hide",
                "$DEMO notifications -e visible false",
                "$DEMO status -e volume hide -e bluetooth hide -e location hide -e alarm hide -e sync hide -e tty hide -e eri hide -e mute hide -e speakerphone hide",
            ).joinToString("; ")

        private val EXIT_COMMAND =
            listOf(
                "$DEMO exit",
                "settings put global $DEMO_MODE_ON 0",
            ).joinToString("; ")
    }
}
