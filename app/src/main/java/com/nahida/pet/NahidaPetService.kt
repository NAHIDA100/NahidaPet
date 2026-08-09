package com.nahida.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class NahidaPetService : Service() {

    companion object {
        private const val TAG = "NahidaPetService"
        private const val CHANNEL_ID = "nahida_pet_channel"
        private const val NOTIFICATION_ID = 1001
        private const val LONG_PRESS_MS = 2000L
        private var instance: NahidaPetService? = null
        private var petView: NahidaPetView? = null

        fun getInstance() = instance
        fun getPetView() = petView

        fun startService(context: Context) {
            val intent = Intent(context, NahidaPetService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private var container: View? = null
    private var bubbleView: TextView? = null
    private var chatContainer: View? = null
    private var chatInput: EditText? = null
    private var isDragging = false
    private var isLongPressTriggered = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    // 聊天历史
    private val chatHistory = mutableListOf<Pair<String, String>>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        }
        showPet()
        // 记录运行状态，供开机自启使用
        getSharedPreferences("pet_config", MODE_PRIVATE).edit().putBoolean("pet_was_running", true).apply()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        longPressRunnable?.let { handler.removeCallbacks(it) }
        chatContainer?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        container?.let { try { windowManager.removeView(it) } catch (e: Exception) { Log.e(TAG, "Error removing view", e) } }
        petView = null
        instance = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.pet_service_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = "小草神桌宠正在运行"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else { PendingIntent.FLAG_UPDATE_CURRENT }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingFlags)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    // ======================== 桌宠主体 ========================

    private fun showPet() {
        if (container != null) return

        val containerLayout = FrameLayout(this)
        val nahidaView = NahidaPetView(this)
        petView = nahidaView

        val bubble = TextView(this).apply {
            text = "你好呀，旅行者~"
            setTextColor(0xFF333333.toInt())
            textSize = 12f
            setPadding(24, 12, 24, 12)
            background = createBubbleBackground(0xFF9BD67A.toInt())
            gravity = Gravity.CENTER
            visibility = View.GONE
            maxWidth = dpToPx(200)
        }
        bubbleView = bubble

        // 屏幕自适应：手机 ~120dp，平板按比例放大
        val dm = resources.displayMetrics
        val screenMin = dm.widthPixels.coerceAtMost(dm.heightPixels)
        val petSize = (screenMin * 0.12f).toInt().coerceIn(dpToPx(100), dpToPx(200))
        containerLayout.addView(nahidaView, FrameLayout.LayoutParams(petSize, petSize).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        })
        // 气泡放在形象正上方，不遮挡脸部
        containerLayout.addView(bubble, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(4)
        })

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(20)
            y = dpToPx(200)
        }

        // 拖拽 + 点击 + 长按检测
        containerLayout.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = false
                        isLongPressTriggered = false
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY

                        // 启动长按计时器
                        longPressRunnable = object : Runnable {
                            override fun run() {
                                if (!isDragging) {
                                    isLongPressTriggered = true
                                    onPetLongPressed()
                                }
                            }
                        }
                        handler.postDelayed(longPressRunnable!!, LONG_PRESS_MS)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (!isDragging && (dx * dx + dy * dy > 100)) {
                            isDragging = true
                            longPressRunnable?.let { handler.removeCallbacks(it) }
                        }
                        if (isDragging) {
                            params.x = initialX + dx.toInt()
                            params.y = initialY + dy.toInt()
                            try { windowManager.updateViewLayout(containerLayout, params) } catch (e: Exception) { Log.e(TAG, "updateViewLayout", e) }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                        if (!isDragging && !isLongPressTriggered) {
                            view.performClick()
                            onPetClicked()
                        }
                        return true
                    }
                }
                return false
            }
        })

        container = containerLayout
        try {
            windowManager.addView(containerLayout, params)
        } catch (e: Exception) {
            Log.e(TAG, "addView error", e); stopSelf()
        }
    }

    // ======================== 短按：弹跳 + 随机/AI 对话 ========================

    private fun onPetClicked() {
        petView?.playBounceAnimation()

        val prefs = getSharedPreferences("pet_config", MODE_PRIVATE)
        if (!prefs.getBoolean("dialogue_enabled", false)) return

        val apiKey = prefs.getString("ai_api_key", "") ?: ""
        if (apiKey.isEmpty()) {
            showBubble(getRandomDialogue())
            return
        }

        val provider = prefs.getString("ai_provider", "deepseek") ?: "deepseek"
        showBubble("让我想想...")
        Thread {
            val reply = AiChatHelper.chat(provider, apiKey, "旅行者轻轻戳了戳纳西妲的脸")
            handler.post { showBubble(reply) }
        }.start()
    }

    // ======================== 长按2秒：弹出聊天输入框 ========================

    private fun onPetLongPressed() {
        petView?.playBounceAnimation()

        val prefs = getSharedPreferences("pet_config", MODE_PRIVATE)
        val apiKey = prefs.getString("ai_api_key", "") ?: ""

        if (!prefs.getBoolean("dialogue_enabled", false) || apiKey.isEmpty()) {
            showBubble("需要先在设置里开启对话并配置 API Key 哦~")
            return
        }

        // 延迟300ms弹出，等 ACTION_UP 事件被容器消费完再创建窗口
        handler.postDelayed(object : Runnable {
            override fun run() { showChatInput() }
        }, 300)
    }

    private fun showChatInput() {
        // 强制清理残留状态，防止第二次以后打不开或闪退
        forceCleanupChat()

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 屏幕自适应：对话框宽度取屏幕宽度的 75%，用 dp 限制上下限
        val density = resources.displayMetrics.density
        val screenWDp = (resources.displayMetrics.widthPixels / density).toInt()
        val chatWidthDp = (screenWDp * 0.75f).toInt().coerceIn(280, 500)
        val chatWidthPx = (chatWidthDp * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBg(0xFFFFFFFF.toInt(), 0xFF9BD67A.toInt(), 20f)
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
        }

        // 标题行
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "🌿 和纳西妲聊天"
            setTextColor(0xFF4FA03A.toInt())
            textSize = 14f
        }
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(0xFF999999.toInt())
            textSize = 18f
            setPadding(dpToPx(14), 0, 0, 0)
            setOnClickListener { hideChatInput() }
        }
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(closeBtn)
        root.addView(titleRow)

        // 输入行
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(10), 0, 0)
        }

        val input = EditText(this).apply {
            hint = "对纳西妲说..."
            setTextColor(0xFF333333.toInt())
            setHintTextColor(0xFFBBBBBB.toInt())
            textSize = 14f
            background = createRoundedBg(0xFFF5F5F5.toInt(), 0xFFDDDDDD.toInt(), 12f)
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_SEND
            isSingleLine = true
            isFocusable = true
            isFocusableInTouchMode = true
        }
        chatInput = input

        val sendBtn = TextView(this).apply {
            text = "发送"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
            background = createRoundedBg(0xFF7ED957.toInt(), 0xFF5AAF44.toInt(), 12f)
            setOnClickListener { sendMessage() }
        }

        input.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendMessage()
                    return true
                }
                return false
            }
        })

        inputRow.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        inputRow.addView(sendBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dpToPx(8) })
        root.addView(inputRow)

        // 独立窗口：FLAG_NOT_TOUCH_MODAL 让键盘能弹出
        // 不用 FLAG_NOT_FOCUSABLE，否则键盘永远不出
        val chatParams = WindowManager.LayoutParams(
            chatWidthPx, WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            // ADJUST_PAN 让界面上移给键盘腾空间，STATE_ALWAYS_VISIBLE 强制弹键盘
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }

        chatContainer = root
        try {
            windowManager.addView(root, chatParams)
            // 多次尝试弹键盘，兼容各品牌机型
            input.requestFocus()
            handler.postDelayed({
                try {
                    input.requestFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                } catch (_: Exception) {}
            }, 300)
            handler.postDelayed({
                try {
                    input.requestFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.toggleSoftInput(android.view.inputmethod.InputMethodManager.SHOW_FORCED, 0)
                } catch (_: Exception) {}
            }, 600)
        } catch (e: Exception) {
            Log.e(TAG, "showChatInput error", e)
            chatContainer = null
        }
    }

    private fun hideChatInput() {
        forceCleanupChat()
    }

    private fun sendMessage() {
        val input = chatInput ?: return
        val msg = input.text.toString().trim()
        if (msg.isEmpty()) return

        input.setText("")
        hideChatInput()
        showBubble("让我想想...")

        val prefs = getSharedPreferences("pet_config", MODE_PRIVATE)
        val apiKey = prefs.getString("ai_api_key", "") ?: return
        val provider = prefs.getString("ai_provider", "deepseek") ?: "deepseek"

        // 添加到历史
        chatHistory.add("user" to msg)
        // 只保留最近5轮
        while (chatHistory.size > 10) chatHistory.removeAt(0)

        Thread {
            val reply = AiChatHelper.chatWithHistory(provider, apiKey, msg, chatHistory)
            chatHistory.add("assistant" to reply)
            handler.post { showBubble(reply) }
        }.start()
    }

    // ======================== 气泡 ========================

    fun showBubble(text: String) {
        bubbleView?.apply {
            this.text = text
            visibility = View.VISIBLE
            alpha = 1f
            val hideRunnable = object : Runnable {
                override fun run() {
                    animate().alpha(0f).setDuration(500).withEndAction { visibility = View.GONE }.start()
                }
            }
            removeCallbacks(null)
            postDelayed(hideRunnable, 5000)
        }
    }

    // ======================== 工具方法 ========================

    private fun getRandomDialogue(): String {
        val dialogues = listOf(
            "你好呀，旅行者~\n今天也要开心哦！", "嘿嘿，想找我聊天吗？",
            "世界树又长新叶子啦~", "要不要一起去须弥看看？",
            "你知道吗，梦境很有趣的！", "哼哼，我可是智慧之神哦~",
            "今天天气真好呢！", "你有没有什么想问我的？",
            "草元素的力量，无所不在！", "嘻嘻，摸摸我的头~",
            "纳西妲最喜欢旅行者了！", "要不要我给你讲个故事？"
        )
        return dialogues[Math.floor(Math.random() * dialogues.size).toInt()]
    }

    private fun createBubbleBackground(borderColor: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFFFFFFFF.toInt()); cornerRadius = 24f; setStroke(2, borderColor)
        }
    }

    private fun createRoundedBg(fillColor: Int, strokeColor: Int, radius: Float): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(fillColor); cornerRadius = radius; setStroke(2, strokeColor)
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    /** 强制清理聊天窗口残留，防止第二次以后不稳定 */
    private fun forceCleanupChat() {
        // 取消所有待执行的键盘弹出回调
        handler.removeCallbacksAndMessages("show_keyboard")
        chatContainer?.let {
            try {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                chatInput?.let { input -> imm.hideSoftInputFromWindow(input.windowToken, 0) }
                windowManager.removeViewImmediate(it)
            } catch (_: Exception) {}
        }
        chatContainer = null
        chatInput = null
    }

    fun setPetVisible(visible: Boolean) {
        container?.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) hideChatInput()
    }

    fun stopPet() {
        hideChatInput()
        getSharedPreferences("pet_config", MODE_PRIVATE).edit().putBoolean("pet_was_running", false).apply()
        try { stopForeground(true) } catch (e: Exception) { Log.e(TAG, "stopForeground", e) }
        stopSelf()
    }
}
