package tech.huangsh.onetap.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.huangsh.onetap.data.model.Contact
import tech.huangsh.onetap.data.model.Settings
import tech.huangsh.onetap.data.repository.AppRepository
import tech.huangsh.onetap.data.repository.ContactRepository
import tech.huangsh.onetap.data.repository.SettingsRepository
import tech.huangsh.onetap.data.repository.WeChatAutomationStartResult
import tech.huangsh.onetap.service.wechat.WeChatAutomationController
import tech.huangsh.onetap.ui.screens.elder.ElderTab
import tech.huangsh.onetap.utils.VoiceAssistant
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val appRepository: AppRepository,
    private val settingsRepository: SettingsRepository,
    private val voiceAssistant: VoiceAssistant,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val homeContacts = contactRepository.homeContacts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val emergencyContacts = contactRepository.emergencyContacts().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val settings = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        Settings()
    )

    val elderApps = appRepository.enabledApps
        .map { apps -> apps.take(6) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _elderTab = MutableStateFlow(ElderTab.Family)
    val elderTab: StateFlow<ElderTab> = _elderTab.asStateFlow()

    init {
        WeChatAutomationController.onFailure = {
            speak("没有打开成功，请让家人帮忙检查微信")
        }
        WeChatAutomationController.onOpenedCallUi = { isVideo ->
            speak(
                if (isVideo) {
                    "已经帮您打开视频电话，请等待接通"
                } else {
                    "已经帮您打开语音电话，请等待接通"
                }
            )
        }
        WeChatAutomationController.onAnnouncement = { message ->
            speak(message)
        }
    }

    fun resetToFamilyPage() {
        _elderTab.value = ElderTab.Family
    }

    fun openAppsPage() {
        _elderTab.value = ElderTab.Apps
    }

    fun openFamilyPage() {
        _elderTab.value = ElderTab.Family
    }

    fun speakFamilyOnlySetting() {
        speak("请让家人设置")
    }

    fun speakErrorPhonePermission() {
        speak("需要电话权限，请让家人开启")
    }

    fun speakMissingPhone() {
        speak("这个家人还没有设置电话")
    }

    fun startWeChatVideo(contact: Contact) {
        startWeChat(contact, video = true)
    }

    fun startWeChatVoice(contact: Contact) {
        startWeChat(contact, video = false)
    }

    private fun startWeChat(contact: Contact, video: Boolean) {
        viewModelScope.launch {
            val result = if (video) {
                contactRepository.startWeChatVideoCall(contact.resolveWeChatSearchKeyword())
            } else {
                contactRepository.startWeChatVoiceCall(contact.resolveWeChatSearchKeyword())
            }
            when (result) {
                WeChatAutomationStartResult.Started ->
                    speak(if (video) "正在帮您打视频电话" else "正在帮您打开语音电话")

                WeChatAutomationStartResult.Busy ->
                    speak("正在帮您打开，请稍等")

                WeChatAutomationStartResult.MissingWeChatInfo ->
                    speak("这个家人还没有设置微信")

                WeChatAutomationStartResult.AccessibilityDisabledOpenSettings ->
                    speak("需要家人帮忙开启微信辅助")

                WeChatAutomationStartResult.WeChatNotInstalled ->
                    speak("没有打开成功，请让家人帮忙检查微信")

                WeChatAutomationStartResult.ClipboardFailed ->
                    speak("联系人昵称复制失败，请让家人帮忙检查")
            }
        }
    }

    fun openDialPad(phone: String) {
        viewModelScope.launch {
            speak("已打开拨号页面，请点击绿色电话按钮")
            try {
                appContext.startActivity(
                    contactRepository.createDialIntent(phone).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) {
                speak("这个软件没有打开成功，请让家人检查")
            }
        }
    }

    fun directDial(phone: String) {
        viewModelScope.launch {
            speak("正在帮您拨打电话")
            try {
                appContext.startActivity(
                    contactRepository.createDirectCallIntent(phone).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) {
                speak("这个软件没有打开成功，请让家人检查")
            }
        }
    }

    fun launchApp(packageName: String) {
        viewModelScope.launch {
            val intent = appRepository.launchApp(packageName)
            if (intent == null) {
                speak("这个软件没有打开成功，请让家人检查")
                return@launch
            }
            try {
                appContext.startActivity(intent)
            } catch (_: Exception) {
                speak("这个软件没有打开成功，请让家人检查")
            }
        }
    }

    fun placeEmergencyCall(phone: String) {
        directDial(phone)
    }

    private fun speak(text: String) {
        viewModelScope.launch {
            if (settingsRepository.settings.first().voiceEnabled) {
                voiceAssistant.speak(text)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        WeChatAutomationController.onFailure = null
        WeChatAutomationController.onOpenedCallUi = null
        WeChatAutomationController.onAnnouncement = null
    }
}
