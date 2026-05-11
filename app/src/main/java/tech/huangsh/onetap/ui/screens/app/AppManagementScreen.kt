package tech.huangsh.onetap.ui.screens.app

import android.Manifest
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import tech.huangsh.onetap.R
import tech.huangsh.onetap.data.model.AppInfo
import tech.huangsh.onetap.ui.screens.components.CommonTopBar
import tech.huangsh.onetap.utils.ImageUtils
import tech.huangsh.onetap.viewmodel.AppViewModel

private const val MAX_ELDER_APPS = 6

@Composable
fun AppManagementScreen(
    onBack: () -> Unit,
    viewModel: AppViewModel = hiltViewModel()
) {
    val enabledApps by viewModel.enabledApps.collectAsState(emptyList())
    val availableApps by viewModel.availableApps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showLimitDialog by remember { mutableStateOf(false) }
    var showMiuiPermissionDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun requestAppListPermission() {
        val permissions = mutableListOf<String>()
        when {
            viewModel.isMiuiSystem() -> permissions.add("com.android.permission.GET_INSTALLED_APPS")
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> permissions.add(Manifest.permission.QUERY_ALL_PACKAGES)
        }
        if (permissions.isEmpty()) {
            viewModel.loadAvailableAppsIfNeeded()
            return
        }
        XXPermissions.with(context as androidx.activity.ComponentActivity)
            .permission(permissions)
            .request(object : OnPermissionCallback {
                override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                    if (allGranted) {
                        viewModel.checkPermission()
                        viewModel.loadAvailableAppsIfNeeded()
                    }
                }

                override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                    if (doNotAskAgain && viewModel.isMiuiSystem()) {
                        showMiuiPermissionDialog = true
                    }
                }
            })
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermission()
        if (viewModel.hasPermission.value) {
            viewModel.loadAvailableAppsIfNeeded()
        } else {
            requestAppListPermission()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermission()
                if (viewModel.hasPermission.value) viewModel.loadAvailableAppsIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val enabledPackages = enabledApps.map { it.packageName }.toSet()
    val filteredApps = remember(availableApps, searchQuery) {
        availableApps
            .filter { app -> app.hasLaunchSurface() }
            .filter { app ->
                searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            }
    }
    val appsToShow = remember(filteredApps, enabledPackages) {
        filteredApps.sortedWith(
            compareBy<AppInfo> { it.packageName !in enabledPackages }
                .thenBy { it.appName.lowercase() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CommonTopBar(
            title = "常用软件管理",
            onBack = onBack,
            actions = {
                IconButton(
                    enabled = !isLoading,
                    onClick = {
                        if (hasPermission) viewModel.refreshApps() else requestAppListPermission()
                    }
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "老人页最多显示 $MAX_ELDER_APPS 个软件。已选 ${enabledApps.size} 个。",
                style = MaterialTheme.typography.bodyLarge
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("搜索软件") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清除")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors()
            )
        }

        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在刷新软件列表")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            items(appsToShow, key = { it.packageName }) { app ->
                val enabledIndex = enabledApps.indexOfFirst { it.packageName == app.packageName }
                AppManagementItem(
                    app = app,
                    isEnabled = enabledIndex >= 0,
                    canMoveUp = enabledIndex > 0,
                    canMoveDown = enabledIndex >= 0 && enabledIndex < enabledApps.lastIndex,
                    onToggle = { enabled ->
                        if (enabled) {
                            if (enabledApps.size >= MAX_ELDER_APPS) {
                                showLimitDialog = true
                            } else {
                                viewModel.enableApp(app)
                            }
                        } else {
                            viewModel.disableApp(app.packageName)
                        }
                    },
                    onMoveUp = {
                        if (enabledIndex > 0) {
                            viewModel.moveApp(app.packageName, enabledIndex, enabledIndex - 1)
                        }
                    },
                    onMoveDown = {
                        if (enabledIndex >= 0 && enabledIndex < enabledApps.lastIndex) {
                            viewModel.moveApp(app.packageName, enabledIndex, enabledIndex + 1)
                        }
                    }
                )
            }
        }
    }

    if (showLimitDialog) {
        AlertDialog(
            onDismissRequest = { showLimitDialog = false },
            title = { Text("最多选择 $MAX_ELDER_APPS 个") },
            text = { Text("请先关闭一个不常用的软件，再添加新的软件。") },
            confirmButton = {
                TextButton(onClick = { showLimitDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }

    if (showMiuiPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showMiuiPermissionDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("需要应用列表权限") },
            text = { Text("请在系统设置中允许 OneTap 读取应用列表，家属才能选择常用软件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMiuiPermissionDialog = false
                        val intent = viewModel.getMiuiPermissionIntent()
                        if (intent != null) {
                            runCatching { context.startActivity(intent) }
                                .onFailure { context.startActivity(viewModel.getAppSettingsIntent()) }
                        } else {
                            context.startActivity(viewModel.getAppSettingsIntent())
                        }
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMiuiPermissionDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun AppManagementItem(
    app: AppInfo,
    isEnabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(app = app, modifier = Modifier.size(58.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
            }
            if (isEnabled) {
                IconButton(enabled = canMoveUp, onClick = onMoveUp) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "上移")
                }
                IconButton(enabled = canMoveDown, onClick = onMoveDown) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "下移")
                }
            }
            Switch(checked = isEnabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun AppIcon(app: AppInfo, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val bitmap = app.iconBytes?.let { ImageUtils.byteArrayToBitmap(it) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = app.appName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(Icons.Default.Android, contentDescription = app.appName, modifier = Modifier.fillMaxSize(0.8f))
        }
    }
}

private fun AppInfo.hasLaunchSurface(): Boolean {
    val blockedPrefixes = listOf(
        "android.",
        "com.android.providers.",
        "com.android.printspooler",
        "com.google.android.gms",
        "com.google.android.gsf"
    )
    return blockedPrefixes.none { packageName.startsWith(it) }
}
