package tech.huangsh.onetap.data.repository

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import tech.huangsh.onetap.data.local.dao.ContactDao
import tech.huangsh.onetap.data.model.Contact
import tech.huangsh.onetap.data.model.PreferredCallMethod
import tech.huangsh.onetap.service.wechat.WeChatAccessibilityService
import tech.huangsh.onetap.service.wechat.WeChatAutomationController
import tech.huangsh.onetap.utils.ImageUtils

enum class WeChatAutomationStartResult {
    Started,
    Busy,
    MissingWeChatInfo,
    AccessibilityDisabledOpenSettings,
    WeChatNotInstalled,
    ClipboardFailed
}

class ContactRepository(
    private val contactDao: ContactDao,
    private val context: Context
) {
    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts()
    val homeContacts: Flow<List<Contact>> = contactDao.getHomeContacts()

    fun emergencyContacts(): Flow<List<Contact>> = contactDao.getEmergencyContacts()
    fun getContactsCount(): Flow<Int> = contactDao.getContactCount()
    suspend fun getContactById(id: Long): Contact? = contactDao.getContactById(id)

    suspend fun insertContact(contact: Contact): Long {
        val maxOrder = contactDao.getMaxOrder() ?: -1
        return contactDao.insertContact(contact.withSyncedLegacyFlags().copy(order = maxOrder + 1))
    }

    suspend fun updateContact(contact: Contact) {
        contactDao.updateContact(
            contact.withSyncedLegacyFlags().copy(updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun deleteContact(contact: Contact) {
        ImageUtils.deleteImageFile(contact.avatarUri)
        contactDao.deleteContact(contact)
    }

    suspend fun deleteContactById(id: Int) {
        contactDao.getContactById(id.toLong())?.let { ImageUtils.deleteImageFile(it.avatarUri) }
        contactDao.deleteContactById(id)
    }

    suspend fun moveContact(contactId: Long, fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        val contacts = contactDao.getAllContacts().first()
        if (fromPosition < toPosition) {
            for (i in fromPosition + 1..toPosition) {
                contactDao.updateContactOrder(contacts[i].id, i - 1)
            }
        } else {
            for (i in toPosition until fromPosition) {
                contactDao.updateContactOrder(contacts[i].id, i + 1)
            }
        }
        contactDao.updateContactOrder(contactId, toPosition)
    }

    fun searchContacts(query: String): Flow<List<Contact>> = contactDao.searchContacts(query)

    fun getSystemContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use { c ->
            val nameIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) {
                contacts.add(
                    Contact(
                        name = c.getString(nameIndex),
                        phone = c.getString(phoneIndex),
                        preferredCallMethod = PreferredCallMethod.PHONE_CALL.name
                    ).withSyncedLegacyFlags()
                )
            }
        }

        return contacts
            .distinctBy { it.name + it.phone }
            .sortedWith(compareBy(tech.huangsh.onetap.utils.ChineseUtils.pinyinComparator) { it.name })
    }

    fun createDialIntent(phoneNumber: String): Intent {
        return Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
            flags = FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun createDirectCallIntent(phoneNumber: String): Intent {
        return Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
            flags = FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun startWeChatVideoCall(searchKeyword: String?): WeChatAutomationStartResult {
        return startWeChatCall(searchKeyword, video = true)
    }

    fun startWeChatVoiceCall(searchKeyword: String?): WeChatAutomationStartResult {
        return startWeChatCall(searchKeyword, video = false)
    }

    private fun startWeChatCall(searchKeyword: String?, video: Boolean): WeChatAutomationStartResult {
        val key = searchKeyword?.trim().orEmpty()
        Log.d(TAG, "startWeChatCall video=$video keyword=$key")
        if (key.isEmpty()) return WeChatAutomationStartResult.MissingWeChatInfo
        if (!isWeChatInstalled()) {
            Log.w(TAG, "WeChat is not installed")
            return WeChatAutomationStartResult.WeChatNotInstalled
        }
        if (!isAccessibilityServiceEnabled()) {
            Log.w(TAG, "WeChat accessibility service is disabled")
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = FLAG_ACTIVITY_NEW_TASK
            })
            return WeChatAutomationStartResult.AccessibilityDisabledOpenSettings
        }
        if (!WeChatAutomationController.setTargetClipboard(context, key)) {
            Log.w(TAG, "Failed to write WeChat target to clipboard")
            return WeChatAutomationStartResult.ClipboardFailed
        }
        if (!WeChatAutomationController.tryStartTask(key, video)) return WeChatAutomationStartResult.Busy
        return if (startWechat()) {
            WeChatAutomationStartResult.Started
        } else {
            WeChatAutomationController.cancelTask()
            WeChatAutomationStartResult.WeChatNotInstalled
        }
    }

    private fun startWechat(): Boolean {
        // 方案1：使用不带 className 的方式（让系统选择默认启动页面）
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(WECHAT_PKG)
            if (launchIntent != null) {
                launchIntent.flags = FLAG_ACTIVITY_NEW_TASK
                context.startActivity(launchIntent)
                Log.d(TAG, "Started WeChat with default launcher")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start WeChat with default launcher", e)
        }

        // 方案2：尝试 LauncherUI（旧版微信）
        try {
            val intent2 = Intent().apply {
                setClassName(WECHAT_PKG, "com.tencent.mm.ui.LauncherUI")
                flags = FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent2)
            Log.d(TAG, "Started WeChat with LauncherUI")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed with LauncherUI", e)
        }

        // 方案3：尝试 main ui（新版本微信）
        try {
            val intent3 = Intent().apply {
                setClassName(WECHAT_PKG, "com.tencent.mm.ui.main.MainUI")
                flags = FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent3)
            Log.d(TAG, "Started WeChat with MainUI")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed with MainUI", e)
        }

        // 方案4：尝试 appbrand LaunchUI
        try {
            val intent4 = Intent().apply {
                setClassName(WECHAT_PKG, "com.tencent.mm.appbrand.ui.LAUNCHUI")
                flags = FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent4)
            Log.d(TAG, "Started WeChat with appbrand LaunchUI")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed with appbrand LaunchUI", e)
        }

        Log.w(TAG, "All WeChat launch methods failed")
        return false
    }

    private fun isWeChatInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(WECHAT_PKG, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceId = context.packageName + "/" + WeChatAccessibilityService::class.java.name
        val enabled = try {
            Settings.Secure.getInt(
                context.applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (_: Settings.SettingNotFoundException) {
            return false
        }
        if (enabled != 1) return false
        val services = Settings.Secure.getString(
            context.applicationContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(services)
        while (splitter.hasNext()) {
            if (splitter.next().equals(serviceId, ignoreCase = true)) return true
        }
        return false
    }

    private companion object {
        const val TAG = "ContactRepository"
        const val WECHAT_PKG = "com.tencent.mm"
    }

    suspend fun getContactCount(): Int = contactDao.getContactCount().first()

    suspend fun deleteAllContacts() {
        contactDao.getAllContacts().map { it }.first().forEach { contact ->
            ImageUtils.deleteImageFile(contact.avatarUri)
        }
        contactDao.deleteAllContacts()
    }
}
