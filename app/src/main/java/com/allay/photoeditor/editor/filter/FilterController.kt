package com.allay.photoeditor.editor.filter

import android.graphics.Bitmap
import android.util.Log
import com.allay.photoeditor.model.FilterType
import com.allay.photoeditor.processing.ImageFilterProcessor

/**
 * Owns the temporary filter-selection session for PhotoEditorView.
 *
 * The controller keeps filter changes non-destructive until Apply is pressed.
 * PhotoEditorView remains responsible for the committed editor bitmap and
 * canvas rendering.
 */
class FilterController(
    private val getCurrentBitmap: () -> Bitmap?,
    private val setCurrentBitmap: (Bitmap) -> Unit,
    private val canEnter: () -> Boolean,
    private val resetGestureState: () -> Unit,
    private val onModeChanged: (Boolean) -> Unit,
    private val invalidate: () -> Unit
) {

    companion object {
        private const val TAG = "PhotoEditor"
    }

    private var originalBitmap: Bitmap? = null
    private var previewBitmap: Bitmap? = null
    private var selectedFilter = FilterType.ORIGINAL

    var isActive: Boolean = false
        private set

    val currentPreviewBitmap: Bitmap?
        get() = previewBitmap

    val currentFilterType: FilterType
        get() = selectedFilter

    /** Starts a temporary filter session using the current editor bitmap. */
    fun enter() {
        if (isActive || !canEnter()) {
            Log.d(TAG, "Filter mode ignored: another editor mode is active")
            return
        }

        val currentBitmap = getCurrentBitmap()
        if (currentBitmap == null) {
            Log.d(TAG, "Filter mode ignored: no image selected")
            return
        }

        originalBitmap = currentBitmap
        previewBitmap = currentBitmap
        selectedFilter = FilterType.ORIGINAL
        isActive = true

        resetGestureState()
        onModeChanged(true)
        invalidate()

        Log.d(
            TAG,
            "Filter session started: bitmap=${currentBitmap.width}x${currentBitmap.height}"
        )
    }

    /** Selects a filter and replaces the temporary preview. */
    fun selectFilter(type: FilterType) {
        if (!isActive) {
            Log.d(TAG, "Filter ignored: filter mode is not active")
            return
        }

        val source = originalBitmap ?: getCurrentBitmap()
        if (source == null) {
            Log.w(TAG, "Filter ignored: source bitmap is missing")
            return
        }

        if (type == selectedFilter && previewBitmap != null) {
            return
        }

        val previousPreview = previewBitmap
        val newPreview = ImageFilterProcessor.process(
            source = source,
            filter = type
        )

        recycleIfTemporary(
            bitmap = previousPreview,
            source = source,
            replacement = newPreview
        )

        selectedFilter = type
        previewBitmap = newPreview
        invalidate()

        Log.d(TAG, "Filter selected: $type")
    }

    /** Commits the current temporary filter preview to the editor bitmap. */
    fun apply() {
        if (!isActive) {
            Log.d(TAG, "Apply filter ignored: filter mode is not active")
            return
        }

        val appliedBitmap = previewBitmap ?: originalBitmap ?: getCurrentBitmap()
        val sourceBitmap = originalBitmap
        val temporaryPreview = previewBitmap
        val appliedFilter = selectedFilter

        if (appliedBitmap == null) {
            cancel()
            return
        }

        setCurrentBitmap(appliedBitmap)

        recycleIfTemporary(
            bitmap = temporaryPreview,
            source = sourceBitmap,
            replacement = appliedBitmap
        )

        clearSessionFields()
        resetGestureState()
        onModeChanged(false)
        invalidate()

        Log.d(TAG, "Filter applied: $appliedFilter")
    }

    /** Discards the temporary filter preview and restores the session source. */
    fun cancel() {
        if (!isActive) return

        val sourceBitmap = originalBitmap
        val temporaryPreview = previewBitmap

        if (sourceBitmap != null) {
            setCurrentBitmap(sourceBitmap)
        }

        recycleIfTemporary(
            bitmap = temporaryPreview,
            source = sourceBitmap,
            replacement = sourceBitmap
        )

        clearSessionFields()
        resetGestureState()
        onModeChanged(false)
        invalidate()

        Log.d(TAG, "Filter session cancelled")
    }

    /** Clears temporary filter state, for example when a new image is loaded. */
    fun clear() {
        val sourceBitmap = originalBitmap
        val temporaryPreview = previewBitmap

        recycleIfTemporary(
            bitmap = temporaryPreview,
            source = sourceBitmap,
            replacement = sourceBitmap
        )

        clearSessionFields()
    }

    private fun clearSessionFields() {
        originalBitmap = null
        previewBitmap = null
        selectedFilter = FilterType.ORIGINAL
        isActive = false
    }

    private fun recycleIfTemporary(
        bitmap: Bitmap?,
        source: Bitmap?,
        replacement: Bitmap?
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
