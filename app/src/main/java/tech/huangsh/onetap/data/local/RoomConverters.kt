package tech.huangsh.onetap.data.local

import androidx.room.TypeConverter
import tech.huangsh.onetap.data.model.PreferredCallMethod

class RoomConverters {
    @TypeConverter
    fun preferredCallMethodToString(value: PreferredCallMethod): String = value.name

    @TypeConverter
    fun stringToPreferredCallMethod(value: String): PreferredCallMethod =
        runCatching { PreferredCallMethod.valueOf(value) }.getOrDefault(PreferredCallMethod.PHONE_CALL)
}
