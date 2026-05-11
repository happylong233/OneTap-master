package tech.huangsh.onetap.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2：扩展联系人字段（老人模式）。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE contacts ADD COLUMN relationLabel TEXT")
        database.execSQL("ALTER TABLE contacts ADD COLUMN wechatId TEXT")
        database.execSQL(
            "ALTER TABLE contacts ADD COLUMN preferredCallMethod TEXT NOT NULL DEFAULT 'WECHAT_VIDEO'"
        )
        database.execSQL("ALTER TABLE contacts ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE contacts ADD COLUMN isVisibleOnHome INTEGER NOT NULL DEFAULT 1")
        database.execSQL("ALTER TABLE contacts ADD COLUMN isEmergency INTEGER NOT NULL DEFAULT 0")
        database.execSQL(
            """
            UPDATE contacts SET preferredCallMethod = CASE
                WHEN hasVideoCall != 0 THEN 'WECHAT_VIDEO'
                WHEN hasVoiceCall != 0 THEN 'WECHAT_VOICE'
                WHEN hasPhoneCall != 0 THEN 'PHONE_CALL'
                ELSE 'WECHAT_VIDEO'
            END
            """.trimIndent()
        )
    }
}
