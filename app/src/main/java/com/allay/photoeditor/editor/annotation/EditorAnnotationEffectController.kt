package com.allay.photoeditor.editor.annotation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.PointF
import com.allay.photoeditor.editor.core.EditorCoordinateMapper
import com.allay.photoeditor.model.AnnotationElement
import com.allay.photoeditor.model.AnnotationType
import com.allay.photoeditor.model.EditorElement

/**
 * Owns the visual rendering and bitmap caches used by Blur and Pixelate
 * annotations. It does not own annotation elements, history or annotation mode
 * state; those remain with the existing editor controllers.
 */
class EditorAnnotationEffectController(
    private val elements: List<EditorElement>,
    private val coordinateMapper: EditorCoordinateMapper,
    private val annotationController: AnnotationController,
    private val bitmapPaint: Paint,
    private val annotationInteractionPathProvider: () -> Path?,
    private val annotationInteractionTypeProvider: () -> AnnotationType,
    private val eraserPointProvider: () -> PointF?
) {
    /** Soft translucent fill for the eraser cursor. */
    private val eraserPreviewFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        alpha = 32
    }

    /** High-contrast outline for the eraser cursor. */
    private val eraserPreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
        alpha = 220
    }

    val blurMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val blurPreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2f
        color = Color.WHITE
        alpha = 180
    }

    /** Semi-transparent tint applied over pixelated regions. */
    val pixelateColorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 120
    }

    /** Cached blurred version of the current base bitmap. */
    private var blurredBitmap: Bitmap? = null
    private var blurredBitmapSource: Bitmap? = null

    /** Cached pixelated version of the current base bitmap. */
    private var pixelatedBitmap: Bitmap? = null
    private var pixelatedBitmapSource: Bitmap? = null

    fun drawBlurAnnotations(canvas: Canvas, sourceBitmap: Bitmap) {
        val blurAnnotations = elements
            .asSequence()
            .filterIsInstance<AnnotationElement>()
            .filter { it.annotationType == AnnotationType.BLUR && it.isVisible }
            .toList()

        if (blurAnnotations.isEmpty()) return

        val blurred = getOrCreateBlurredBitmap(sourceBitmap) ?: return
        val imageToScreenMatrix = coordinateMapper.imageToScreenMatrix
        val screenScale = coordinateMapper.currentScreenScale()

        for (annotation in blurAnnotations) {
            val transformedPath = Path(annotation.path)
            transformedPath.transform(imageToScreenMatrix)

            canvas.saveLayer(null, null)
            canvas.drawBitmap(
                blurred,
                imageToScreenMatrix,
                bitmapPaint
            )

            blurMaskPaint.strokeWidth = annotation.strokeWidth * screenScale
            blurMaskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawPath(transformedPath, blurMaskPaint)
            blurMaskPaint.xfermode = null
            canvas.restore()
        }
    }

    fun drawPixelateAnnotations(canvas: Canvas, sourceBitmap: Bitmap) {
        val pixelateAnnotations = elements
            .asSequence()
            .filterIsInstance<AnnotationElement>()
            .filter { it.annotationType == AnnotationType.PIXELATE && it.isVisible }
            .toList()

        if (pixelateAnnotations.isEmpty()) return

        val pixelated = getOrCreatePixelatedBitmap(sourceBitmap) ?: return
        val imageToScreenMatrix = coordinateMapper.imageToScreenMatrix
        val screenScale = coordinateMapper.currentScreenScale()

        for (annotation in pixelateAnnotations) {
            val transformedPath = Path(annotation.path)
            transformedPath.transform(imageToScreenMatrix)

            blurMaskPaint.style = Paint.Style.STROKE
            blurMaskPaint.strokeWidth = annotation.strokeWidth * screenScale
            blurMaskPaint.xfermode = null

            val maskPath = Path()
            blurMaskPaint.getFillPath(transformedPath, maskPath)

            canvas.save()
            canvas.clipPath(maskPath)
            canvas.drawBitmap(
                pixelated,
                imageToScreenMatrix,
                bitmapPaint
            )

            pixelateColorPaint.color = annotation.color
            canvas.drawPath(maskPath, pixelateColorPaint)
            canvas.restore()
        }
    }

    /** Draws the in-progress annotation preview without adding an element. */
    fun drawActiveFreehandPath(canvas: Canvas) {
        val path = annotationInteractionPathProvider() ?: return
        val annotationType = annotationInteractionTypeProvider()
        val imageToScreenMatrix = coordinateMapper.imageToScreenMatrix

        if (
            annotationType == AnnotationType.BLUR ||
            annotationType == AnnotationType.PIXELATE
        ) {
            val transformedPath = Path(path)
            transformedPath.transform(imageToScreenMatrix)
            blurPreviewPaint.strokeWidth =
                annotationController.currentStrokeWidth * coordinateMapper.currentScreenScale()
            canvas.drawPath(transformedPath, blurPreviewPaint)
            return
        }

        val previewAnnotation = annotationController.createAnnotation(
            path = path,
            annotationType = annotationType,
            color = annotationController.currentColor,
            strokeWidth = annotationController.currentStrokeWidth
        )

        previewAnnotation.draw(
            canvas = canvas,
            matrix = imageToScreenMatrix
        )
    }

    /** Draws the visible eraser cursor. */
    fun drawActiveEraserPreview(canvas: Canvas) {
        val point = eraserPointProvider() ?: return
        val imageRadius = annotationController.currentStrokeWidth / 2f
        val screenRadius = imageRadius * coordinateMapper.currentScreenScale()

        if (screenRadius <= 0f) return

        canvas.drawCircle(point.x, point.y, screenRadius, eraserPreviewFillPaint)
        canvas.drawCircle(point.x, point.y, screenRadius, eraserPreviewPaint)
    }

    fun clearCaches() {
        blurredBitmap = null
        blurredBitmapSource = null
        pixelatedBitmap = null
        pixelatedBitmapSource = null
    }

    fun getOrCreatePixelatedBitmap(source: Bitmap): Bitmap? {
        if (
            pixelatedBitmapSource === source &&
            pixelatedBitmap != null &&
            !pixelatedBitmap!!.isRecycled
        ) {
            return pixelatedBitmap
        }

        pixelatedBitmap = null
        pixelatedBitmapSource = null

        return try {
            val output = Bitmap.createBitmap(
                source.width,
                source.height,
                Bitmap.Config.ARGB_8888
            )

            val pixels = IntArray(source.width * source.height)
            source.getPixels(
                pixels,
                0,
                source.width,
                0,
                0,
                source.width,
                source.height
            )

            val blockSize = 16
            var blockTop = 0
            while (blockTop < source.height) {
                val blockBottom = minOf(blockTop + blockSize, source.height)
                var blockLeft = 0
                while (blockLeft < source.width) {
                    val blockRight = minOf(blockLeft + blockSize, source.width)
                    var a = 0
                    var r = 0
                    var g = 0
                    var b = 0
                    var count = 0

                    for (y in blockTop until blockBottom) {
                        val row = y * source.width
                        for (x in blockLeft until blockRight) {
                            val color = pixels[row + x]
                            a += Color.alpha(color)
                            r += Color.red(color)
                            g += Color.green(color)
                            b += Color.blue(color)
                            count++
                        }
                    }

                    if (count > 0) {
                        val average = Color.argb(
                            a / count,
                            r / count,
                            g / count,
                            b / count
                        )
                        for (y in blockTop until blockBottom) {
                            val row = y * source.width
                            for (x in blockLeft until blockRight) {
                                pixels[row + x] = average
                            }
                        }
                    }
                    blockLeft += blockSize
                }
                blockTop += blockSize
            }

            output.setPixels(
                pixels,
                0,
                source.width,
                0,
                0,
                source.width,
                source.height
            )

            pixelatedBitmap = output
            pixelatedBitmapSource = source
            output
        } catch (exception: Exception) {
            android.util.Log.e(
                "EditorAnnotationEffect",
                "Unable to create pixelated bitmap",
                exception
            )
            pixelatedBitmap = null
            pixelatedBitmapSource = null
            null
        }
    }

    fun getOrCreateBlurredBitmap(source: Bitmap): Bitmap? {
        if (
            blurredBitmapSource === source &&
            blurredBitmap != null &&
            !blurredBitmap!!.isRecycled
        ) {
            return blurredBitmap
        }

        blurredBitmap = null
        blurredBitmapSource = null

        return try {
            val output = Bitmap.createBitmap(
                source.width,
                source.height,
                Bitmap.Config.ARGB_8888
            )
            drawFallbackBlur(source, output)
            blurredBitmap = output
            blurredBitmapSource = source
            output
        } catch (exception: Exception) {
            android.util.Log.e(
                "EditorAnnotationEffect",
                "Unable to create blurred bitmap",
                exception
            )
            blurredBitmap = null
            blurredBitmapSource = null
            null
        }
    }

    private fun drawFallbackBlur(source: Bitmap, output: Bitmap) {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)

        val temp = IntArray(pixels.size)
        val radius = 7

        for (y in 0 until source.height) {
            val row = y * source.width
            for (x in 0 until source.width) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                val start = maxOf(0, x - radius)
                val end = minOf(source.width - 1, x + radius)
                for (sampleX in start..end) {
                    val color = pixels[row + sampleX]
                    a += Color.alpha(color)
                    r += Color.red(color)
                    g += Color.green(color)
                    b += Color.blue(color)
                    count++
                }
                temp[row + x] = Color.argb(a / count, r / count, g / count, b / count)
            }
        }

        for (x in 0 until source.width) {
            for (y in 0 until source.height) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                val start = maxOf(0, y - radius)
                val end = minOf(source.height - 1, y + radius)
                for (sampleY in start..end) {
                    val color = temp[sampleY * source.width + x]
                    a += Color.alpha(color)
                    r += Color.red(color)
                    g += Color.green(color)
                    b += Color.blue(color)
                    count++
                }
                pixels[y * source.width + x] = Color.argb(a / count, r / count, g / count, b / count)
            }
        }

        output.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    }
}
