package com.jayked.tvhome.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.jayked.tvhome.R

data class Shortcut(val label: String, val icon: ImageVector, val intent: Intent)

/** HDMI/passthrough inputs (if the device has any) followed by common settings pages. */
fun loadShortcuts(context: Context): List<Shortcut> {
    val inputs = context.getSystemService(TvInputManager::class.java)?.tvInputList.orEmpty()
        .filter { it.isPassthroughInput }
        .map {
            Shortcut(
                label = it.loadLabel(context).toString(),
                icon = Icons.AutoMirrored.Filled.Input,
                intent = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(it.id)),
            )
        }
    val settings = listOf(
        Shortcut(context.getString(R.string.settings_network), Icons.Default.Wifi, Intent(Settings.ACTION_WIFI_SETTINGS)),
        Shortcut(context.getString(R.string.settings_display), Icons.Default.Tv, Intent(Settings.ACTION_DISPLAY_SETTINGS)),
        Shortcut(context.getString(R.string.settings_remotes), Icons.Default.SettingsRemote, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)),
        Shortcut(context.getString(R.string.settings_apps), Icons.Default.Apps, Intent(Settings.ACTION_APPLICATION_SETTINGS)),
        Shortcut(context.getString(R.string.settings), Icons.Default.Settings, Intent(Settings.ACTION_SETTINGS)),
    )
        .filter { it.intent.resolveActivity(context.packageManager) != null }
    return inputs + settings
}
