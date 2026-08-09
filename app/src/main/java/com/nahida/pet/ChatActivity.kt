package com.nahida.pet

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ChatActivity : AppCompatActivity() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnNewTopic: TextView

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatMessageAdapter
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        rvMessages = findViewById(R.id.rvMessages)
        etInput = findViewById(R.id.etChatInput)
        btnSend = findViewById(R.id.btnSend)
        tvTitle = findViewById(R.id.tvChatTitle)
        btnBack = findViewById(R.id.btnBack)
        btnNewTopic = findViewById(R.id.btnNewTopic)

        val nickname = ChatStorage.getNickname(this)
        tvTitle.text = "和纳西妲聊天"

        // 加载历史
        messages.addAll(ChatStorage.loadHistory(this))

        adapter = ChatMessageAdapter(this, messages, nickname)
        rvMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvMessages.adapter = adapter

        if (messages.isNotEmpty()) {
            rvMessages.scrollToPosition(messages.size - 1)
        }

        btnBack.setOnClickListener { finish() }

        btnSend.setOnClickListener { sendMessage() }

        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage(); true
            } else false
        }

        // 新话题
        btnNewTopic.setOnClickListener {
            messages.clear()
            ChatStorage.clearHistory(this)
            adapter.notifyDataSetChanged()
            addBotMessage("好的旅行者，我们聊点新话题吧~")
        }

        // 首次打开没有历史，欢迎语
        if (messages.isEmpty()) {
            addBotMessage("旅行者，你来找我聊天啦？今天想聊什么呢~")
        }
    }

    private fun sendMessage() {
        val text = etInput.text.toString().trim()
        if (text.isEmpty()) return
        etInput.setText("")

        messages.add(ChatMessage("user", text))
        adapter.notifyItemInserted(messages.size - 1)
        rvMessages.scrollToPosition(messages.size - 1)
        ChatStorage.saveHistory(this, messages)

        // 加个等待提示
        val waiting = ChatMessage("assistant", "让我想想...")
        messages.add(waiting)
        adapter.notifyItemInserted(messages.size - 1)
        rvMessages.scrollToPosition(messages.size - 1)

        val prefs = getSharedPreferences("pet_config", MODE_PRIVATE)
        val apiKey = prefs.getString("ai_api_key", "") ?: ""
        val provider = prefs.getString("ai_provider", "deepseek") ?: "deepseek"
        val nickname = ChatStorage.getNickname(this)

        if (apiKey.isEmpty()) {
            replaceLastMessage("你还没有设置 API Key 哦，去设置里填一下~")
            return
        }

        // 构造历史（去掉最后一条"让我想想"）
        val history = messages.dropLast(1).map { it.role to it.content }

        Thread {
            val userPrefix = "（用户昵称是「$nickname」）$text"
            val reply = AiChatHelper.chatWithHistory(provider, apiKey, userPrefix, history)
            handler.post {
                replaceLastMessage(reply)
                ChatStorage.saveHistory(this, messages)
            }
        }.start()
    }

    private fun addBotMessage(text: String) {
        messages.add(ChatMessage("assistant", text))
        adapter.notifyItemInserted(messages.size - 1)
        rvMessages.scrollToPosition(messages.size - 1)
        ChatStorage.saveHistory(this, messages)
    }

    private fun replaceLastMessage(newContent: String) {
        if (messages.isEmpty()) return
        val last = messages.last()
        messages[messages.size - 1] = ChatMessage(last.role, newContent, last.timestamp)
        adapter.notifyItemChanged(messages.size - 1)
        rvMessages.scrollToPosition(messages.size - 1)
    }
}
