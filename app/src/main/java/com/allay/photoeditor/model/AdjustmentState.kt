package com.allay.photoeditor.model

/**
 * Non-destructive image adjustment values for the current editing session.
 *
 * For the Phase 7.5 UI, brightness, contrast and saturation use a 0..100
 * range where 0 is neutral and higher values progressively strengthen the effect.
 * The model remains UI-independent so later adjustment phases can reuse it.
 */
data class AdjustmentState(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val exposure: Float = 0f,
    val temperature: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f
)
