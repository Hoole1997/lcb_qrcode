package com.android.common.scanner.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.sqrt

/**
 * ImageView with pinch-to-zoom and pan support.
 * Optimized for performance with minimal object allocation during touch events.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    companion object {
        private const val MIN_SCALE = 1.0f
        private const val MAX_SCALE = 5.0f
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }

    private val matrix = Matrix()
    private val savedMatrix = Matrix()
    private val matrixValues = FloatArray(9)

    private var mode = NONE
    private val startPoint = PointF()
    private val midPoint = PointF()
    private var oldDist = 1f
    private var currentScale = 1f

    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    private var viewWidth = 0
    private var viewHeight = 0
    private var imageWidth = 0f
    private var imageHeight = 0f
    private var isInitialized = false
    private var zoomAnimator: ValueAnimator? = null

    init {
        scaleType = ScaleType.MATRIX

        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val newScale = currentScale * scaleFactor

                if (newScale in MIN_SCALE..MAX_SCALE) {
                    currentScale = newScale
                    matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                    constrainMatrix()
                    imageMatrix = matrix
                }
                return true
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Double tap to reset or zoom with smooth animation
                if (currentScale > MIN_SCALE + 0.1f) {
                    animateZoomTo(MIN_SCALE, e.x, e.y)
                } else {
                    // Zoom to 2x at tap point with animation
                    animateZoomTo(2.0f, e.x, e.y)
                }
                return true
            }
        })
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        if (drawable != null && !isInitialized) {
            initializeMatrix()
        }
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        isInitialized = false
        if (bm != null && viewWidth > 0 && viewHeight > 0) {
            initializeMatrix()
        }
    }

    private fun initializeMatrix() {
        val drawable = drawable ?: return
        imageWidth = drawable.intrinsicWidth.toFloat()
        imageHeight = drawable.intrinsicHeight.toFloat()

        if (imageWidth <= 0 || imageHeight <= 0) return

        // Fit center initially
        val scaleX = viewWidth / imageWidth
        val scaleY = viewHeight / imageHeight
        val scale = minOf(scaleX, scaleY)

        val dx = (viewWidth - imageWidth * scale) / 2f
        val dy = (viewHeight - imageHeight * scale) / 2f

        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)
        imageMatrix = matrix

        currentScale = 1f
        isInitialized = true
    }

    fun resetToFitCenter() {
        initializeMatrix()
    }

    private fun animateZoomTo(targetScale: Float, focusX: Float, focusY: Float) {
        zoomAnimator?.cancel()

        val startScale = currentScale
        val startMatrix = Matrix(matrix)

        zoomAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                val animatedScale = startScale + (targetScale - startScale) * fraction
                val scaleFactor = animatedScale / currentScale

                currentScale = animatedScale
                matrix.set(startMatrix)
                val totalScaleFactor = animatedScale / startScale
                matrix.postScale(totalScaleFactor, totalScaleFactor, focusX, focusY)
                constrainMatrix()
                imageMatrix = matrix
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(matrix)
                startPoint.set(event.x, event.y)
                mode = DRAG
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    oldDist = spacing(event)
                    if (oldDist > 10f) {
                        savedMatrix.set(matrix)
                        midPoint(midPoint, event)
                        mode = ZOOM
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG && !scaleDetector.isInProgress) {
                    val dx = event.x - startPoint.x
                    val dy = event.y - startPoint.y
                    matrix.set(savedMatrix)
                    matrix.postTranslate(dx, dy)
                    constrainMatrix()
                    imageMatrix = matrix

                    // Allow ViewPager to intercept if at edge and trying to swipe
                    if (isAtEdge(dx)) {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun constrainMatrix() {
        matrix.getValues(matrixValues)
        val currentScaleX = matrixValues[Matrix.MSCALE_X]
        var transX = matrixValues[Matrix.MTRANS_X]
        var transY = matrixValues[Matrix.MTRANS_Y]

        val scaledWidth = imageWidth * currentScaleX
        val scaledHeight = imageHeight * currentScaleX

        // Constrain translation
        val minX: Float
        val maxX: Float
        val minY: Float
        val maxY: Float

        if (scaledWidth <= viewWidth) {
            minX = (viewWidth - scaledWidth) / 2f
            maxX = minX
        } else {
            minX = viewWidth - scaledWidth
            maxX = 0f
        }

        if (scaledHeight <= viewHeight) {
            minY = (viewHeight - scaledHeight) / 2f
            maxY = minY
        } else {
            minY = viewHeight - scaledHeight
            maxY = 0f
        }

        if (transX < minX) transX = minX
        if (transX > maxX) transX = maxX
        if (transY < minY) transY = minY
        if (transY > maxY) transY = maxY

        matrixValues[Matrix.MTRANS_X] = transX
        matrixValues[Matrix.MTRANS_Y] = transY
        matrix.setValues(matrixValues)
    }

    private fun isAtEdge(dx: Float): Boolean {
        if (currentScale <= MIN_SCALE + 0.1f) return true

        matrix.getValues(matrixValues)
        val transX = matrixValues[Matrix.MTRANS_X]
        val currentScaleX = matrixValues[Matrix.MSCALE_X]
        val scaledWidth = imageWidth * currentScaleX

        // At left edge and swiping right, or at right edge and swiping left
        val atLeftEdge = transX >= 0 && dx > 0
        val atRightEdge = transX <= viewWidth - scaledWidth && dx < 0

        return atLeftEdge || atRightEdge
    }

    private fun spacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return sqrt(x * x + y * y)
    }

    private fun midPoint(point: PointF, event: MotionEvent) {
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point.set(x / 2, y / 2)
    }

}
