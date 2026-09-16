package com.allay.photoeditor.model

/**
 * Supported shape types for the Photo Editor.
 *
 * Rendering and interaction behavior for these shapes
 * will be implemented incrementally during Phase 8.
 */
enum class ShapeType {

    /**
     * Standard four-sided rectangle.
     */
    RECTANGLE,

    /**
     * Circle / ellipse.
     */
    CIRCLE,

    /**
     * Rectangle with rounded corners.
     */
    ROUNDED_RECTANGLE,

    /**
     * Three-sided triangle.
     */
    TRIANGLE,

    /**
     * Simple line.
     */
    LINE,

    /**
     * Line with an arrow head.
     */
    ARROW,

    /**
     * Pointer shape.
     */
    POINTER
}