package tech.huangsh.onetap.data.repository

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import tech.huangsh.onetap.data.model.ContrastMode
import tech.huangsh.onetap.data.model.FontSize
import tech.huangsh.onetap.data.model.Settings
import tech.huangsh.onetap.data.model.ThemeMode
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 扩展DataStore
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 设置数据仓库
 */
class SettingsRepository(private val context: Context) {
    private val dataStore = context.dataStore

    // 键定义
    companion object {
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val VOICE_SPEED = floatPreferencesKey("voice_speed")
        val VOICE_VOLUME = floatPreferencesKey("voice_volume")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val CONTRAST_MODE = stringPreferencesKey("contrast_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAUNCHER_MODE = booleanPreferencesKey("launcher_mode")
        val FLOATING_BALL_ENABLED = booleanPreferencesKey("floating_ball_enabled")
        val AUTO_START_ENABLED = booleanPreferencesKey("auto_start_enabled")
        val ONE_TAP_PHONE_ENABLED = booleanPreferencesKey("one_tap_phone_enabled")
        val PASSWORD = stringPreferencesKey("password")
        val IS_DEFAULT_LAUNCHER = booleanPreferencesKey("is_default_launcher")
        val SHOW_EXIT_LAUNCHER = booleanPreferencesKey("show_exit_launcher")
        val LAUNCHER_EXIT_CONFIRMATION = booleanPreferencesKey("launcher_exit_confirmation")
        val FAMILY_CONTACTS_PER_PAGE = intPreferencesKey("family_contacts_per_page")
    }

    // 语音设置
    val voiceEnabled: Flow<Boolean> = dataStore.data.map { it[VOICE_ENABLED] ?: true }
    val voiceSpeed: Flow<Float> = dataStore.data.map { it[VOICE_SPEED] ?: 1.0f }
    val voiceVolume: Flow<Float> = dataStore.data.map { it[VOICE_VOLUME] ?: 0.8f }

    // 显示设置
    val fontSize: Flow<FontSize> = dataStore.data.map { 
        FontSize.valueOf(it[FONT_SIZE] ?: FontSize.MEDIUM.name) 
    }
    val contrastMode: Flow<ContrastMode> = dataStore.data.map { 
        ContrastMode.valueOf(it[CONTRAST_MODE] ?: ContrastMode.NORMAL.name) 
    }
    val themeMode: Flow<ThemeMode> = dataStore.data.map { 
        ThemeMode.valueOf(it[THEME_MODE] ?: ThemeMode.ORANGE.name) 
    }

    // 系统设置
    val launcherMode: Flow<Boolean> = dataStore.data.map { it[LAUNCHER_MODE] ?: true }
    val floatingBallEnabled: Flow<Boolean> = dataStore.data.map { it[FLOATING_BALL_ENABLED] ?: false }
    val autoStartEnabled: Flow<Boolean> = dataStore.data.map { it[AUTO_START_ENABLED] ?: false }
    val oneTapPhoneEnabled: Flow<Boolean> = dataStore.data.map { it[ONE_TAP_PHONE_ENABLED] ?: false }
    val password: Flow<String> = dataStore.data.map { it[PASSWORD] ?: "" }
    
    // 桌面启动器设置
    val isDefaultLauncher: Flow<Boolean> = dataStore.data.map { it[IS_DEFAULT_LAUNCHER] ?: false }
    val showExitLauncher: Flow<Boolean> = dataStore.data.map { it[SHOW_EXIT_LAUNCHER] ?: true }
    val launcherExitConfirmation: Flow<Boolean> = dataStore.data.map { it[LAUNCHER_EXIT_CONFIRMATION] ?: true }

    // 获取所有设置
    val settings: Flow<Settings> = dataStore.data.map { preferences ->
        Settings(
            voiceEnabled = preferences[VOICE_ENABLED] ?: true,
            voiceSpeed = preferences[VOICE_SPEED] ?: 1.0f,
            voiceVolume = preferences[VOICE_VOLUME] ?: 0.8f,
            fontSize = FontSize.valueOf(preferences[FONT_SIZE] ?: FontSize.MEDIUM.name),
            contrastMode = ContrastMode.valueOf(preferences[CONTRAST_MODE] ?: ContrastMode.NORMAL.name),
            themeMode = ThemeMode.valueOf(preferences[THEME_MODE] ?: ThemeMode.ORANGE.name),
            launcherMode = preferences[LAUNCHER_MODE] ?: true,
            floatingBallEnabled = preferences[FLOATING_BALL_ENABLED] ?: false,
            autoStartEnabled = preferences[AUTO_START_ENABLED] ?: false,
            oneTapPhoneEnabled = preferences[ONE_TAP_PHONE_ENABLED] ?: false,
            password = preferences[PASSWORD] ?: "",
            isDefaultLauncher = preferences[IS_DEFAULT_LAUNCHER] ?: false,
            showExitLauncher = preferences[SHOW_EXIT_LAUNCHER] ?: true,
            launcherExitConfirmation = preferences[LAUNCHER_EXIT_CONFIRMATION] ?: true,
            familyContactsPerPage = normalizeFamilyContactsPerPage(preferences[FAMILY_CONTACTS_PER_PAGE] ?: 2)
        )
    }

    // 更新设置
    suspend fun updateVoiceEnabled(enabled: Boolean) {
        dataStore.edit { it[VOICE_ENABLED] = enabled }
    }

    suspend fun updateVoiceSpeed(speed: Float) {
        dataStore.edit { it[VOICE_SPEED] = speed }
    }

    suspend fun updateVoiceVolume(volume: Float) {
        dataStore.edit { it[VOICE_VOLUME] = volume }
    }

    suspend fun updateFontSize(size: FontSize) {
        dataStore.edit { it[FONT_SIZE] = size.name }
    }

    suspend fun updateContrastMode(mode: ContrastMode) {
        dataStore.edit { it[CONTRAST_MODE] = mode.name }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun updateLauncherMode(enabled: Boolean) {
        dataStore.edit { it[LAUNCHER_MODE] = enabled }
    }

    suspend fun updateFloatingBallEnabled(enabled: Boolean) {
        dataStore.edit { it[FLOATING_BALL_ENABLED] = enabled }
    }

    suspend fun updateAutoStartEnabled(enabled: Boolean) {
        dataStore.edit { it[AUTO_START_ENABLED] = enabled }
    }

    suspend fun updateOneTapPhoneEnabled(enabled: Boolean) {
        dataStore.edit { it[ONE_TAP_PHONE_ENABLED] = enabled }
    }

    suspend fun updatePassword(newPassword: String) {
        dataStore.edit { it[PASSWORD] = newPassword }
    }

    suspend fun updateIsDefaultLauncher(isDefault: Boolean) {
        dataStore.edit { it[IS_DEFAULT_LAUNCHER] = isDefault }
    }

    suspend fun updateShowExitLauncher(show: Boolean) {
        dataStore.edit { it[SHOW_EXIT_LAUNCHER] = show }
    }

    suspend fun updateLauncherExitConfirmation(needConfirmation: Boolean) {
        dataStore.edit { it[LAUNCHER_EXIT_CONFIRMATION] = needConfirmation }
    }

    suspend fun updateFamilyContactsPerPage(count: Int) {
        dataStore.edit { it[FAMILY_CONTACTS_PER_PAGE] = normalizeFamilyContactsPerPage(count) }
    }

    private fun normalizeFamilyContactsPerPage(count: Int): Int {
        return when {
            count <= 2 -> 2
            count <= 4 -> 4
            count <= 6 -> 6
            else -> 8
        }
    }

    /**
     * 验证密码
     */
    suspend fun verifyPassword(inputPassword: String): Boolean {
        return password.map { it == inputPassword }.first()
    }

    /**
     * 格式化时间显示
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun formatTimeForDisplay(time: String): String {
        return try {
            val localTime = LocalTime.parse(time, DateTimeFormatter.ISO_LOCAL_TIME)
            localTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            time
        }
    }
}
