package tech.huangsh.onetap.ui.screens.home

import androidx.compose.runtime.Composable
import tech.huangsh.onetap.ui.screens.elder.ElderHomeShell
import tech.huangsh.onetap.viewmodel.MainViewModel

/** @deprecated 老人端入口请使用 [ElderHomeShell] */
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    ElderHomeShell(viewModel = viewModel)
}
