package com.allay.photoeditor.processing

import android.graphics.Bitmap
import com.allay.photoeditor.model.AdjustmentState

/**
 * Public facade for image-adjustment processing.
 *
 * Keeps callers independent from the internal processing pipeline.
 */
object ImageAdjustmentProcessor {

    /**
     * Applies the supplied adjustment state to the source bitmap.
     *
     * The source bitmap is never modified directly.
     */
    fun process(
        source: Bitmap,
        state: AdjustmentState
    ): Bitmap {
        return AdjustmentProcessingPipeline.process(
            source = source,
            state = state
        )
    }
}