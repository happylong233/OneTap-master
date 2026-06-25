package tech.huangsh.onetap.ui.screens.elder

import android.Manifest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import tech.huangsh.onetap.R
import tech.huangsh.onetap.data.model.Contact
import tech.huangsh.onetap.data.model.PreferredCallMethod
import tech.huangsh.onetap.ui.activity.ContactDetailActivity
import tech.huangsh.onetap.ui.activity.SettingsActivity
import tech.huangsh.onetap.viewmodel.MainViewModel

@Composable
fun FamilyPage(
    viewModel: MainViewModel,
    onOpenApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contacts by viewModel.homeContacts.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val activity = context as androidx.activity.ComponentActivity
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    val familyContactsPerPage = when {
        settings.familyContactsPerPage <= 2 -> 2
        settings.familyContactsPerPage <= 4 -> 4
        settings.familyContactsPerPage <= 6 -> 6
        else -> 8
    }

    fun openSettings() {
        context.startActivity(android.content.Intent(context, SettingsActivity::class.java))
    }

    fun openAddContact() {
        ContactDetailActivity.start(context, null, true)
    }

    fun directCall(phone: String) {
        if (phone.isBlank()) {
            viewModel.speakMissingPhone()
            return
        }
        XXPermissions.with(activity)
            .permission(Manifest.permission.CALL_PHONE)
            .request(object : OnPermissionCallback {
                override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                    if (allGranted) viewModel.directDial(phone)
                }

                override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                    viewModel.speakErrorPhonePermission()
                }
            })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        FamilyTopBar(onOpenSettings = ::openSettings)

        if (contacts.isEmpty()) {
            EmptyFamilySetupPanel(
                onAddContact = ::openAddContact,
                onOpenSettings = ::openSettings,
                modifier = Modifier.weight(1f)
            )
        } else {
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val verticalSpacing = 10.dp
                val horizontalSpacing = 10.dp
                val bottomPadding = 10.dp
                val columnCount = if (familyContactsPerPage == 2) 1 else 2
                val rowCount = familyContactsPerPage / columnCount
                val cardHeight = (
                    (maxHeight - bottomPadding - verticalSpacing * (rowCount - 1).toFloat()) /
                        rowCount.toFloat()
                    ).coerceAtLeast(128.dp)

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                    horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                    contentPadding = PaddingValues(bottom = bottomPadding)
                ) {
                    items(contacts, key = { it.id }) { contact ->
                        FamilyContactCard(
                            contact = contact,
                            cardHeight = cardHeight,
                            onPhotoClick = { selectedContact = contact }
                        )
                    }
                }
            }
        }

        PrimaryBottomButton(
            text = stringResource(R.string.elder_open_apps),
            icon = Icons.Default.Movie,
            onClick = onOpenApps
        )
        Text(
            text = "按手机主页键也会回到这里",
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    selectedContact?.let { contact ->
        CallMethodDialog(
            contact = contact,
            onDismiss = { selectedContact = null },
            onVideoClick = {
                selectedContact = null
                viewModel.startWeChatVideo(contact)
            },
            onVoiceClick = {
                selectedContact = null
                viewModel.startWeChatVoice(contact)
            },
            onPhoneClick = {
                selectedContact = null
                directCall(contact.phone.orEmpty().trim())
            }
        )
    }
}

@Composable
private fun FamilyTopBar(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "今天想联系谁？",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "找家人",
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.heightIn(min = 60.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "家属",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun EmptyFamilySetupPanel(
    onAddContact: () -> Unit,
    onOpenSettings: () -> Unit,
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
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "还没有家人信息",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "请家属先添加照片、电话和微信备注",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onAddContact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.size(10.dp))
                    Text("添加家人", fontSize = 24.sp)
                }
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.heightIn(min = 56.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("家属设置", fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun FamilyContactCard(
    contact: Contact,
    cardHeight: Dp,
    onPhotoClick: () -> Unit,
    onVideoClick: () -> Unit = {},
    onVoiceClick: () -> Unit = {},
    onPhoneClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val contentHeight = (cardHeight - 20.dp).coerceAtLeast(108.dp)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = contentHeight + 20.dp)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            ContactPhoto(
                contact = contact,
                context = context,
                onPhotoClick = onPhotoClick,
                modifier = Modifier
                    .weight(1f)
                    .height(contentHeight)
            )
            Column(
                modifier = Modifier
                    .size(0.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onVideoClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12A65C)),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = stringResource(R.string.wechat_video),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "视频",
                        fontSize = 29.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                OutlinedButton(
                    onClick = onPhoneClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(3.dp, Color(0xFF2F241D)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2F241D)),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        Icons.Outlined.Phone,
                        contentDescription = stringResource(R.string.phone_call),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "电话",
                        fontSize = 29.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                TextButton(
                    onClick = onVoiceClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 34.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    Icon(
                        Icons.Default.RecordVoiceOver,
                        contentDescription = stringResource(R.string.wechat_voice),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "语音",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CallMethodDialog(
    contact: Contact,
    onDismiss: () -> Unit,
    onVideoClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onPhoneClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = {
            Text(
                text = "选择联系方法",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DialogActionButton(
                    text = "微信视频",
                    icon = Icons.Default.Videocam,
                    onClick = onVideoClick,
                    containerColor = Color(0xFF12A65C),
                    contentColor = Color.White
                )
                DialogActionButton(
                    text = "微信语音",
                    icon = Icons.Default.RecordVoiceOver,
                    onClick = onVoiceClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
                DialogActionButton(
                    text = "打电话",
                    icon = Icons.Outlined.Phone,
                    onClick = onPhoneClick,
                    containerColor = Color.White,
                    contentColor = Color(0xFF2F241D)
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(
                        text = "取消",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun DialogActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(38.dp))
        Spacer(Modifier.size(10.dp))
        Text(text, fontSize = 28.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ContactPhoto(
    contact: Contact,
    context: android.content.Context,
    onPhotoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onPhotoClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7EF))
    ) {
        if (contact.avatarUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(contact.avatarUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "人",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun PrimaryBottomButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp)
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D2D))
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.size(12.dp))
        Text(text, fontSize = 31.sp, fontWeight = FontWeight.Black)
    }
}
