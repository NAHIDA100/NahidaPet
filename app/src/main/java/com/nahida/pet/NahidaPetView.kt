package com.nahida.pet

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * 桌宠视图 — 使用用户提供的图片，保留浮动/呼吸/弹跳动画
 */
class NahidaPetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var petBitmap: Bitmap? = null
    private var bounceOffset = 0f
    private var floatOffset = 0f
    private var breatheScale = 1f

    private var bounceAnimator: ValueAnimator? = null
    private var breatheAnimator: ValueAnimator? = null
    private var floatAnimator: ValueAnimator? = null

    init {
        petBitmap = loadPetImage()
        startBreatheAnimation()
        startFloatAnimation()
    }

    /** 从 assets 加载图片并去除白色背景 */
    private fun loadPetImage(): Bitmap? {
        return try {
            val original = context.assets.open("nahida_pet.jpg").use {
                BitmapFactory.decodeStream(it)
            } ?: return null
            removeWhiteBackground(original)
        } catch (e: Exception) {
            null
        }
    }

    /** 将白色/接近白色像素变透明，边缘做渐变过渡 */
    private fun removeWhiteBackground(src: Bitmap): Bitmap {
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true) ?: return src
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // 感知亮度
            val brightness = (r * 0.299 + g * 0.587 + b * 0.114).toInt()
            when {
                brightness > 248 -> pixels[i] = 0x00000000
                brightness > 225 -> {
                    val alpha = ((248 - brightness) * 255 / 23).coerceIn(0, 255)
                    pixels[i] = (alpha shl 24) or (p and 0x00FFFFFF)
                }
            }
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return bmp
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = petBitmap ?: return

        val cx = width / 2f
        val cy = height / 2f
        // 缩放图片留 8% 边距
        val scale = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height) * 0.92f
        val drawW = bmp.width * scale
        val drawH = bmp.height * scale
        val left = cx - drawW / 2f
        val top = cy - drawH / 2f

        canvas.save()
        canvas.translate(0f, bounceOffset + floatOffset)
        canvas.scale(breatheScale, breatheScale, cx, cy)
        canvas.drawBitmap(bmp, null, RectF(left, top, left + drawW, top + drawH), paint)
        canvas.restore()
    }

    // ===== 动画 =====

    fun playBounceAnimation() {
        bounceAnimator?.cancel()
        bounceAnimator = ValueAnimator.ofFloat(0f, -18f, 0f, -8f, 0f, -3f, 0f).apply {
            duration = 550
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                bounceOffset = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startBreatheAnimation() {
        breatheAnimator = ValueAnimator.ofFloat(0.97f, 1.03f).apply {
            duration = 2600
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                breatheScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startFloatAnimation() {
        floatAnimator = ValueAnimator.ofFloat(-4f, 4f).apply {
            duration = 3200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                floatOffset = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        bounceAnimator?.cancel()
        breatheAnimator?.cancel()
        floatAnimator?.cancel()
    }
}
