package tech.huangsh.onetap.data.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * 联系人数据模型。
 * [wechatDisplayName] 持久化列名为历史字段 `wechatNickname`。
 */
@Entity(tableName = "contacts")
@Parcelize
data class Contact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 显示名称（家属输入） */
    val name: String,
    /** 关系称呼，可空 */
    val relationLabel: String? = null,
    val phone: String? = null,
    @ColumnInfo(name = "wechatNickname")
    val wechatDisplayName: String? = null,
    /** 微信号，可选；搜索关键词优先级低于备注名 */
    val wechatId: String? = null,
    val avatarUri: String? = null,
    val preferredCallMethod: String = PreferredCallMethod.WECHAT_VIDEO.name,
    val isFavorite: Boolean = false,
    val isVisibleOnHome: Boolean = true,
    val isEmergency: Boolean = false,
    val order: Int = 0,
    /** 兼容旧版 UI，保存时与 [preferredCallMethod] 同步 */
    val hasVideoCall: Boolean = false,
    val hasVoiceCall: Boolean = false,
    val hasPhoneCall: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Parcelable {

    val supportedActions: List<String>
        get() = mutableListOf<String>().apply {
            if (hasVideoCall) add("video")
            if (hasVoiceCall) add("voice")
            if (hasPhoneCall) add("phone")
        }

    /** 微信搜索关键词：备注名优先，其次微信号 */
    fun resolveWeChatSearchKeyword(): String? {
        val remark = wechatDisplayName?.trim().orEmpty()
        if (remark.isNotEmpty()) return remark
        val id = wechatId?.trim().orEmpty()
        if (id.isNotEmpty()) return id
        return null
    }

    fun preferredMethodEnum(): PreferredCallMethod =
        try {
            PreferredCallMethod.valueOf(preferredCallMethod)
        } catch (_: IllegalArgumentException) {
            PreferredCallMethod.WECHAT_VIDEO
        }

    /** 与 [preferredCallMethod] 对齐旧字段，便于兼容数据库列 */
    fun withSyncedLegacyFlags(): Contact {
        return when (preferredMethodEnum()) {
            PreferredCallMethod.WECHAT_VIDEO -> copy(
                hasVideoCall = true,
                hasVoiceCall = false,
                hasPhoneCall = false
            )
            PreferredCallMethod.WECHAT_VOICE -> copy(
                hasVideoCall = false,
                hasVoiceCall = true,
                hasPhoneCall = false
            )
            PreferredCallMethod.PHONE_CALL -> copy(
                hasVideoCall = false,
                hasVoiceCall = false,
                hasPhoneCall = true
            )
        }
    }
}
