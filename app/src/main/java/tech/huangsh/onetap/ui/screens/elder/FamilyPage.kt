package tech.huangsh.onetap.ui.screens.elder

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FamilyPage(
    viewModel: MainViewModel,
    onOpenApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contacts by viewModel.homeContacts.collectAsState()
    val context = LocalContext.current
    val activity = context as androidx.activity.ComponentActivity

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "家属设置",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(38.dp)
                    .combinedClickable(
                        onClick = { openSettings() },
                        onLongClick = { openSettings() }
                    )
            )
        }

        if (contacts.isEmpty()) {
            EmptyFamilySetupPanel(
                onAddContact = ::openAddContact,
                onOpenSettings = ::openSettings,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(contacts, key = { it.id }) { contact ->
                    FamilyContactCard(
                        contact = contact,
                        onPhotoClick = {
                            when (contact.preferredMethodEnum()) {
                                PreferredCallMethod.WECHAT_VIDEO -> viewModel.startWeChatVideo(contact)
                                PreferredCallMethod.WECHAT_VOICE -> viewModel.startWeChatVoice(contact)
                                PreferredCallMethod.PHONE_CALL -> directCall(contact.phone.orEmpty().trim())
                            }
                        },
                        onVideoClick = { viewModel.startWeChatVideo(contact) },
                        onVoiceClick = { viewModel.startWeChatVoice(contact) },
                        onPhoneClick = { directCall(contact.phone.orEmpty().trim()) }
                    )
                }
            }
        }

        PrimaryBottomButton(
            text = stringResource(R.string.elder_open_apps),
            icon = Icons.Default.Movie,
            onClick = onOpenApps
        )
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
                Text("还没有家人信息", fontSize = 26.sp)
                Text("请家属先添加照片、电话和微信备注", fontSize = 18.sp)
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
    onPhotoClick: () -> Unit,
    onVideoClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onPhoneClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.92f),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onPhotoClick() }
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
                        Text(text = "人", fontSize = 64.sp)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onVideoClick, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = stringResource(R.string.wechat_video),
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onVoiceClick, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Default.RecordVoiceOver,
                        contentDescription = stringResource(R.string.wechat_voice),
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onPhoneClick, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Outlined.Phone,
                        contentDescription = stringResource(R.string.phone_call),
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
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
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D2D))
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.size(12.dp))
        Text(text, fontSize = 28.sp)
    }
}
