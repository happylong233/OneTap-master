package tech.huangsh.onetap.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import tech.huangsh.onetap.ui.activity.LauncherChooserActivity

object LauncherUtils {
    fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    fun getAvailableLaunchers(context: Context): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        return context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_ALL
        ).filter { it.activityInfo.packageName != context.packageName }
    }

    fun openDefaultAppSettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )

        for (intent in intents) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (isIntentAvailable(context, intent)) {
                context.startActivity(intent)
                return
            }
        }
        Toast.makeText(context, "无法打开默认桌面设置", Toast.LENGTH_SHORT).show()
    }

    fun triggerDefaultLauncherChooser(context: Context) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
            Toast.makeText(context, "请按提示选择 OneTap 作为默认桌面", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            openDefaultAppSettings(context)
        }
    }

    fun forceShowLauncherChooser(context: Context) {
        try {
            context.startActivity(
                Intent(context, LauncherChooserActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (_: Exception) {
            triggerDefaultLauncherChooser(context)
        }
    }

    fun exitLauncherMode(context: Context) {
        openDefaultAppSettings(context)
        Toast.makeText(context, "请在默认桌面设置中选择原系统桌面", Toast.LENGTH_LONG).show()
    }

    fun getSystemDefaultLauncher(context: Context): String? {
        val launchers = getAvailableLaunchers(context)
        val systemLaunchers = launchers.filter { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            packageName.contains("launcher", ignoreCase = true) ||
                packageName.contains("home", ignoreCase = true) ||
                packageName.startsWith("com.android") ||
                packageName.startsWith("com.google.android") ||
                packageName.startsWith("com.samsung") ||
                packageName.startsWith("com.huawei") ||
                packageName.startsWith("com.xiaomi") ||
                packageName.startsWith("com.oppo") ||
                packageName.startsWith("com.vivo")
        }
        return systemLaunchers.firstOrNull()?.activityInfo?.packageName
            ?: launchers.firstOrNull()?.activityInfo?.packageName
    }

    fun launchSpecificLauncher(context: Context, packageName: String, className: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    component = ComponentName(packageName, className)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
        } catch (_: Exception) {
            Toast.makeText(context, "无法启动指定桌面", Toast.LENGTH_SHORT).show()
        }
    }

    fun resetToSystemLauncher(context: Context) {
        exitLauncherMode(context)
    }

    private fun isIntentAvailable(context: Context, intent: Intent): Boolean {
        return context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        ) != null
    }
}
