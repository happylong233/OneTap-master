package tech.huangsh.onetap.ui.screens.elder

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.huangsh.onetap.R
import tech.huangsh.onetap.data.model.AppInfo
import tech.huangsh.onetap.ui.activity.AppManagementActivity
import tech.huangsh.onetap.utils.ImageUtils
import tech.huangsh.onetap.viewmodel.MainViewModel

@Composable
fun AppsPage(
    viewModel: MainViewModel,
    onBackFamily: () -> Unit,
    modifier: Modifier = Modifier
) {
    val apps by viewModel.elderApps.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    fun openAppManagement() {
        context.startActivity(android.content.Intent(context, AppManagementActivity::class.java))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        if (apps.isEmpty()) {
            EmptyAppsPanel(
                onAddApps = ::openAppManagement,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 20.dp, bottom = 12.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    ElderAppTile(
                        app = app,
                        longPressAsClickEnabled = settings.longPressAsClickEnabled,
                        onClick = { viewModel.launchApp(app.packageName) }
                    )
                }
            }
        }

        PrimaryBottomButton(
            text = stringResource(R.string.elder_back_family),
            icon = Icons.Default.FamilyRestroom,
            onClick = onBackFamily
        )
    }
}

@Composable
private fun EmptyAppsPanel(
    onAddApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(76.dp))
                Text("还没有常用软件", fontSize = 26.sp)
                Text("请家属先添加抖音、微信、相册等软件", fontSize = 18.sp)
                TextButton(
                    onClick = onAddApps,
                    modifier = Modifier.heightIn(min = 72.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.size(10.dp))
                    Text("添加常用软件", fontSize = 24.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ElderAppTile(
    app: AppInfo,
    longPressAsClickEnabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (longPressAsClickEnabled) onClick else null
            ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val bitmap = app.iconBytes?.let { ImageUtils.byteArrayToBitmap(it) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = app.appName,
                    modifier = Modifier.size(112.dp)
                )
            } else {
                Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(112.dp))
            }
            Spacer(Modifier.size(8.dp))
            Text(app.appName, fontSize = 22.sp)
        }
    }
}
