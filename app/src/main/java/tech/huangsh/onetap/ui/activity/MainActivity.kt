package tech.huangsh.onetap.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import tech.huangsh.onetap.data.model.Settings
import tech.huangsh.onetap.ui.screens.elder.ElderHomeShell
import tech.huangsh.onetap.ui.theme.OneTapTheme
import tech.huangsh.onetap.viewmodel.MainViewModel
import tech.huangsh.onetap.viewmodel.SettingsViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateLauncherStatus()
        settingsViewModel.rescheduleTimeAnnouncements()
        lifecycleScope.launch {
            viewModel.maximizeVolumeIfNeeded()
        }
        setContent {
            val settings by settingsViewModel.settings.collectAsState(initial = Settings())
            OneTapTheme(
                darkTheme = false,
                highContrast = settings.highContrast,
                fontSize = settings.fontSize,
                themeMode = settings.themeMode
            ) {
                ElderHomeShell(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateLauncherStatus()
        lifecycleScope.launch {
            viewModel.maximizeVolumeIfNeeded()
        }
        viewModel.resetToFamilyPage()
    }

    private fun updateLauncherStatus() {
        lifecycleScope.launch {
            settingsViewModel.refreshDefaultLauncherStatus()
        }
    }
}
