package com.allay.photoeditor.model

/**
 * Defines the available annotation tools.
 *
 * Rendering and interaction for these tools will be
 * implemented incrementally in later Phase 9 steps.
 */
enum class AnnotationType {

    /**
     * Freehand drawing using a continuous path.
     */
    FREEHAND,

    /**
     * Pen-style annotation.
     */
    PEN,

    /**
     * Semi-transparent highlighting annotation.
     */
    HIGHLIGHTER,

    /**
     * Erases annotation content.
     */
    ERASER,

    /**
     * Applies a blur effect to the annotated area.
     */
    BLUR,

    /**
     * Applies a pixelation effect to the annotated area.
     */
    PIXELATE
}