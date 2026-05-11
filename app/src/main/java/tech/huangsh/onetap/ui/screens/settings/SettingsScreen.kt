package tech.huangsh.onetap.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import tech.huangsh.onetap.R
import tech.huangsh.onetap.data.model.Settings
import tech.huangsh.onetap.ui.activity.AppManagementActivity
import tech.huangsh.onetap.ui.activity.ContactDetailActivity
import tech.huangsh.onetap.ui.activity.ContactManagementActivity
import tech.huangsh.onetap.ui.activity.DisplaySettingsActivity
import tech.huangsh.onetap.ui.screens.components.CommonTopBar
import tech.huangsh.onetap.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val settings by settingsViewModel.settings.collectAsState(initial = Settings())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CommonTopBar(title = stringResource(R.string.settings), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsItem(
                icon = Icons.Default.PersonAdd,
                title = "添加联系人",
                onClick = { ContactDetailActivity.start(context, null, true) }
            )

            SettingsItem(
                icon = Icons.Default.People,
                title = stringResource(R.string.contact_management),
                onClick = { context.startActivity(Intent(context, ContactManagementActivity::class.java)) }
            )

            SettingsItem(
                icon = Icons.Default.Apps,
                title = stringResource(R.string.app_settings),
                onClick = { context.startActivity(Intent(context, AppManagementActivity::class.java)) }
            )

            SettingsItemWithDescription(
                icon = Icons.Default.TouchApp,
                title = "微信一键辅助设置",
                description = "去系统无障碍中开启 OneTap 微信辅助。只会在点击家人后执行一次任务。",
                onClick = { settingsViewModel.openAccessibilitySettings() }
            )

            SettingsItemWithDescription(
                icon = Icons.Default.Call,
                title = "电话设置",
                description = "老人端点击电话会直接拨出。首次使用时需要家属授权电话权限。"
            )

            SettingsItem(
                icon = Icons.Default.DisplaySettings,
                title = stringResource(R.string.display_settings),
                onClick = { context.startActivity(Intent(context, DisplaySettingsActivity::class.java)) }
            )

            if (settings.showExitLauncher) {
                LauncherSettingsSection(settingsViewModel)
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    trailing: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    }
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingIcon(icon)
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            trailing()
        }
    }
}

@Composable
fun LauncherSettingsSection(settingsViewModel: SettingsViewModel) {
    val settings by settingsViewModel.settings.collectAsState(initial = Settings())
    val isDefaultLauncher by settingsViewModel.isDefaultLauncher.collectAsState()
    var showExitDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsItemWithDescription(
            icon = Icons.Default.Home,
            title = stringResource(R.string.default_launcher_status),
            description = if (isDefaultLauncher) {
                stringResource(R.string.is_default_launcher)
            } else {
                stringResource(R.string.not_default_launcher) + "\n" + stringResource(R.string.set_as_default_launcher)
            },
            descriptionColor = if (isDefaultLauncher) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            onClick = {
                if (isDefaultLauncher) {
                    settingsViewModel.refreshDefaultLauncherStatus()
                } else {
                    settingsViewModel.triggerDefaultLauncherChooser()
                }
            }
        )

        SettingsItemWithDescription(
            icon = Icons.Default.DisplaySettings,
            title = stringResource(R.string.open_launcher_settings),
            description = "打开系统默认桌面设置。",
            onClick = { settingsViewModel.openDefaultAppSettings() }
        )

        SettingsItemWithDescription(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            title = stringResource(R.string.exit_launcher_mode),
            description = stringResource(R.string.exit_launcher_mode_desc),
            onClick = {
                if (settings.launcherExitConfirmation) {
                    showExitDialog = true
                } else {
                    settingsViewModel.exitLauncherMode()
                }
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.exit_launcher_dialog_title)) },
            text = { Text(stringResource(R.string.exit_launcher_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        settingsViewModel.exitLauncherMode()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun SettingsItemWithDescription(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    descriptionColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit = {},
    trailing: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    }
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingIcon(icon)
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = descriptionColor
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            trailing()
        }
    }
}

@Composable
private fun SettingIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
