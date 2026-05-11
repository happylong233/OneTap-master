package tech.huangsh.onetap.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import tech.huangsh.onetap.data.local.dao.AppInfoDao
import tech.huangsh.onetap.data.model.AppInfo
import tech.huangsh.onetap.utils.ImageUtils

class AppRepository(
    private val appInfoDao: AppInfoDao,
    private val context: Context
) {
    val enabledApps = appInfoDao.getEnabledApps()

    suspend fun getAvailableApps(): List<AppInfo> = queryLaunchableApps()

    suspend fun getCachedApps(): List<AppInfo> {
        val cached = appInfoDao.getAllAppsList()
        return if (cached.isNotEmpty()) cached else queryLaunchableApps()
    }

    suspend fun scanInstalledApps() {
        val enabledByPackage = appInfoDao.getEnabledAppsList().associateBy { it.packageName }
        val maxOrder = appInfoDao.getMaxOrder() ?: -1
        var nextOrder = maxOrder + 1

        queryLaunchableApps().forEach { scanned ->
            val existing = appInfoDao.getAppByPackage(scanned.packageName)
            val enabled = enabledByPackage[scanned.packageName]
            val appToSave = when {
                enabled != null -> scanned.copy(isEnabled = true, order = enabled.order)
                existing != null -> scanned.copy(isEnabled = existing.isEnabled, order = existing.order)
                else -> scanned.copy(isEnabled = false, order = nextOrder++)
            }
            appInfoDao.insertApp(appToSave)
        }
    }

    suspend fun insertApp(appInfo: AppInfo) {
        appInfoDao.insertApp(appInfo)
    }

    suspend fun disableApp(packageName: String) {
        appInfoDao.updateAppEnabledStatus(packageName, false)
    }

    suspend fun moveApp(packageName: String, fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        val apps = appInfoDao.getEnabledAppsList()
        if (fromPosition !in apps.indices || toPosition !in apps.indices) return

        if (fromPosition < toPosition) {
            for (i in fromPosition + 1..toPosition) {
                appInfoDao.updateAppOrder(apps[i].packageName, i - 1)
            }
        } else {
            for (i in toPosition until fromPosition) {
                appInfoDao.updateAppOrder(apps[i].packageName, i + 1)
            }
        }
        appInfoDao.updateAppOrder(packageName, toPosition)
    }

    suspend fun getMaxEnabledAppOrder(): Int? = appInfoDao.getMaxEnabledAppOrder()

    suspend fun deleteAllApps() {
        appInfoDao.deleteAllApps()
    }

    fun launchApp(packageName: String): Intent? {
        return context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun hasQueryAllPackagesPermission(): Boolean = true

    fun needsDynamicPermissionRequest(): Boolean = false

    fun needsManualPermissionSetting(): Boolean = false

    fun isMiuiSupportDynamicPermission(): Boolean = false

    fun isMiuiSystem(): Boolean {
        return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
            Build.BRAND.equals("Xiaomi", ignoreCase = true)
    }

    fun getMiuiPermissionIntent(): Intent? {
        return Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            putExtra("extra_pkgname", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun getAppSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun queryLaunchableApps(): List<AppInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = pm.queryIntentActivities(launcherIntent, 0)
        Log.d(TAG, "Launcher apps resolved=${resolved.size}")

        return resolved
            .asSequence()
            .filter { it.activityInfo != null }
            .filter { it.activityInfo.packageName != context.packageName }
            .filter { pm.getLaunchIntentForPackage(it.activityInfo.packageName) != null }
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                runCatching {
                    val packageInfo = pm.getPackageInfo(packageName, 0)
                    val label = resolveInfo.loadLabel(pm)?.toString()
                        ?: packageInfo.applicationInfo?.loadLabel(pm)?.toString()
                        ?: packageName
                    AppInfo(
                        packageName = packageName,
                        appName = label,
                        iconBytes = loadIconBytes(pm, resolveInfo),
                        isEnabled = false,
                        installTime = packageInfo.firstInstallTime,
                        lastUpdateTime = packageInfo.lastUpdateTime
                    )
                }.onFailure {
                    Log.w(TAG, "Unable to load app info for $packageName", it)
                }.getOrNull()
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    private fun loadIconBytes(
        packageManager: PackageManager,
        resolveInfo: android.content.pm.ResolveInfo
    ): ByteArray? {
        return runCatching {
            val drawable = resolveInfo.loadIcon(packageManager)
            val bitmap = ImageUtils.drawableToBitmap(drawable)
            val scaled = if (bitmap.width > MAX_ICON_SIZE || bitmap.height > MAX_ICON_SIZE) {
                ImageUtils.resizeBitmap(bitmap, MAX_ICON_SIZE, MAX_ICON_SIZE)
            } else {
                bitmap
            }
            ImageUtils.bitmapToByteArray(scaled, quality = 90)
        }.onFailure {
            Log.w(TAG, "Unable to load icon for ${resolveInfo.activityInfo.packageName}", it)
        }.getOrNull()
    }

    private companion object {
        const val TAG = "AppRepository"
        const val MAX_ICON_SIZE = 128
    }
}
