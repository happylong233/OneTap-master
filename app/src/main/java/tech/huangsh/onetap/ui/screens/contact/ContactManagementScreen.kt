package tech.huangsh.onetap.ui.screens.contact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import tech.huangsh.onetap.R
import tech.huangsh.onetap.data.model.Contact
import tech.huangsh.onetap.ui.activity.ContactDetailActivity
import tech.huangsh.onetap.ui.screens.components.CommonTopBar
import tech.huangsh.onetap.viewmodel.ContactViewModel

@Composable
fun ContactManagementScreen(
    onBack: () -> Unit,
    onAddContact: () -> Unit,
    viewModel: ContactViewModel = hiltViewModel()
) {
    val contacts by viewModel.contacts.collectAsState(emptyList())
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.fillMaxSize()) {
        CommonTopBar(
            title = stringResource(R.string.contact_management),
            onBack = onBack,
            actions = {
                IconButton(onClick = onAddContact) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_contact))
                }
            }
        )

        if (contacts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(80.dp))
                Text(stringResource(R.string.no_contacts), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.add_first_contact))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(contacts, key = { it.id }) { contact ->
                    ContactRow(
                        contact = contact,
                        onClick = { ContactDetailActivity.start(context, contact, true) },
                        onMoveUp = { viewModel.moveContact(contact.id, -1) },
                        onMoveDown = { viewModel.moveContact(contact.id, 1) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(contact.name, style = MaterialTheme.typography.titleLarge)
                val subtitle = listOfNotNull(contact.relationLabel, contact.phone).joinToString("  ")
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.size(8.dp))
            IconButton(onClick = onMoveUp) {
                Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.move_up))
            }
            IconButton(onClick = onMoveDown) {
                Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.move_down))
            }
        }
    }
}
