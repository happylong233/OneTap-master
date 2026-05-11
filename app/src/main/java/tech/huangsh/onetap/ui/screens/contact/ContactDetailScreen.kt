package tech.huangsh.onetap.ui.screens.contact

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import tech.huangsh.onetap.R
import tech.huangsh.onetap.data.model.Contact
import tech.huangsh.onetap.data.model.PreferredCallMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contact: Contact?,
    onBack: () -> Unit,
    onSave: (Contact) -> Unit,
    onDelete: (Contact) -> Unit,
    onImageSelected: (Uri) -> Unit
) {
    var name by remember { mutableStateOf(contact?.name.orEmpty()) }
    var relationLabel by remember { mutableStateOf(contact?.relationLabel.orEmpty()) }
    var phone by remember { mutableStateOf(contact?.phone.orEmpty()) }
    var wechatDisplayName by remember { mutableStateOf(contact?.wechatDisplayName.orEmpty()) }
    var wechatId by remember { mutableStateOf(contact?.wechatId.orEmpty()) }
    var avatarUri by remember { mutableStateOf(contact?.avatarUri) }
    var method by remember {
        mutableStateOf(contact?.preferredMethodEnum() ?: PreferredCallMethod.WECHAT_VIDEO)
    }
    var visibleOnHome by remember { mutableStateOf(contact?.isVisibleOnHome ?: true) }
    var emergency by remember { mutableStateOf(contact?.isEmergency ?: false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            avatarUri = it.toString()
            onImageSelected(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (contact == null) stringResource(R.string.add_contact) else stringResource(R.string.edit_contact)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (contact != null) {
                        IconButton(onClick = { onDelete(contact) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (avatarUri != null) {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(132.dp)
                            .clickable { imagePicker.launch("image/*") },
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier
                            .size(132.dp)
                            .clickable { imagePicker.launch("image/*") }
                    )
                }
                TextButton(onClick = { imagePicker.launch("image/*") }) {
                    Text(stringResource(R.string.contact_change_avatar))
                }
            }

            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("显示名称") }, singleLine = true)
            OutlinedTextField(relationLabel, { relationLabel = it }, Modifier.fillMaxWidth(), label = { Text("关系称呼") }, singleLine = true)
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("手机号") }, singleLine = true)
            OutlinedTextField(wechatDisplayName, { wechatDisplayName = it }, Modifier.fillMaxWidth(), label = { Text("微信备注名") }, singleLine = true)
            OutlinedTextField(wechatId, { wechatId = it }, Modifier.fillMaxWidth(), label = { Text("微信号（可选）") }, singleLine = true)

            Text("默认联系方式", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MethodChip("微信视频", method == PreferredCallMethod.WECHAT_VIDEO) { method = PreferredCallMethod.WECHAT_VIDEO }
                MethodChip("微信语音", method == PreferredCallMethod.WECHAT_VOICE) { method = PreferredCallMethod.WECHAT_VOICE }
                MethodChip("电话", method == PreferredCallMethod.PHONE_CALL) { method = PreferredCallMethod.PHONE_CALL }
            }

            ToggleRow("显示在老人首页", visibleOnHome) { visibleOnHome = it }
            ToggleRow("紧急联系人", emergency) { emergency = it }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val saved = Contact(
                        id = contact?.id ?: 0,
                        name = name.trim(),
                        relationLabel = relationLabel.trim().ifBlank { null },
                        phone = phone.trim().ifBlank { null },
                        wechatDisplayName = wechatDisplayName.trim().ifBlank { null },
                        wechatId = wechatId.trim().ifBlank { null },
                        avatarUri = avatarUri,
                        preferredCallMethod = method.name,
                        isVisibleOnHome = visibleOnHome,
                        isEmergency = emergency,
                        isFavorite = contact?.isFavorite ?: false,
                        order = contact?.order ?: 0,
                        createdAt = contact?.createdAt ?: System.currentTimeMillis()
                    ).withSyncedLegacyFlags()
                    onSave(saved)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun MethodChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text) })
}

@Composable
private fun ToggleRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text)
    }
}
