package com.lomkich.brickbreak

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private data class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float, val radius: Float)
private data class Brick(val rect: RectF, var hp: Int, val color: Int)
private data class Bonus(var x: Float, var y: Float, val kind: BonusKind, val radius: Float = 15f)
private enum class BonusKind { TRIPLE, WIDE, SLOW, LIFE }

class BrickBreakView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val bg = 0xFF10131C.toInt()
    private val white = 0xFFF3F5FA.toInt()
    private val muted = 0xFF929BB0.toInt()
    private val accent = 0xFF62E6B5.toInt()
    private val brickColors = intArrayOf(0xFFFF6B6B.toInt(), 0xFFFFB86B.toInt(), 0xFFFFD866.toInt(), 0xFF6BE3A4.toInt(), 0xFF6BB8FF.toInt())
    private val balls = mutableListOf<Ball>()
    private val bricks = mutableListOf<Brick>()
    private val bonuses = mutableListOf<Bonus>()
    private var paddle = RectF()
    private var paddleWidth = 112f
    private var ballRadius = 7f
    private var score = 0
    private var lives = 3
    private var level = 1
    private var running = false
    private var gameOver = false
    private var won = false
    private var lastFrame = 0L
    private var message = "tap to launch"
    private var messageUntil = 0L
    private var lastBonusAt = 0L
    private var slowUntil = 0L
    private var wideUntil = 0L
    private var started = false

    init {
        isFocusable = true
    }

    private fun dp(value: Float) = value * density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ballRadius = dp(6f)
        paddleWidth = dp(112f)
        paddle = RectF(w / 2f - paddleWidth / 2f, h - dp(62f), w / 2f + paddleWidth / 2f, h - dp(48f))
        if (!started && w > 0 && h > 0) {
            started = true
            resetLevel()
        }
    }

    private fun resetLevel() {
        balls.clear()
        bonuses.clear()
        bricks.clear()
        paddleWidth = dp(if (System.currentTimeMillis() < wideUntil) 150f else 112f)
        paddle.set(width / 2f - paddleWidth / 2f, height - dp(62f), width / 2f + paddleWidth / 2f, height - dp(48f))
        val cols = 7
        val rows = min(5 + (level - 1) / 2, 7)
        val gap = dp(5f)
        val side = dp(16f)
        val brickW = (width - side * 2 - gap * (cols - 1)) / cols
        val brickH = dp(22f)
        val top = dp(90f)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val left = side + c * (brickW + gap)
                val rect = RectF(left, top + r * (brickH + gap), left + brickW, top + r * (brickH + gap) + brickH)
                bricks += Brick(rect, if (r == 0 && level > 1) 2 else 1, brickColors[r % brickColors.size])
            }
        }
        balls += Ball(width / 2f, paddle.top - ballRadius - dp(2f), dp(190f), -dp(250f), ballRadius)
        running = false
        gameOver = false
        won = false
        message = "tap to launch"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(bg)
        drawHud(canvas)
        drawBricks(canvas)
        drawPaddle(canvas)
        drawBalls(canvas)
        drawBonuses(canvas)
        if (gameOver || won || !running) drawOverlay(canvas)
        val now = System.currentTimeMillis()
        if (running && !gameOver && !won) {
            val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame).coerceIn(1L, 32L) / 1000f)
            lastFrame = now
            updateGame(dt, now)
            postInvalidateOnAnimation()
        } else {
            lastFrame = 0L
            if (messageUntil > now && !gameOver && !won) postInvalidateDelayed(80L)
        }
    }

    private fun drawHud(canvas: Canvas) {
        paint.color = white
        paint.textSize = dp(19f)
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        canvas.drawText("BRICK BREAK", dp(18f), dp(34f), paint)
        paint.textSize = dp(13f)
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        paint.color = muted
        canvas.drawText("SCORE  $score", dp(18f), dp(57f), paint)
        val livesText = "♥".repeat(max(0, lives))
        paint.color = 0xFFFF777F.toInt()
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(livesText, width - dp(18f), dp(35f), paint)
        paint.color = muted
        canvas.drawText("LVL $level", width - dp(18f), dp(57f), paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawBricks(canvas: Canvas) {
        for (brick in bricks) {
            paint.color = brick.color
            canvas.drawRoundRect(brick.rect, dp(5f), dp(5f), paint)
            if (brick.hp > 1) {
                paint.color = 0xAAFFFFFF.toInt()
                canvas.drawRoundRect(brick.rect.left + dp(4f), brick.rect.top + dp(4f), brick.rect.right - dp(4f), brick.rect.bottom - dp(4f), dp(3f), dp(3f), paint)
            }
        }
    }

    private fun drawPaddle(canvas: Canvas) {
        paint.color = accent
        canvas.drawRoundRect(paddle, dp(8f), dp(8f), paint)
    }

    private fun drawBalls(canvas: Canvas) {
        paint.color = white
        for (ball in balls) canvas.drawCircle(ball.x, ball.y, ball.radius, paint)
    }

    private fun drawBonuses(canvas: Canvas) {
        for (bonus in bonuses) {
            paint.color = when (bonus.kind) {
                BonusKind.TRIPLE -> 0xFFB89CFF.toInt()
                BonusKind.WIDE -> 0xFF62D7FF.toInt()
                BonusKind.SLOW -> 0xFFFFD866.toInt()
                BonusKind.LIFE -> 0xFFFF777F.toInt()
            }
            canvas.drawCircle(bonus.x, bonus.y, dp(13f), paint)
            paint.color = bg
            paint.textSize = dp(12f)
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            val label = when (bonus.kind) {
                BonusKind.TRIPLE -> "×3"
                BonusKind.WIDE -> "↔"
                BonusKind.SLOW -> "Ⅱ"
                BonusKind.LIFE -> "+"
            }
            canvas.drawText(label, bonus.x, bonus.y + dp(4f), paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawOverlay(canvas: Canvas) {
        paint.color = 0xB8000000.toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = white
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        paint.textSize = dp(29f)
        val title = when {
            gameOver -> "GAME OVER"
            won -> "LEVEL CLEAR"
            else -> "READY?"
        }
        canvas.drawText(title, width / 2f, height / 2f - dp(15f), paint)
        paint.textSize = dp(15f)
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.color = muted
        val subtitle = when {
            gameOver -> "tap to try again"
            won -> "tap for next level"
            else -> "tap anywhere to launch"
        }
        canvas.drawText(subtitle, width / 2f, height / 2f + dp(18f), paint)
        if (messageUntil > System.currentTimeMillis() && message.isNotBlank()) {
            paint.color = accent
            canvas.drawText(message, width / 2f, height / 2f + dp(48f), paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun updateGame(dt: Float, now: Long) {
        if (now > wideUntil && paddleWidth > dp(112f)) {
            paddleWidth = dp(112f)
            paddle.right = paddle.left + paddleWidth
            clampPaddle()
        }
        val speedFactor = if (now < slowUntil) 0.68f else 1f
        val toRemoveBricks = mutableListOf<Brick>()
        val toRemoveBalls = mutableListOf<Ball>()
        for (ball in balls) {
            ball.x += ball.vx * dt * speedFactor
            ball.y += ball.vy * dt * speedFactor
            if (ball.x - ball.radius < 0f) { ball.x = ball.radius; ball.vx = abs(ball.vx) }
            if (ball.x + ball.radius > width) { ball.x = width - ball.radius; ball.vx = -abs(ball.vx) }
            if (ball.y - ball.radius < dp(68f)) { ball.y = dp(68f) + ball.radius; ball.vy = abs(ball.vy) }
            if (ball.vy > 0 && ball.y + ball.radius >= paddle.top && ball.y - ball.radius <= paddle.bottom &&
                ball.x >= paddle.left - ball.radius && ball.x <= paddle.right + ball.radius) {
                ball.y = paddle.top - ball.radius
                val offset = ((ball.x - paddle.centerX()) / (paddle.width() / 2f)).coerceIn(-1f, 1f)
                val speed = max(dp(260f), kotlin.math.sqrt(ball.vx * ball.vx + ball.vy * ball.vy))
                ball.vx = offset * speed * 0.85f
                ball.vy = -kotlin.math.sqrt(max(dp(100f) * dp(100f), speed * speed - ball.vx * ball.vx))
            }
            if (ball.y - ball.radius > height) toRemoveBalls += ball
            for (brick in bricks) {
                if (brick in toRemoveBricks) continue
                if (ball.x + ball.radius >= brick.rect.left && ball.x - ball.radius <= brick.rect.right &&
                    ball.y + ball.radius >= brick.rect.top && ball.y - ball.radius <= brick.rect.bottom) {
                    ball.vy = -ball.vy
                    brick.hp--
                    if (brick.hp <= 0) {
                        toRemoveBricks += brick
                        score += 10
                        if (Random.nextFloat() < 0.18f) {
                            val kinds = BonusKind.values()
                            bonuses += Bonus(brick.rect.centerX(), brick.rect.centerY(), kinds[Random.nextInt(kinds.size)], dp(13f))
                        }
                    }
                    break
                }
            }
        }
        balls.removeAll(toRemoveBalls.toSet())
        bricks.removeAll(toRemoveBricks.toSet())
        if (balls.isEmpty()) {
            lives--
            if (lives <= 0) {
                gameOver = true
                running = false
            } else {
                balls += Ball(paddle.centerX(), paddle.top - ballRadius - dp(2f), dp(190f), -dp(250f), ballRadius)
                running = false
                message = "tap to continue"
                messageUntil = now + 1500L
            }
        }
        val caught = bonuses.filter { it.y + dp(13f) >= paddle.top && it.y <= paddle.bottom && it.x in paddle.left..paddle.right }
        for (bonus in caught) applyBonus(bonus.kind, now)
        bonuses.removeAll(caught.toSet())
        bonuses.removeAll { it.y > height }
        bonuses.forEach { it.y += dp(105f) * dt }
        if (bricks.isEmpty() && !gameOver) {
            won = true
            running = false
        }
    }

    private fun applyBonus(kind: BonusKind, now: Long) {
        when (kind) {
            BonusKind.TRIPLE -> {
                val source = balls.firstOrNull()
                if (source != null) {
                    val copies = balls.toList()
                    for (ball in copies) {
                        balls += Ball(ball.x, ball.y, -ball.vx - dp(65f), ball.vy, ball.radius)
                        balls += Ball(ball.x, ball.y, -ball.vx + dp(65f), ball.vy, ball.radius)
                    }
                }
                message = "BALL MULTIPLIED ×3"
            }
            BonusKind.WIDE -> {
                wideUntil = now + 9000L
                paddleWidth = dp(160f)
                paddle.right = paddle.left + paddleWidth
                clampPaddle()
                message = "WIDE PADDLE"
            }
            BonusKind.SLOW -> {
                slowUntil = now + 7000L
                message = "SLOW MOTION"
            }
            BonusKind.LIFE -> {
                lives = min(5, lives + 1)
                message = "+1 LIFE"
            }
        }
        messageUntil = now + 1500L
        invalidate()
    }

    private fun clampPaddle() {
        if (paddle.left < 0f) paddle.offset(-paddle.left, 0f)
        if (paddle.right > width) paddle.offset(width - paddle.right, 0f)
    }

    private fun movePaddle(x: Float) {
        paddle.offset(x - paddle.centerX(), 0f)
        clampPaddle()
        if (!running && !gameOver && !won && balls.size == 1) {
            val ball = balls.first()
            ball.x = paddle.centerX()
            ball.y = paddle.top - ball.radius - dp(2f)
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (event.y > dp(65f) && !gameOver && !won) movePaddle(event.x)
                if (event.actionMasked == MotionEvent.ACTION_DOWN && (gameOver || won)) {
                    if (gameOver) {
                        score = 0
                        lives = 3
                        level = 1
                    } else {
                        level++
                    }
                    resetLevel()
                } else if (event.actionMasked == MotionEvent.ACTION_DOWN && !running) {
                    running = true
                    lastFrame = 0L
                    if (balls.isNotEmpty() && balls.size == 1) {
                        val ball = balls[0]
                        if (abs(ball.vx) < dp(1f)) ball.vx = if (Random.nextBoolean()) dp(180f) else -dp(180f)
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> return true
        }
        return true
    }
}
