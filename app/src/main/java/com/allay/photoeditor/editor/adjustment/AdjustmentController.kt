package com.allay.photoeditor.editor.adjustment

import android.graphics.Bitmap
import android.util.Log
import com.allay.photoeditor.model.AdjustmentState
import com.allay.photoeditor.processing.ImageAdjustmentProcessor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns the temporary image-adjustment editing session.
 *
 * PhotoEditorView remains responsible for rendering and editor state while this
 * controller owns adjustment state, preview generation and background preview
 * processing. The committed bitmap is changed only when Apply is requested.
 */
class AdjustmentController(
    private val getSourceBitmap: () -> Bitmap?,
    private val setCommittedBitmap: (Bitmap) -> Unit,
    private val resetGestureState: () -> Unit,
    private val onModeChanged: (Boolean) -> Unit,
    private val invalidate: () -> Unit
) {

    companion object {
        private const val TAG = "AdjustmentController"
    }

    private var originalBitmap: Bitmap? = null
    private var state = AdjustmentState()
    private var previewBitmap: Bitmap? = null

    private var previewExecutor: ExecutorService? = null

    @Volatile
    private var previewGeneration = 0L

    private var lastProcessedState: AdjustmentState? = null
    private var pendingApply = false

    private val pendingPreview =
        AtomicReference<AdjustmentPreviewRequest?>(null)

    private val previewWorkerRunning =
        AtomicBoolean(false)

    private data class AdjustmentPreviewRequest(
        val source: Bitmap,
        val state: AdjustmentState,
        val generation: Long
    )

    val isActive: Boolean
        get() = originalBitmap != null

    val currentState: AdjustmentState
        get() = state

    val currentPreviewBitmap: Bitmap?
        get() = previewBitmap

    /** Starts a temporary non-destructive adjustment session. */
    fun enter() {
        if (isActive) {
            Log.d(TAG, "Adjustment mode ignored: already active")
            return
        }

        val source = getSourceBitmap()
        if (source == null) {
            Log.d(TAG, "Adjustment mode ignored: no image selected")
            return
        }

        previewGeneration++
        pendingApply = false
        lastProcessedState = AdjustmentState()

        originalBitmap = source
        state = AdjustmentState()
        previewBitmap = source

        resetGestureState()
        onModeChanged(true)
        invalidate()

        Log.d(
            TAG,
            "Adjustment session started: " +
                    "bitmap=${source.width}x${source.height}"
        )
    }

    /** Updates the temporary state and schedules a background preview refresh. */
    fun setState(newState: AdjustmentState) {
        if (!isActive) {
            Log.d(TAG, "Adjustment state ignored: adjustment mode is not active")
            return
        }

        val source = originalBitmap ?: getSourceBitmap() ?: return

        if (newState == state) {
            return
        }

        state = newState
        pendingApply = false

        val generation = ++previewGeneration

        if (newState == AdjustmentState()) {
            pendingPreview.set(null)

            val previousPreview = previewBitmap
            previewBitmap = source
            lastProcessedState = newState

            recycleIfTemporary(
                bitmap = previousPreview,
                source = source
            )

            invalidate()
            Log.d(TAG, "Adjustment preview reset to original")
            return
        }

        pendingPreview.set(
            AdjustmentPreviewRequest(
                source = source,
                state = newState,
                generation = generation
            )
        )

        startPreviewWorkerIfNeeded()

        Log.d(
            TAG,
            "Adjustment preview scheduled: generation=$generation, state=$newState"
        )
    }

    /** Resets temporary adjustment values while keeping the session active. */
    fun reset() {
        if (!isActive) {
            Log.d(TAG, "Reset adjustments ignored: adjustment mode is not active")
            return
        }

        previewGeneration++
        pendingApply = false
        pendingPreview.set(null)

        val source = originalBitmap
        val previousPreview = previewBitmap

        state = AdjustmentState()
        previewBitmap = source
        lastProcessedState = AdjustmentState()

        recycleIfTemporary(
            bitmap = previousPreview,
            source = source
        )

        invalidate()
        Log.d(TAG, "Adjustments reset")
    }

    /** Commits the current preview when the newest preview has finished. */
    fun apply() {
        if (!isActive) {
            Log.d(TAG, "Apply adjustments ignored: adjustment mode is not active")
            return
        }

        if (lastProcessedState != state) {
            pendingApply = true
            Log.d(TAG, "Apply queued: waiting for latest adjustment preview")
            return
        }

        applyInternal()
    }

    /** Discards the temporary preview and restores the original session bitmap. */
    fun cancel() {
        if (!isActive) {
            Log.d(TAG, "Cancel adjustments ignored: adjustment mode is not active")
            return
        }

        previewGeneration++
        pendingApply = false
        pendingPreview.set(null)

        originalBitmap?.let { setCommittedBitmap(it) }

        clearSession()
        resetGestureState()
        onModeChanged(false)
        invalidate()

        Log.d(TAG, "Adjustments cancelled")
    }

    /** Clears the temporary session without invoking the mode callback. */
    fun clearSession() {
        previewGeneration++
        pendingApply = false
        pendingPreview.set(null)

        originalBitmap = null
        previewBitmap = null
        state = AdjustmentState()
        lastProcessedState = null
    }

    /** Stops the preview worker and invalidates pending preview generations. */
    fun close() {
        previewGeneration++
        pendingPreview.set(null)
        previewExecutor?.shutdownNow()
        previewExecutor = null
        previewWorkerRunning.set(false)
    }

    private fun applyInternal() {
        if (!isActive) {
            return
        }

        previewBitmap?.let { setCommittedBitmap(it) }

        clearSession()
        resetGestureState()
        onModeChanged(false)
        invalidate()

        Log.d(TAG, "Adjustments applied")
    }

    private fun startPreviewWorkerIfNeeded() {
        if (!previewWorkerRunning.compareAndSet(false, true)) {
            return
        }

        getPreviewExecutor().execute {
            processPendingPreviews()
        }
    }

    /** Processes only the newest pending request to avoid a slider backlog. */
    private fun processPendingPreviews() {
        while (true) {
            val request = pendingPreview.getAndSet(null) ?: break

            val preview = try {
                ImageAdjustmentProcessor.process(
                    source = request.source,
                    state = request.state
                )
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Adjustment preview processing failed",
                    exception
                )
                continue
            }

            postToUi {
                if (
                    !isActive ||
                    request.generation != previewGeneration
                ) {
                    recycleIfTemporary(
                        bitmap = preview,
                        source = request.source
                    )
                    return@postToUi
                }

                val previousPreview = previewBitmap

                previewBitmap = preview
                lastProcessedState = request.state

                recycleIfTemporary(
                    bitmap = previousPreview,
                    source = request.source,
                    replacement = preview
                )

                invalidate()

                Log.d(
                    TAG,
                    "Adjustment preview updated: state=${request.state}"
                )

                if (
                    pendingApply &&
                    lastProcessedState == state
                ) {
                    pendingApply = false
                    applyInternal()
                }
            }
        }

        previewWorkerRunning.set(false)

        if (pendingPreview.get() != null) {
            startPreviewWorkerIfNeeded()
        }
    }

    private fun getPreviewExecutor(): ExecutorService {
        return previewExecutor ?: Executors.newSingleThreadExecutor { runnable ->
            Thread(
                runnable,
                "PhotoEditor-AdjustmentPreview"
            ).apply {
                isDaemon = true
            }
        }.also { executor ->
            previewExecutor = executor
        }
    }

    private fun postToUi(action: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(action)
    }

    private fun recycleIfTemporary(
        bitmap: Bitmap?,
        source: Bitmap?,
        replacement: Bitmap? = null
    ) {
        if (
            bitmap != null &&
            bitmap !== source &&
            bitmap !== replacement &&
            !bitmap.isRecycled
        ) {
            bitmap.recycle()
        }
    }
}
