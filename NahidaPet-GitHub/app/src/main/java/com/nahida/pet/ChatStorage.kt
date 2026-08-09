package com.nahida.pet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ChatStorage {

    private const val PREF_NAME = "pet_config"
    private const val KEY_HISTORY = "chat_history"
    private const val KEY_NICKNAME = "user_nickname"
    private const val KEY_USER_AVATAR = "user_avatar_uri"
    private const val MAX_MESSAGES = 200

    fun saveHistory(context: Context, messages: List<ChatMessage>) {
        val arr = JSONArray()
        val toSave = if (messages.size > MAX_MESSAGES) messages.takeLast(MAX_MESSAGES) else messages
        for (m in toSave) {
            arr.put(JSONObject().apply {
                put("role", m.role)
                put("content", m.content)
                put("ts", m.timestamp)
            })
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun loadHistory(context: Context): List<ChatMessage> {
        val str = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(str)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ChatMessage(obj.getString("role"), obj.getString("content"), obj.optLong("ts", 0L))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_HISTORY).apply()
    }

    fun getNickname(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_NICKNAME, "旅行者") ?: "旅行者"
    }

    fun setNickname(context: Context, name: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_NICKNAME, name).apply()
    }

    fun getUserAvatar(context: Context): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_AVATAR, null)
    }

    fun setUserAvatar(context: Context, uri: String?) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_USER_AVATAR, uri).apply()
    }
}
