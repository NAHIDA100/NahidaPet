package com.nahida.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatMessageAdapter(
    private val context: Context,
    private val messages: List<ChatMessage>,
    private val nickname: String
) : RecyclerView.Adapter<ChatMessageAdapter.VH>() {

    companion object {
        const val TYPE_USER = 0
        const val TYPE_ASSISTANT = 1
    }

    private val petAvatar: Bitmap? by lazy { loadPetAvatar() }
    private val userAvatar: Bitmap? by lazy { loadUserAvatar() }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].role == "user") TYPE_USER else TYPE_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layoutId = if (viewType == TYPE_USER) R.layout.item_chat_user else R.layout.item_chat_assistant
        val view = LayoutInflater.from(context).inflate(layoutId, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = messages[position]
        holder.tvContent.text = msg.content

        val avatarView = holder.ivAvatar
        if (msg.role == "user") {
            avatarView.setImageBitmap(userAvatar ?: createDefaultAvatar(0xFF7ED957.toInt(), nickname.firstOrNull()?.toString() ?: "旅"))
        } else {
            avatarView.setImageBitmap(petAvatar ?: createDefaultAvatar(0xFF9BD67A.toInt(), "纳"))
        }

        holder.tvName?.text = if (msg.role == "user") nickname else "纳西妲"
    }

    override fun getItemCount() = messages.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
        val tvName: TextView? = view.findViewById(R.id.tvName)
    }

    private fun loadPetAvatar(): Bitmap? {
        return try {
            val original = context.assets.open("nahida_pet.jpg").use { BitmapFactory.decodeStream(it) } ?: return null
            cropToCircle(original)
        } catch (_: Exception) { null }
    }

    private fun loadUserAvatar(): Bitmap? {
        val uriStr = ChatStorage.getUserAvatar(context) ?: return null
        return try {
            val uri = Uri.parse(uriStr)
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(input)
            input.close()
            cropToCircle(bitmap)
        } catch (_: Exception) { null }
    }

    private fun cropToCircle(src: Bitmap): Bitmap {
        val size = src.width.coerceAtMost(src.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawOval(rect, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val left = (src.width - size) / 2
        val top = (src.height - size) / 2
        canvas.drawBitmap(src, -left.toFloat(), -top.toFloat(), paint)
        return output
    }

    private fun createDefaultAvatar(color: Int, letter: String): Bitmap {
        val size = 80
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = 32f
        paint.textAlign = Paint.Align.CENTER
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(letter, size / 2f, y, paint)
        return bmp
    }
}
