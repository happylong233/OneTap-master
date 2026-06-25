package tech.huangsh.onetap.data.model

data class Settings(
    val voiceEnabled: Boolean = true,
    val voiceSpeed: Float = 1.0f,
    val voiceVolume: Float = 0.8f,
    val fontSize: FontSize = FontSize.MEDIUM,
    val contrastMode: ContrastMode = ContrastMode.NORMAL,
    val themeMode: ThemeMode = ThemeMode.ORANGE,
    val launcherMode: Boolean = true,
    val floatingBallEnabled: Boolean = false,
    val autoStartEnabled: Boolean = false,
    val oneTapPhoneEnabled: Boolean = false,
    val password: String = "",
    val isDefaultLauncher: Boolean = false,
    val showExitLauncher: Boolean = true,
    val launcherExitConfirmation: Boolean = true,
    val familyContactsPerPage: Int = 2
) {
    val voiceAssistantEnabled: Boolean get() = voiceEnabled
    val voiceFeedbackEnabled: Boolean get() = voiceEnabled
    val highContrast: Boolean get() = contrastMode == ContrastMode.HIGH
}

enum class FontSize {
    SMALL,
    MEDIUM,
    LARGE;

    val intValue: Int
        get() = when (this) {
            SMALL -> 0
            MEDIUM -> 1
            LARGE -> 2
        }
}

enum class ContrastMode {
    NORMAL,
    HIGH
}

enum class ThemeMode {
    BLUE,
    ORANGE
}

enum class AppCategory {
    SOCIAL,
    TOOLS,
    ENTERTAINMENT,
    LIFE,
    OTHER
}

enum class ContactAction {
    VIDEO_CALL,
    VOICE_CALL,
    PHONE_CALL
}
