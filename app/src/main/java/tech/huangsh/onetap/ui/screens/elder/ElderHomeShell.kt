package tech.huangsh.onetap.ui.screens.elder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import tech.huangsh.onetap.viewmodel.MainViewModel

/**
 * 老人端根布局：找家人 / 常用软件 两页切换，无底部导航栏。
 */
@Composable
fun ElderHomeShell(
    viewModel: MainViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val tab by viewModel.elderTab.collectAsState()

    val warmBg = Color(0xFFFFF4E6)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(warmBg)
    ) {
        when (tab) {
            ElderTab.Family -> FamilyPage(
                viewModel = viewModel,
                onOpenApps = { viewModel.openAppsPage() }
            )

            ElderTab.Apps -> AppsPage(
                viewModel = viewModel,
                onBackFamily = { viewModel.openFamilyPage() }
            )
        }
    }
}
