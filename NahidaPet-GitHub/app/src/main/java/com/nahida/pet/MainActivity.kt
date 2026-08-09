package com.nahida.pet

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_PICK_AVATAR = 1001
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var statusText: TextView
    private lateinit var btnOverlay: Button
    private lateinit var btnAccessibility: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var switchDialogue: Switch
    private lateinit var aiSettingsArea: LinearLayout
    private lateinit var rgProvider: RadioGroup
    private lateinit var rbDeepSeek: RadioButton
    private lateinit var rbMiMo: RadioButton
    private lateinit var etApiKey: EditText
    private lateinit var btnSaveAi: Button
    private lateinit var linkDeepSeek: TextView
    private lateinit var linkMiMo: TextView
    private lateinit var btnChat: Button
    private lateinit var etNickname: EditText
    private lateinit var btnSaveNickname: Button
    private lateinit var ivUserAvatar: ImageView
    private lateinit var btnPickAvatar: Button
    private lateinit var btnBattery: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("pet_config", MODE_PRIVATE)

        // 基础控件
        statusText = findViewById(R.id.statusText)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        // AI 设置
        switchDialogue = findViewById(R.id.switchDialogue)
        aiSettingsArea = findViewById(R.id.aiSettingsArea)
        rgProvider = findViewById(R.id.rgProvider)
        rbDeepSeek = findViewById(R.id.rbDeepSeek)
        rbMiMo = findViewById(R.id.rbMiMo)
        etApiKey = findViewById(R.id.etApiKey)
        btnSaveAi = findViewById(R.id.btnSaveAi)
        linkDeepSeek = findViewById(R.id.linkDeepSeek)
        linkMiMo = findViewById(R.id.linkMiMo)

        // 聊天 + 个性化
        btnChat = findViewById(R.id.btnChat)
        etNickname = findViewById(R.id.etNickname)
        btnSaveNickname = findViewById(R.id.btnSaveNickname)
        ivUserAvatar = findViewById(R.id.ivUserAvatar)
        btnPickAvatar = findViewById(R.id.btnPickAvatar)
        btnBattery = findViewById(R.id.btnBattery)

        loadSettings()

        // 对话开关
        switchDialogue.setOnCheckedChangeListener { _, checked ->
            aiSettingsArea.visibility = if (checked) View.VISIBLE else View.GONE
            prefs.edit().putBoolean("dialogue_enabled", checked).apply()
        }

        // 保存 AI 设置
        btnSaveAi.setOnClickListener {
            val provider = if (rbMiMo.isChecked) "mimo" else "deepseek"
            val apiKey = etApiKey.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("ai_provider", provider).putString("ai_api_key", apiKey).apply()
            Toast.makeText(this, "AI 设置已保存", Toast.LENGTH_SHORT).show()
        }

        // 平台链接
        linkDeepSeek.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.deepseek.com")))
        }
        linkMiMo.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dev.mi.com")))
        }

        // 昵称保存
        btnSaveNickname.setOnClickListener {
            val name = etNickname.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "昵称不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ChatStorage.setNickname(this, name)
            Toast.makeText(this, "昵称已保存：$name", Toast.LENGTH_SHORT).show()
        }

        // 头像选择
        btnPickAvatar.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, REQ_PICK_AVATAR)
        }

        // 进入聊天
        btnChat.setOnClickListener {
            val apiKey = prefs.getString("ai_api_key", "") ?: ""
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "请先设置 API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, ChatActivity::class.java))
        }

        // 防杀后台 - 弹出选择方式
        btnBattery.setOnClickListener { showAntiKillDialog() }

        // 权限按钮
        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "悬浮窗权限已开启", Toast.LENGTH_SHORT).show()
            }
        }

        btnAccessibility.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(this, "请找到「小草神桌宠」并开启", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show()
            }
        }

        btnStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先开启悬浮窗权限！", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            NahidaPetService.startService(this)
            Toast.makeText(this, "小草神已经出现啦~", Toast.LENGTH_SHORT).show()
            updateStatus()
        }

        btnStop.setOnClickListener {
            NahidaPetService.getInstance()?.stopPet()
            Toast.makeText(this, "小草神下次再见~", Toast.LENGTH_SHORT).show()
            updateStatus()
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        loadNicknameAndAvatar()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_AVATAR && resultCode == RESULT_OK && data != null) {
            val uri = data.data ?: return
            // 持久化 URI 权限
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            ChatStorage.setUserAvatar(this, uri.toString())
            loadNicknameAndAvatar()
            Toast.makeText(this, "头像已更新", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSettings() {
        val enabled = prefs.getBoolean("dialogue_enabled", false)
        val provider = prefs.getString("ai_provider", "deepseek") ?: "deepseek"
        val apiKey = prefs.getString("ai_api_key", "") ?: ""

        switchDialogue.isChecked = enabled
        aiSettingsArea.visibility = if (enabled) View.VISIBLE else View.GONE
        if (provider == "mimo") rbMiMo.isChecked = true else rbDeepSeek.isChecked = true
        if (apiKey.isNotEmpty()) etApiKey.setText(apiKey)

        loadNicknameAndAvatar()
    }

    private fun loadNicknameAndAvatar() {
        val nickname = ChatStorage.getNickname(this)
        etNickname.setText(nickname)

        val avatarUri = ChatStorage.getUserAvatar(this)
        if (avatarUri != null) {
            try {
                ivUserAvatar.setImageURI(Uri.parse(avatarUri))
            } catch (_: Exception) {
                ivUserAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
            }
        } else {
            ivUserAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
        }
    }

    private fun updateStatus() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAccessibility = isAccessibilityServiceEnabled()

        val sb = StringBuilder()
        sb.append("悬浮窗权限：").append(if (hasOverlay) "✅ 已开启" else "❌ 未开启").append("\n")
        sb.append("无障碍服务：").append(if (hasAccessibility) "✅ 已开启" else "❌ 未开启").append("\n")
        sb.append("\n桌宠状态：").append(if (NahidaPetService.getInstance() != null) "🟢 运行中" else "⚪ 未启动")
        statusText.text = sb.toString()
        btnStart.isEnabled = hasOverlay
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val name = ComponentName(this, PetAccessibilityService::class.java).flattenToString()
            val list = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            list.contains(name)
        } catch (e: Exception) { false }
    }

    private fun showAntiKillDialog() {
        val items = arrayOf(
            "电池优化白名单（推荐，无需额外App）",
            "Shizuku 权限（需安装 Shizuku，未用过请谨慎选择）"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择防杀后台方式")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> requestBatteryOptimization()
                    1 -> requestShizukuKeepAlive()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun requestBatteryOptimization() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "已在电池优化白名单中", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestShizukuKeepAlive() {
        try {
            packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        } catch (e: Exception) {
            Toast.makeText(this, "请先安装 Shizuku App", Toast.LENGTH_LONG).show()
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app"))) } catch (_: Exception) {}
            return
        }

        try {
            if (!rikka.shizuku.Shizuku.pingBinder()) {
                Toast.makeText(this, "请先通过 Shizuku App 启动服务", Toast.LENGTH_LONG).show()
                return
            }
        } catch (e: Throwable) {
            Toast.makeText(this, "Shizuku 未运行，请先激活", Toast.LENGTH_LONG).show()
            return
        }

        try {
            if (rikka.shizuku.Shizuku.checkSelfPermission() == -1) {
                rikka.shizuku.Shizuku.requestPermission(1002)
                Toast.makeText(this, "请在弹出的对话框中授权", Toast.LENGTH_SHORT).show()
                return
            }
            executeShizukuKeepAlive()
        } catch (e: Throwable) {
            Toast.makeText(this, "Shizuku 错误：" + e.message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002) {
            if (grantResults.isNotEmpty() && grantResults[0] == 0) {
                executeShizukuKeepAlive()
            } else {
                Toast.makeText(this, "Shizuku 权限被拒绝", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun executeShizukuKeepAlive() {
        Thread {
            val cmds = arrayOf(
                "cmd appops set $packageName RUN_IN_BACKGROUND allow",
                "cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow",
                "dumpsys deviceidle whitelist +$packageName"
            )
            var success = 0
            try {
                val binder = rikka.shizuku.Shizuku.getBinder()
                val stubClass = Class.forName("rikka.shizuku.server.api.IPrivilegedService\$Stub")
                val asInterface = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
                val service = asInterface.invoke(null, binder)
                val newProcess = service.javaClass.getMethod("newProcess", Array<String>::class.java, String::class.java, String::class.java)
                for (cmd in cmds) {
                    try {
                        val proc = newProcess.invoke(service, arrayOf("sh", "-c", cmd), null, null) as Process
                        proc.waitFor()
                        success++
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Shizuku 调用失败：" + e.message, Toast.LENGTH_LONG).show() }
                return@Thread
            }
            runOnUiThread {
                Toast.makeText(this, "Shizuku: $success/${cmds.size} 条命令执行成功", Toast.LENGTH_LONG).show()
            }
        }.start()
    }
}
