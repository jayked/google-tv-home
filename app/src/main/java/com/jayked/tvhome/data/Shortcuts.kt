package com.jayked.tvhome.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

data class Shortcut(val label: String, val icon: ImageVector, val intent: Intent)

/** HDMI/passthrough inputs (if the device has any) followed by common settings pages. */
fun loadShortcuts(context: Context): List<Shortcut> {
    val inputs = context.getSystemService(TvInputManager::class.java)?.tvInputList.orEmpty()
        .filter { it.isPassthroughInput }
        .map {
            Shortcut(
                label = it.loadLabel(context).toString(),
                icon = Icons.Default.PlayArrow,
                intent = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(it.id)),
            )
        }
    val settings = listOf(
        "Settings" to Settings.ACTION_SETTINGS,
        "Network & Internet" to Settings.ACTION_WIFI_SETTINGS,
        "Display & Sound" to Settings.ACTION_DISPLAY_SETTINGS,
        "Remotes & Accessories" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "Apps" to Settings.ACTION_APPLICATION_SETTINGS,
    ).map { (label, action) -> Shortcut(label, Icons.Default.Settings, Intent(action)) }
        .filter { it.intent.resolveActivity(context.packageManager) != null }
    return inputs + settings
}
