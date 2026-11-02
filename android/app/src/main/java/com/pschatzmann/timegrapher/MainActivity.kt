package com.pschatzmann.timegrapher

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pschatzmann.timegrapher.ui.AppTheme
import com.pschatzmann.timegrapher.ui.Amber
import com.pschatzmann.timegrapher.ui.Chip
import com.pschatzmann.timegrapher.ui.Emerald
import com.pschatzmann.timegrapher.ui.LiveScreen
import com.pschatzmann.timegrapher.ui.SettingsScreen
import com.pschatzmann.timegrapher.ui.TrendsScreen

private enum class Screen(@StringRes val label: Int, val icon: ImageVector) {
    Live(R.string.nav_live, Icons.Default.Speed),
    Trends(R.string.nav_trends, Icons.Default.ShowChart),
    Settings(R.string.nav_settings, Icons.Default.Settings),
}

class MainActivity : AppCompatActivity() {
    private val engine: TimeGrapherEngine get() = (application as TimeGrapherApp).engine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark by engine.darkTheme.collectAsStateWithLifecycle()
            val cfg by engine.config.collectAsStateWithLifecycle()
            LaunchedEffect(cfg.keepScreenOn) {
                if (cfg.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            AppTheme(dark) { AppRoot(engine) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(vm: TimeGrapherEngine) {
    var screen by rememberSaveable { mutableStateOf(Screen.Live) }
    val dark by vm.darkTheme.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshPermissions()
    }
    val requestPermissions: () -> Unit = {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        launcher.launch(perms.toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontSize = 18.sp, maxLines = 1) },
                actions = {
                    Chip(if (running) "LISTENING" else "IDLE", if (running) Emerald else Amber)
                    IconButton(onClick = { vm.toggleTheme() }) {
                        Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle theme")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach {
                    NavigationBarItem(
                        selected = screen == it,
                        onClick = { screen = it },
                        icon = { Icon(it.icon, stringResource(it.label)) },
                        label = { Text(stringResource(it.label), fontSize = 10.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (screen) {
                Screen.Live -> LiveScreen(vm, requestPermissions)
                Screen.Trends -> TrendsScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
            }
        }
    }
}
