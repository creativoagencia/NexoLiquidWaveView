package com.nexo.nexoliquidwaveview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class NexoLiquidWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val wavePath = Path()

    private var isAnimating = false
    private var waveLevel = 0f
    private var targetWaveLevel = 0f

    // Tiempo general de animación
    private var time = 0f

    private data class Bubble(
        var x: Float,
        var y: Float,
        var radius: Float,
        var speed: Float,
        var alpha: Int,
        var drift: Float
    )

    private val bubbles = mutableListOf<Bubble>()
    private val maxBubbles = 18

    fun startAnimation() {
        if (isAnimating) return
        isAnimating = true
        invalidate()
    }

    fun stopAnimation() {
        isAnimating = false
        invalidate()
    }

    fun setWaveLevel(level: Float) {
        val newLevel = level.coerceIn(0f, 1f)
        val wasZero = targetWaveLevel <= 0.01f && waveLevel <= 0.01f

        targetWaveLevel = newLevel

        if (!isAnimating) {
            waveLevel = targetWaveLevel
        }

        if (wasZero && newLevel > 0.01f) {
            invalidate()
        } else {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        waveLevel += (targetWaveLevel - waveLevel) * 0.12f

        if (waveLevel <= 0.01f && targetWaveLevel <= 0.01f) {
            waveLevel = 0f
            bubbles.clear()

            if (isAnimating) {
                postInvalidateOnAnimation()
            }
            return
        }

        val centerY = h * 0.80f
        val compressedLevel = 1f - (1f - waveLevel) * (1f - waveLevel)

        // altura principal de la ola
        val amplitudeMain = h * (0.008f + compressedLevel * 0.10f)

        // detalle local para romper uniformidad
        val amplitudeDetail = h * (0.003f + compressedLevel * 0.028f)

        // olas bastante juntas
        val waveLengthMain = w / 2.5f
        val waveLengthDetail = w / 1.15f

        wavePath.reset()
        wavePath.moveTo(0f, h)

        var x = 0f
        while (x <= w) {
            val nx = x / w

            // Forma base
            val base = sin(((x / waveLengthMain) * 2f * PI).toFloat())

            // Modulación local: cada zona "respira" distinto
            val localMotion1 = sin((time * 1.7f) + nx * 8f)
            val localMotion2 = sin((time * 2.3f) + nx * 15f)
            val localMotion3 = sin((time * 1.1f) - nx * 12f)

            // Envolvente para que unos picos crezcan más que otros
            val localEnvelope =
                0.65f +
                        0.20f * localMotion1 +
                        0.10f * localMotion2 +
                        0.05f * localMotion3

            val detail = sin(
                ((x / waveLengthDetail) * 2f * PI).toFloat() +
                        localMotion1 * 0.6f +
                        localMotion2 * 0.35f
            )

            val y = centerY +
                    (amplitudeMain * localEnvelope) * base +
                    amplitudeDetail * detail

            wavePath.lineTo(x, y)
            x += 5f
        }

        wavePath.lineTo(w, h)
        wavePath.close()

        canvas.drawPath(wavePath, wavePaint)

        updateAndDrawBubbles(
            canvas = canvas,
            w = w,
            centerY = centerY,
            amplitudeMain = amplitudeMain,
            amplitudeDetail = amplitudeDetail,
            waveLengthMain = waveLengthMain,
            waveLengthDetail = waveLengthDetail
        )

        if (isAnimating) {
            time += 0.08f
            postInvalidateOnAnimation()
        }
    }

    private fun updateAndDrawBubbles(
        canvas: Canvas,
        w: Float,
        centerY: Float,
        amplitudeMain: Float,
        amplitudeDetail: Float,
        waveLengthMain: Float,
        waveLengthDetail: Float
    ) {
        if (waveLevel > 0.03f && bubbles.size < maxBubbles) {
            val chance = 0.20f + (waveLevel * 0.30f)
            if (Random.nextFloat() < chance) {
                val bubbleX = Random.nextFloat() * w
                bubbles.add(
                    Bubble(
                        x = bubbleX,
                        y = getWaveSurfaceY(
                            x = bubbleX,
                            centerY = centerY,
                            amplitudeMain = amplitudeMain,
                            amplitudeDetail = amplitudeDetail,
                            waveLengthMain = waveLengthMain,
                            waveLengthDetail = waveLengthDetail
                        ) - Random.nextFloat() * 16f,
                        radius = 2f + Random.nextFloat() * (3f + waveLevel * 4f),
                        speed = 1f + Random.nextFloat() * (1.2f + waveLevel * 1.6f),
                        alpha = 70 + Random.nextInt(90),
                        drift = -0.4f + Random.nextFloat() * 0.8f
                    )
                )
            }
        }

        val iterator = bubbles.iterator()
        while (iterator.hasNext()) {
            val bubble = iterator.next()

            bubble.y -= bubble.speed
            bubble.x += bubble.drift

            if (bubble.y < -20f || bubble.x < -20f || bubble.x > w + 20f) {
                iterator.remove()
                continue
            }

            bubblePaint.alpha = bubble.alpha
            canvas.drawCircle(bubble.x, bubble.y, bubble.radius, bubblePaint)
        }
    }

    private fun getWaveSurfaceY(
        x: Float,
        centerY: Float,
        amplitudeMain: Float,
        amplitudeDetail: Float,
        waveLengthMain: Float,
        waveLengthDetail: Float
    ): Float {
        val nx = x / width.toFloat().coerceAtLeast(1f)

        val base = sin(((x / waveLengthMain) * 2f * PI).toFloat())

        val localMotion1 = sin((time * 1.7f) + nx * 8f)
        val localMotion2 = sin((time * 2.3f) + nx * 15f)
        val localMotion3 = sin((time * 1.1f) - nx * 12f)

        val localEnvelope =
            0.65f +
                    0.20f * localMotion1 +
                    0.10f * localMotion2 +
                    0.05f * localMotion3

        val detail = sin(
            ((x / waveLengthDetail) * 2f * PI).toFloat() +
                    localMotion1 * 0.6f +
                    localMotion2 * 0.35f
        )

        return centerY +
                (amplitudeMain * localEnvelope) * base +
                amplitudeDetail * detail
    }
}