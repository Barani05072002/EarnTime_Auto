package com.example.domain.usecases

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.data.entities.ControlledAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScanInstalledAppsUseCase(private val context: Context) {
    suspend operator fun invoke(): List<ControlledAppEntity> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        packages.filter { appInfo ->
            // Filter out system apps unless they have a launch intent
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                pm.getLaunchIntentForPackage(appInfo.packageName) != null
        }.mapNotNull { appInfo ->
            val appName = appInfo.loadLabel(pm).toString()
            if (pm.getLaunchIntentForPackage(appInfo.packageName) != null && appInfo.packageName != context.packageName) {
                ControlledAppEntity(
                    packageName = appInfo.packageName,
                    appName = appName
                )
            } else {
                null
            }
        }.sortedBy { it.appName }
    }
}
