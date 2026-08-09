package com.nahida.pet

data class ChatMessage(
    val role: String,       // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
