package com.jayked.tvhome

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jayked.tvhome.data.WatchNextItem
import com.jayked.tvhome.data.WatchNextRepository
import com.jayked.tvhome.ui.HomeScreen
import com.jayked.tvhome.ui.TvHomeTheme

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    /** Bumped when Home is pressed while already on the home screen, to scroll back to the top. */
    private var homePresses by mutableIntStateOf(0)

    private val requestWatchNextAccess =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(WatchNextRepository.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestWatchNextAccess.launch(WatchNextRepository.PERMISSION)
        }
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            TvHomeTheme {
                // The home screen is the bottom of the stack; Back has nowhere to go.
                BackHandler {}
                HomeScreen(
                    state = state,
                    homePresses = homePresses,
                    onLaunch = ::launch,
                    onContinueWatching = ::continueWatching,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onMoveFavorite = viewModel::moveFavorite,
                    onMoveApp = viewModel::moveApp,
                    onSetWallpaperQuery = viewModel::setWallpaperQuery,
                    onAppInfo = { packageName ->
                        launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setVisible(true)
    }

    override fun onStop() {
        super.onStop()
        viewModel.setVisible(false)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePresses++
    }

    /** Reopens the app's player if its media session is still alive, else the app (or its deep link). */
    private fun continueWatching(item: WatchNextItem) {
        val session = item.sessionActivity
        if (session != null) {
            // The PendingIntent belongs to the other app; let it start its activity from our foreground.
            val options = ActivityOptions.makeBasic().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }
            }
            val sent = runCatching { session.send(this, 0, null, null, null, null, options.toBundle()) }.isSuccess
            if (sent) return
        }
        launch(item.intent)
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }
}
