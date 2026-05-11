package tech.huangsh.onetap.ui.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import tech.huangsh.onetap.data.model.Contact
import tech.huangsh.onetap.data.model.Settings
import tech.huangsh.onetap.ui.screens.contact.ContactDetailScreen
import tech.huangsh.onetap.ui.theme.OneTapTheme
import tech.huangsh.onetap.utils.ImageUtils
import tech.huangsh.onetap.viewmodel.ContactViewModel
import tech.huangsh.onetap.viewmodel.SettingsViewModel

@AndroidEntryPoint
class ContactDetailActivity : ComponentActivity() {
    private val viewModel: ContactViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private var copiedImagePath: String? = null
    private var oldImagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CONTACT, Contact::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_CONTACT)
        }
        oldImagePath = contact?.avatarUri

        setContent {
            val settings by settingsViewModel.settings.collectAsState(initial = Settings())
            OneTapTheme(
                darkTheme = false,
                highContrast = settings.highContrast,
                fontSize = settings.fontSize,
                themeMode = settings.themeMode
            ) {
                ContactDetailScreen(
                    contact = contact,
                    onBack = { finish() },
                    onSave = { updatedContact ->
                        val finalContact = updatedContact.copy(
                            avatarUri = copiedImagePath ?: updatedContact.avatarUri
                        )
                        val oldPath = oldImagePath
                        val newPath = copiedImagePath
                        if (oldPath != null && newPath != null && oldPath != newPath) {
                            ImageUtils.deleteImageFile(oldPath)
                        }
                        if (contact == null) {
                            viewModel.addContact(finalContact)
                        } else {
                            viewModel.updateContact(finalContact)
                        }
                        finish()
                    },
                    onDelete = { contactToDelete ->
                        viewModel.deleteContact(contactToDelete)
                        finish()
                    },
                    onImageSelected = { uri: Uri ->
                        copiedImagePath = ImageUtils.copyImageToAppDirectory(this, uri)
                    }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_CONTACT = "contact"

        fun start(context: Context, contact: Contact? = null, isEditMode: Boolean = true) {
            val intent = Intent(context, ContactDetailActivity::class.java).apply {
                putExtra(EXTRA_CONTACT, contact)
            }
            context.startActivity(intent)
        }
    }
}
