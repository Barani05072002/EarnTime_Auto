package com.example.service

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

object AppSuspender {
    fun setAppSuspended(context: Context, packageName: String, suspended: Boolean) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, EarnTimeDeviceAdminReceiver::class.java)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)) {
                    val packages = arrayOf(packageName)
                    dpm.setPackagesSuspended(adminComponent, packages, suspended)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isAppSuspended(context: Context, packageName: String): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                dpm.isPackageSuspended(ComponentName(context, EarnTimeDeviceAdminReceiver::class.java), packageName)
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
