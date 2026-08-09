package com.nahida.pet

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务：检测当前前台应用，仅在桌面启动器上显示桌宠
 */
class PetAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PetAccessibility"
        private var instance: PetAccessibilityService? = null
        fun getInstance() = instance

        // 已知的各品牌桌面启动器包名
        private val LAUNCHER_PACKAGES = setOf(
            // 华为 / 荣耀
            "com.huawei.android.launcher",
            "com.hihonor.android.launcher",
            // 小米 / Redmi / POCO
            "com.miui.home",
            "com.mi.android.globallauncher",
            // OPPO / realme
            "com.oppo.launcher",
            "com.coloros.launcher",
            "com.realme.launcher",
            // vivo / iQOO
            "com.bbk.launcher2",
            "com.vivo.launcher",
            "com.iqoo.launcher",
            // 三星
            "com.sec.android.app.launcher",
            // 一加
            "com.oneplus.launcher",
            "net.oneplus.launcher",
            // Google Pixel
            "com.google.android.apps.nexuslauncher",
            // 魅族
            "com.meizu.flyme.launcher",
            // 中兴 / 努比亚
            "com.zte.mifavor.launcher",
            // 传音 (Tecno / Infinix / itel)
            "com.transsion.hilauncher",
            "com.transsion.XOSLauncher",
            // TCL / Alcatel
            "com.tct.launcher",
            // AOSP 通用
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3",
            "com.android.quickstep"
        )
    }

    private var isOnLauncher = true // 默认认为在桌面

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return
                if (packageName == "com.nahida.pet") return

                val onLauncher = isLauncher(packageName)

                // 状态变化时切换桌宠可见性
                if (onLauncher != isOnLauncher) {
                    isOnLauncher = onLauncher
                    NahidaPetService.getInstance()?.setPetVisible(onLauncher)
                    Log.d(TAG, "Launcher: $onLauncher, pkg: $packageName")
                }

                // 如果在桌面且桌宠已开启，显示气泡
                if (onLauncher) {
                    NahidaPetService.getInstance()?.showBubble("旅行者回来啦~")
                }
            }
        }
    }

    /** 判断是否是桌面启动器 */
    private fun isLauncher(packageName: String): Boolean {
        if (packageName in LAUNCHER_PACKAGES) return true
        // 兜底：包名含 launcher / home 的大概率是桌面
        val lower = packageName.lowercase()
        return lower.contains("launcher") || lower.contains(".home")
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        // 服务断开时恢复桌宠可见
        NahidaPetService.getInstance()?.setPetVisible(true)
        instance = null
    }
}
