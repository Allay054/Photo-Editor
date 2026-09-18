package com.allay.photoeditor.editor.history

/**
 * Centralized Undo / Redo history controller.
 *
 * Phase 11.1 - History Foundation
 *
 * Responsibilities:
 * - Maintain Undo history
 * - Maintain Redo history
 * - Record editor state changes
 * - Clear Redo history when a new operation is recorded
 * - Limit history size
 * - Provide safe Undo / Redo state retrieval
 *
 * This class does NOT know anything about:
 * - PhotoEditorView
 * - Text
 * - Shapes
 * - Annotations
 * - Crop
 * - Filters
 * - Adjustments
 *
 * It only manages snapshots.
 */
class EditorHistoryController(
    private val maxHistorySize: Int = DEFAULT_MAX_HISTORY_SIZE
) {

    companion object {

        /**
         * Maximum number of undo operations retained by default.
         *
         * Keeping this bounded prevents unlimited memory growth,
         * especially when bitmap snapshots are introduced.
         */
        const val DEFAULT_MAX_HISTORY_SIZE = 50
    }

    // =========================================================================
    // HISTORY STACKS
    // =========================================================================

    /**
     * Older states available for Undo.
     *
     * The last item is always the most recent Undo state.
     */
    private val undoStack =
        ArrayDeque<EditorStateSnapshot>()

    /**
     * States available for Redo.
     *
     * The last item is always the most recent Redo state.
     */
    private val redoStack =
        ArrayDeque<EditorStateSnapshot>()

    // =========================================================================
    // STATE
    // =========================================================================

    /**
     * Returns true when at least one Undo operation is available.
     */
    fun canUndo(): Boolean {
        return undoStack.isNotEmpty()
    }

    /**
     * Returns true when at least one Redo operation is available.
     */
    fun canRedo(): Boolean {
        return redoStack.isNotEmpty()
    }

    /**
     * Returns the current number of Undo states.
     */
    fun undoCount(): Int {
        return undoStack.size
    }

    /**
     * Returns the current number of Redo states.
     */
    fun redoCount(): Int {
        return redoStack.size
    }

    // =========================================================================
    // RECORD
    // =========================================================================

    /**
     * Records a new editor operation.
     *
     * The supplied [before] state becomes available through Undo.
     *
     * The supplied [after] state becomes available through Redo after
     * the Undo operation is performed.
     *
     * Recording a new operation always clears the existing Redo stack.
     */
    fun record(
        before: EditorStateSnapshot,
        after: EditorStateSnapshot
    ) {

        /*
         * Do not create a history entry when there is no actual state
         * change.
         *
         * Reference equality is intentionally not used here because
         * snapshots may contain independently copied objects.
         *
         * The caller is responsible for deciding whether an operation
         * actually changed the editor state.
         */

        undoStack.addLast(
            before
        )

        /*
         * A new operation after Undo creates a new branch.
         *
         * Therefore all previously available Redo states must be removed.
         */
        clearRedo()

        /*
         * Prevent unlimited history growth.
         */
        trimUndoStack()
    }

    // =========================================================================
    // UNDO
    // =========================================================================

    /**
     * Performs one Undo step.
     *
     * Returns the state that should be restored.
     *
     * The current state must be supplied by the caller.
     *
     * Example:
     *
     * currentState = current editor state
     *
     * undo() returns the previous state and stores currentState
     * in the Redo stack.
     */
    fun undo(
        currentState: EditorStateSnapshot
    ): EditorStateSnapshot? {

        if (undoStack.isEmpty()) {
            return null
        }

        /*
         * Save the current state so Redo can restore it later.
         */
        redoStack.addLast(
            currentState
        )

        /*
         * The most recent previous state becomes the state to restore.
         */
        return undoStack.removeLast()
    }

    // =========================================================================
    // REDO
    // =========================================================================

    /**
     * Performs one Redo step.
     *
     * Returns the state that should be restored.
     *
     * The current state is moved back into the Undo stack.
     */
    fun redo(
        currentState: EditorStateSnapshot
    ): EditorStateSnapshot? {

        if (redoStack.isEmpty()) {
            return null
        }

        /*
         * Save the current state so another Undo can return to it.
         */
        undoStack.addLast(
            currentState
        )

        trimUndoStack()

        /*
         * Restore the most recently undone state.
         */
        return redoStack.removeLast()
    }

    // =========================================================================
    // CLEAR
    // =========================================================================

    /**
     * Clears both Undo and Redo history.
     *
     * Intended for:
     * - New image
     * - Export completion
     * - Complete editor reset
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    /**
     * Clears only the Redo history.
     *
     * This is automatically called when a new operation is recorded.
     */
    fun clearRedo() {
        redoStack.clear()
    }

    /**
     * Clears only the Undo history.
     */
    fun clearUndo() {
        undoStack.clear()
    }

    // =========================================================================
    // INTERNAL HISTORY LIMIT
    // =========================================================================

    /**
     * Ensures the Undo stack never exceeds [maxHistorySize].
     *
     * The oldest states are removed first.
     */
    private fun trimUndoStack() {

        while (
            undoStack.size >
            maxHistorySize
        ) {

            undoStack.removeFirst()
        }
    }
}