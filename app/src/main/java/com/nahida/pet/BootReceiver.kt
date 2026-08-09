package com.nahida.pet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 开机/应用更新后自动重启桌宠服务，防止系统杀后台
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val prefs = context.getSharedPreferences("pet_config", Context.MODE_PRIVATE)
        val wasRunning = prefs.getBoolean("pet_was_running", false)
        if (!wasRunning) return

        Log.d("BootReceiver", "Auto-restarting pet service after $action")
        try {
            NahidaPetService.startService(context)
        } catch (e: Exception) {
            Log.e("BootReceiver", "Failed to restart service", e)
        }
    }
}
