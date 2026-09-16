package com.allay.photoeditor

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.allay.photoeditor.editor.PhotoEditorView
import com.allay.photoeditor.model.EditorElement
import com.allay.photoeditor.model.TextElement
import com.allay.photoeditor.utils.ColorPickerDialog


private fun TextElement.TextFont.displayName(): String =
    when (this) {
        TextElement.TextFont.DEFAULT -> "Default"
        TextElement.TextFont.SANS_SERIF -> "Sans Serif"
        TextElement.TextFont.SERIF -> "Serif"
        TextElement.TextFont.MONOSPACE -> "Monospace"
        TextElement.TextFont.SANS_LIGHT -> "Sans Serif Light"
        TextElement.TextFont.SANS_CONDENSED -> "Sans Serif Condensed"
    }

class MainActivity : AppCompatActivity() {

    companion object {

        private const val TAG =
            "PhotoEditor"
    }

    // -------------------------------------------------------------------------
    // VIEWS
    // -------------------------------------------------------------------------

    private lateinit var photoEditorView:
            PhotoEditorView

    private lateinit var btnSelectImage:
            Button

    private lateinit var btnAddText:
            Button

    private lateinit var btnDelete:
            Button

    private lateinit var btnCrop:
            Button

    private lateinit var btnCropRotate: Button

    private lateinit var cropToolsScroll: View
    private lateinit var btnCropFree: Button
    private lateinit var btnCropOneToOne: Button
    private lateinit var btnCropFourToThree: Button
    private lateinit var btnCropSixteenToNine: Button
    private lateinit var btnCropOriginal: Button
    private lateinit var btnCropReset: Button
    private lateinit var btnCropApply: Button

    private lateinit var btnTextColor: Button

    private lateinit var btnTextSize: Button

    private lateinit var btnTextEdit: Button

    private lateinit var btnTextBold: Button

    private lateinit var btnTextItalic: Button

    private lateinit var btnTextAlignment: Button

    private lateinit var btnTextBackground: Button

    private lateinit var btnTextBackgroundColor: Button

    private lateinit var btnTextFont: Button

    private lateinit var textToolsScroll: View

    private lateinit var btnTextMultiColor: Button

    // -------------------------------------------------------------------------
    // IMAGE PICKER
    // -------------------------------------------------------------------------

    private val imagePicker =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->

            if (uri == null) {

                Log.d(
                    TAG,
                    "No image selected"
                )

                return@registerForActivityResult
            }

            Log.d(
                TAG,
                "Selected image URI: $uri"
            )

            loadImage(uri)
        }

    // -------------------------------------------------------------------------
    // ON CREATE
    // -------------------------------------------------------------------------

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        enableEdgeToEdge()

        setContentView(
            R.layout.activity_main
        )

        setupWindowInsets()

        // ---------------------------------------------------------------------
        // FIND VIEWS
        // ---------------------------------------------------------------------

        photoEditorView =
            findViewById(
                R.id.photoEditorView
            )

        btnSelectImage =
            findViewById(
                R.id.btnSelectImage
            )

        btnCrop =
            findViewById(
                R.id.btnCrop
            )

        cropToolsScroll = findViewById(R.id.cropToolsScroll)
        btnCropFree = findViewById(R.id.btnCropFree)
        btnCropOneToOne = findViewById(R.id.btnCropOneToOne)
        btnCropFourToThree = findViewById(R.id.btnCropFourToThree)
        btnCropSixteenToNine = findViewById(R.id.btnCropSixteenToNine)
        btnCropOriginal = findViewById(R.id.btnCropOriginal)
        btnCropReset = findViewById(R.id.btnCropReset)
        btnCropApply = findViewById(R.id.btnCropApply)
        btnCropRotate = findViewById(R.id.btnCropRotate)

        btnAddText =
            findViewById(
                R.id.btnAddText
            )

        btnDelete =
            findViewById(
                R.id.btnDelete
            )

        btnTextColor=findViewById(R.id.btnTextColor)

        btnTextSize = findViewById(R.id.btnTextSize)

        btnTextEdit = findViewById(R.id.btnTextEdit)

        btnTextBold = findViewById(R.id.btnTextBold)
        btnTextItalic = findViewById(R.id.btnTextItalic)
        btnTextAlignment = findViewById(R.id.btnTextAlignment)

        btnTextBackground = findViewById(R.id.btnTextBackground)

        btnTextBackgroundColor =
            findViewById(R.id.btnTextBackgroundColor)

        textToolsScroll =
            findViewById(R.id.textToolsScroll)

        btnTextFont =
            findViewById(R.id.btnTextFont)

        btnTextColor = findViewById(R.id.btnTextColor)

        btnTextMultiColor=findViewById(R.id.btnTextMultiColor)

        // ---------------------------------------------------------------------
        // SETUP
        // ---------------------------------------------------------------------

        setupListeners()

        updateCropTools(false)
        updateDeleteButton()

        updateTextEditButton(
            photoEditorView.getSelectedElement()
        )

        updateTextColorButton(
            photoEditorView.getSelectedElement()
        )

        updateTextSizeButton(
            photoEditorView.getSelectedElement()
        )

        updateTextBoldButton(
            photoEditorView.getSelectedElement()
        )

        updateTextItalicButton(
            photoEditorView.getSelectedElement()
        )

        updateTextAlignmentButton(
            photoEditorView.getSelectedElement()
        )

        updateTextBackgroundButton(
            photoEditorView.getSelectedElement()
        )

        updateTextBackgroundColorButton(
            photoEditorView.getSelectedElement()
        )

        updateTextFontButton(
            photoEditorView.getSelectedElement()
        )

        updateTextMultiColorButton(
            photoEditorView.getSelectedElement()
        )
    }

    // -------------------------------------------------------------------------
    // WINDOW INSETS
    // -------------------------------------------------------------------------

    private fun setupWindowInsets() {

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(
                R.id.main
            )
        ) { view, insets ->

            val systemBars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }
    }

    // -------------------------------------------------------------------------
    // LISTENERS
    // -------------------------------------------------------------------------

    private fun setupListeners() {

        // ---------------------------------------------------------------------
        // SELECT IMAGE
        // ---------------------------------------------------------------------

        btnSelectImage.setOnClickListener {

            openGallery()
        }

        // ---------------------------------------------------------------------
        // CROP
        // ---------------------------------------------------------------------

        btnCrop.setOnClickListener {

            Log.d(
                TAG,
                "Crop button clicked"
            )

            if (photoEditorView.isCropMode()) {
                Log.d(
                    TAG,
                    "Crop button used to cancel active crop"
                )
                photoEditorView.cancelCrop()
                return@setOnClickListener
            }

            if (
                photoEditorView.getCurrentBitmap() == null
            ) {

                Toast.makeText(
                    this,
                    "Please select an image first",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            photoEditorView.enterCropMode()
        }

        // ---------------------------------------------------------------------
        // CROP ASPECT RATIO TOOLS
        // ---------------------------------------------------------------------

        btnCropFree.setOnClickListener {
            Log.d(TAG, "Free crop selected")
            photoEditorView.setFreeCropMode()
            updateCropButtons()
        }

        btnCropOneToOne.setOnClickListener {
            Log.d(TAG, "1:1 crop selected")
            photoEditorView.setOneToOneCropMode()
            updateCropButtons()
        }

        btnCropFourToThree.setOnClickListener {
            Log.d(TAG, "4:3 crop selected")
            photoEditorView.setFourToThreeCropMode()
            updateCropButtons()
        }

        btnCropSixteenToNine.setOnClickListener {
            Log.d(TAG, "16:9 crop selected")
            photoEditorView.setSixteenToNineCropMode()
            updateCropButtons()
        }

        btnCropOriginal.setOnClickListener {
            Log.d(TAG, "Original ratio crop selected")
            photoEditorView.setOriginalRatioCropMode()
            updateCropButtons()
        }

        btnCropReset.setOnClickListener {
            Log.d(TAG, "Reset crop clicked")
            photoEditorView.resetCrop()
            updateCropButtons()
        }

        btnCropApply.setOnClickListener {

            Log.d(
                TAG,
                "Apply crop clicked"
            )

            photoEditorView.applyCrop()
        }

        // ---------------------------------------------------------------------
        // ADD TEXT
        // ---------------------------------------------------------------------

        btnAddText.setOnClickListener {

            showAddTextDialog()
        }

        // ---------------------------------------------------------------------
        // DELETE SELECTED ELEMENT
        // ---------------------------------------------------------------------

        btnDelete.setOnClickListener {

            Log.d(
                TAG,
                "Delete button clicked"
            )

            photoEditorView
                .deleteSelectedElement()

            updateDeleteButton()
        }

        btnTextEdit.setOnClickListener {

            Log.d(
                TAG,
                "Text edit button clicked"
            )

            val selectedElement =
                photoEditorView.getSelectedElement()

            if (selectedElement !is TextElement) {

                Toast.makeText(
                    this,
                    "Please select a text element first",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            showEditTextDialog(
                selectedElement
            )
        }

        btnCropRotate.setOnClickListener {
            Log.d(TAG, "Rotate 90 degrees clockwise clicked")
            photoEditorView.rotateCrop90Degrees()
            updateCropButtons()
        }

        btnTextSize.setOnClickListener {

            Log.d(
                TAG,
                "Text size button clicked"
            )

            showTextSizeDialog()
        }

        btnTextBold.setOnClickListener {

            val selectedElement =
                photoEditorView.getSelectedElement()

            if (selectedElement !is TextElement) {

                Toast.makeText(
                    this,
                    "Please select a text element first",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val newBoldState = !selectedElement.bold

            Log.d(
                TAG,
                "Text bold button clicked. New state: $newBoldState"
            )

            photoEditorView.updateTextElementBold(
                selectedElement,
                newBoldState
            )

            updateTextBoldButton(selectedElement)
        }

        btnTextItalic.setOnClickListener {

            val selectedElement =
                photoEditorView.getSelectedElement()

            if (selectedElement !is TextElement) {

                Toast.makeText(
                    this,
                    "Please select a text element first",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val newItalicState = !selectedElement.italic

            Log.d(
                TAG,
                "Text italic button clicked. New state: $newItalicState"
            )

            photoEditorView.updateTextElementItalic(
                selectedElement,
                newItalicState
            )

            updateTextItalicButton(selectedElement)
        }

        btnTextAlignment.setOnClickListener {

            val selectedElement =
                photoEditorView.getSelectedElement()

            if (selectedElement !is TextElement) {

                Toast.makeText(
                    this,
                    "Please select a text element first",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val nextAlignment = when (selectedElement.alignment) {

                TextElement.TextAlignment.LEFT ->
                    TextElement.TextAlignment.CENTER

                TextElement.TextAlignment.CENTER ->
                    TextElement.TextAlignment.RIGHT

                TextElement.TextAlignment.RIGHT ->
                    TextElement.TextAlignment.LEFT
            }

            Log.d(
                TAG,
                "Text alignment changed: " +
                        "${selectedElement.alignment} -> $nextAlignment"
            )

            photoEditorView.updateTextElementAlignment(
                selectedElement,
                nextAlignment
            )

            updateTextAlignmentButton(selectedElement)
        }

        // ---------------------------------------------------------------------
        // TEXT BACKGROUND
        // ---------------------------------------------------------------------

        btnTextBackground.setOnClickListener {

            val selectedElement =
                photoEditorView.getSelectedElement()

            if (selectedElement !is TextElement) {

                Toast.makeText(
                    this,
                    "Please select a text element first",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val newBackgroundState =
                !selectedElement.backgroundEnabled

            Log.d(
                TAG,
                "Text background button clicked. " +
                        "New state: $newBackgroundState"
            )

            photoEditorView.updateTextElementBackground(
                selectedElement,
                newBackgroundState
            )

            updateTextBackgroundButton(selectedElement)
            updateTextBackgroundColorButton(selectedElement)
        }

        btnTextBackgroundColor.setOnClickListener {

            Log.d(
                TAG,
                "Text background color button clicked"
            )

            showTextBackgroundColorPicker()
        }

        btnTextFont.setOnClickListener {

            val selectedElement =
                photoEditorView.getSelectedElement()

            if (selectedElement !is TextElement) {

                Toast.makeText(
                    this,
                    "Please select a text element first",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            Log.d(
                TAG,
                "Text font button clicked"
            )

            showTextFontDialog(
                selectedElement
            )
        }

        // ---------------------------------------------------------------------
        // SELECTION CHANGED
        // ---------------------------------------------------------------------
        //
        // PhotoEditorView will call this whenever:
        //
        // - User selects text
        // - User selects another element
        // - User taps empty canvas
        // - Element is deleted
        //
        // This keeps the Delete button synchronized with the editor.
        // ---------------------------------------------------------------------

        photoEditorView.onSelectionChanged = { element ->

            Log.d(
                TAG,
                "Selection changed: $element"
            )

            updateDeleteButton()
            updateTextEditButton(element)
            updateTextColorButton(element)
            updateTextSizeButton(element)
            updateTextBoldButton(element)
            updateTextItalicButton(element)
            updateTextAlignmentButton(element)
            updateTextBackgroundButton(element)
            updateTextBackgroundColorButton(element)
            updateTextFontButton(element)
            updateTextMultiColorButton(element)
        }

        // ---------------------------------------------------------------------
// EDIT TEXT REQUESTED
// ---------------------------------------------------------------------

        photoEditorView.onEditTextRequested = { textElement ->

            Log.d(
                TAG,
                "Edit text requested: ${textElement.text}"
            )

            showEditTextDialog(textElement)
        }

        // ---------------------------------------------------------------------
        // CROP MODE CHANGED
        // ---------------------------------------------------------------------

        photoEditorView.onCropModeChanged = { isCropMode ->

            Log.d(
                TAG,
                "Crop mode changed: $isCropMode"
            )

            btnCrop.text =
                if (isCropMode) {
                    "Cancel Crop"
                } else {
                    "Crop"
                }

            updateCropTools(isCropMode)

            if (isCropMode) {
                updateCropButtons()
            }
        }



        btnTextColor.setOnClickListener {

            Log.d(
                TAG,
                "Text color button clicked"
            )

            showTextColorPicker()
        }

        btnTextMultiColor.setOnClickListener {

            Log.d(
                TAG,
                "Text multi-color button clicked"
            )

            val selectedElement =
                photoEditorView.getSelectedElement()

            if (selectedElement !is TextElement) {

                Toast.makeText(
                    this,
                    "Please select a text element first",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            showTextMultiColorDialog(
                selectedElement
            )
        }
    }

    // -------------------------------------------------------------------------
    // GALLERY
    // -------------------------------------------------------------------------

    private fun openGallery() {

        Log.d(
            TAG,
            "Opening gallery"
        )

        imagePicker.launch(
            "image/*"
        )
    }

    // -------------------------------------------------------------------------
    // LOAD IMAGE
    // -------------------------------------------------------------------------

    private fun loadImage(
        uri: Uri
    ) {

        try {

            contentResolver
                .openInputStream(uri)
                ?.use { inputStream ->

                    val bitmap =
                        BitmapFactory
                            .decodeStream(
                                inputStream
                            )

                    if (
                        bitmap != null
                    ) {

                        Log.d(
                            TAG,
                            "Image loaded: " +
                                    "${bitmap.width} x " +
                                    "${bitmap.height}"
                        )

                        photoEditorView
                            .setImage(
                                bitmap
                            )

                        btnSelectImage.text =
                            resources.getString(R.string.change_image)

                        updateDeleteButton()

                    } else {

                        Log.e(
                            TAG,
                            "Failed to decode image"
                        )

                        Toast.makeText(
                            this,
                            "Unable to load image",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "Error loading image",
                exception
            )

            Toast.makeText(
                this,
                "Error loading image",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // -------------------------------------------------------------------------
    // ADD TEXT DIALOG
    // -------------------------------------------------------------------------

    private fun showAddTextDialog() {

        // ---------------------------------------------------------------------
        // CHECK IMAGE
        // ---------------------------------------------------------------------

        if (
            photoEditorView
                .getCurrentBitmap() == null
        ) {

            Toast.makeText(
                this,
                "Please select an image first",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // ---------------------------------------------------------------------
        // TEXT INPUT
        // ---------------------------------------------------------------------

        val editText =
            EditText(this).apply {

                hint =
                    "Enter text"

                setSingleLine(
                    false
                )

                minLines =
                    1

                maxLines =
                    4

                setPadding(
                    40,
                    20,
                    40,
                    20
                )
            }

        // ---------------------------------------------------------------------
        // DIALOG
        // ---------------------------------------------------------------------

        val dialog =
            AlertDialog.Builder(
                this
            )
                .setTitle(
                    "Add Text"
                )
                .setView(
                    editText
                )
                .setNegativeButton(
                    "Cancel",
                    null
                )
                .setPositiveButton(
                    "Add",
                    null
                )
                .create()

        // ---------------------------------------------------------------------
        // SHOW DIALOG
        // ---------------------------------------------------------------------

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val text =
                    editText.text
                        .toString()
                        .trim()

                // -------------------------------------------------------------
                // VALIDATE
                // -------------------------------------------------------------

                if (
                    text.isEmpty()
                ) {

                    editText.error =
                        "Please enter text"

                    return@setOnClickListener
                }

                // -------------------------------------------------------------
                // ADD TEXT
                // -------------------------------------------------------------

                addTextElement(
                    text
                )

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    // -------------------------------------------------------------------------
    // CREATE TEXT ELEMENT
    // -------------------------------------------------------------------------

    private fun addTextElement(
        text: String
    ) {

        val imageWidth =
            photoEditorView
                .getImageWidth()
                .toFloat()

        val imageHeight =
            photoEditorView
                .getImageHeight()
                .toFloat()

        // ---------------------------------------------------------------------
        // START TEXT IN CENTER
        // ---------------------------------------------------------------------

        val position =
            PointF(
                imageWidth / 2f,
                imageHeight / 2f
            )

        // ---------------------------------------------------------------------
        // CREATE TEXT
        // ---------------------------------------------------------------------

        val textElement =
            TextElement(
                text = text,
                position = position,
                textSize = 80f,
                color = Color.WHITE
            )

        // ---------------------------------------------------------------------
        // ADD TO EDITOR
        // ---------------------------------------------------------------------

        photoEditorView.addElement(
            textElement
        )

        /*
         * addElement() automatically selects the new element.
         *
         * onSelectionChanged will therefore update the Delete button.
         */

        updateDeleteButton()
    }

    // -------------------------------------------------------------------------
    // DELETE BUTTON
    // -------------------------------------------------------------------------

    private fun updateDeleteButton() {

        val elements =
            photoEditorView
                .getElements()

        val selected =
            elements.any {
                it.isSelected
            }

        btnDelete.visibility =
            if (selected) {
                View.VISIBLE
            } else {
                View.GONE
            }

        Log.d(
            TAG,
            "Delete button visibility: " +
                    if (selected) {
                        "VISIBLE"
                    } else {
                        "GONE"
                    }
        )
    }

    private fun showEditTextDialog(
        textElement: TextElement
    ) {

        Log.d(
            TAG,
            "showEditTextDialog() called"
        )

        val editText = EditText(this).apply {

            setText(textElement.text)

            setSelection(text.length)

            hint = "Enter text"

            setSingleLine(false)

            minLines = 1

            maxLines = 4

            setPadding(
                40,
                20,
                40,
                20
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Text")
            .setView(editText)
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Update",
                null
            )
            .create()

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val newText = editText.text
                    .toString()
                    .trim()

                if (newText.isEmpty()) {

                    editText.error =
                        "Please enter text"

                    return@setOnClickListener
                }

                Log.d(
                    TAG,
                    "Updating text: $newText"
                )

                photoEditorView.updateTextElement(
                    textElement,
                    newText
                )

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showTextColorPicker() {

        val selectedElement =
            photoEditorView.getSelectedElement()

        if (selectedElement !is TextElement) {

            Toast.makeText(
                this,
                "Please select a text element first",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        Log.d(
            TAG,
            "Opening color picker for: ${selectedElement.text}"
        )

        ColorPickerDialog(
            context = this,
            initialColor = selectedElement.color
        ) { selectedColor ->

            Log.d(
                TAG,
                "Selected color: $selectedColor"
            )

            photoEditorView.updateTextElementColor(
                selectedElement,
                selectedColor
            )
        }.show()
    }

    private fun showTextBackgroundColorPicker() {

        val selectedElement =
            photoEditorView.getSelectedElement()

        if (selectedElement !is TextElement) {

            Toast.makeText(
                this,
                "Please select a text element first",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (!selectedElement.backgroundEnabled) {

            Toast.makeText(
                this,
                "Please enable text background first",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        Log.d(
            TAG,
            "Opening background color picker for: " +
                    selectedElement.text
        )

        ColorPickerDialog(
            context = this,
            initialColor = selectedElement.backgroundColor
        ) { selectedColor ->

            Log.d(
                TAG,
                "Selected background color: $selectedColor"
            )

            photoEditorView.updateTextElementBackgroundColor(
                selectedElement,
                selectedColor
            )
        }.show()
    }

    private fun showTextSizeDialog() {

        val selectedElement =
            photoEditorView.getSelectedElement()

        if (selectedElement !is TextElement) {

            Toast.makeText(
                this,
                "Please select a text element first",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        Log.d(
            TAG,
            "Opening text size dialog for: ${selectedElement.text}"
        )

        val minSize = 20
        val maxSize = 200

        val originalSize =
            selectedElement.textSize

        val currentSize =
            originalSize.coerceIn(
                minSize.toFloat(),
                maxSize.toFloat()
            )

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                40,
                20,
                40,
                20
            )
        }

        val sizeLabel = TextView(this).apply {
            text = "Size: ${currentSize.toInt()}"
            textSize = 16f
        }

        val seekBar = SeekBar(this).apply {
            max = maxSize - minSize

            progress =
                currentSize.toInt() - minSize
        }

        container.addView(
            sizeLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        container.addView(
            seekBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val dialog =
            AlertDialog.Builder(this)
                .setTitle("Text Size")
                .setView(container)
                .setNegativeButton(
                    "Cancel",
                    null
                )
                .setPositiveButton(
                    "Done",
                    null
                )
                .create()

        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {

                    val newSize =
                        (minSize + progress).toFloat()

                    sizeLabel.text =
                        "Size: ${newSize.toInt()}"

                    photoEditorView.updateTextElementSize(
                        selectedElement,
                        newSize
                    )
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) {
                }

                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) {
                }
            }
        )

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_NEGATIVE
            ).setOnClickListener {

                Log.d(
                    TAG,
                    "Text size cancelled. Restoring: $originalSize"
                )

                photoEditorView.updateTextElementSize(
                    selectedElement,
                    originalSize
                )

                dialog.dismiss()
            }

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                Log.d(
                    TAG,
                    "Text size applied: ${selectedElement.textSize}"
                )

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun updateTextColorButton(
        element: EditorElement?
    ) {

        btnTextColor.visibility =
            if (element is TextElement) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }



    private fun updateTextSizeButton(
        element: EditorElement?
    ) {

        btnTextSize.visibility =
            if (element is TextElement) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun updateTextBoldButton(
        element: EditorElement?
    ) {

        if (element is TextElement) {

            btnTextBold.visibility = View.VISIBLE

            btnTextBold.text =
                if (element.bold) {
                    "Bold: On"
                } else {
                    "Bold: Off"
                }

        } else {

            btnTextBold.visibility = View.GONE
        }
    }

    private fun updateTextItalicButton(
        element: EditorElement?
    ) {

        if (element is TextElement) {

            btnTextItalic.visibility = View.VISIBLE

            btnTextItalic.text =
                if (element.italic) {
                    "Italic: On"
                } else {
                    "Italic: Off"
                }

        } else {

            btnTextItalic.visibility = View.GONE
        }
    }

    private fun updateTextBackgroundButton(
        element: EditorElement?
    ) {

        if (element is TextElement) {

            textToolsScroll.visibility = View.VISIBLE
            btnTextBackground.visibility = View.VISIBLE

            btnTextBackground.text =
                if (element.backgroundEnabled) {
                    "Background: On"
                } else {
                    "Background: Off"
                }

        } else {

            btnTextBackground.visibility = View.GONE
        }
    }

    private fun updateTextBackgroundColorButton(
        element: EditorElement?
    ) {

        if (element is TextElement && element.backgroundEnabled) {

            textToolsScroll.visibility = View.VISIBLE
            btnTextBackgroundColor.visibility = View.VISIBLE

        } else {

            btnTextBackgroundColor.visibility = View.GONE
        }
    }

    private fun updateTextAlignmentButton(
        element: EditorElement?
    ) {

        if (element is TextElement) {

            textToolsScroll.visibility = View.VISIBLE
            btnTextAlignment.visibility = View.VISIBLE

            btnTextAlignment.text =
                when (element.alignment) {

                    TextElement.TextAlignment.LEFT ->
                        "Align: Left"

                    TextElement.TextAlignment.CENTER ->
                        "Align: Center"

                    TextElement.TextAlignment.RIGHT ->
                        "Align: Right"
                }

        } else {

            textToolsScroll.visibility = View.GONE
            btnTextAlignment.visibility = View.GONE
        }
    }

    private fun showTextFontDialog(
        textElement: TextElement
    ) {

        val fonts = TextElement.TextFont.values()

        val fontNames = fonts.map {
            it.displayName()
        }.toTypedArray()

        val selectedIndex =
            fonts.indexOf(textElement.font).coerceAtLeast(0)

        Log.d(
            TAG,
            "Opening font selector. Current font: " +
                    textElement.getFontDisplayName()
        )

        AlertDialog.Builder(this)
            .setTitle("Select Font")
            .setSingleChoiceItems(
                fontNames,
                selectedIndex
            ) { dialog, which ->

                val selectedFont = fonts[which]

                Log.d(
                    TAG,
                    "Font selected: ${selectedFont.displayName()}"
                )

                photoEditorView.updateTextElementFont(
                    textElement,
                    selectedFont
                )

                updateTextFontButton(textElement)

                dialog.dismiss()
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    private fun updateTextFontButton(
        element: EditorElement?
    ) {

        if (element is TextElement) {

            textToolsScroll.visibility = View.VISIBLE
            btnTextFont.visibility = View.VISIBLE

            btnTextFont.text =
                "Font: ${element.getFontDisplayName()}"

        } else {

            btnTextFont.visibility = View.GONE
        }
    }

    private fun showTextMultiColorDialog(
        textElement: TextElement
    ) {

        Log.d(
            TAG,
            "Opening multi-color dialog for: ${textElement.text}"
        )

        val editText =
            EditText(this).apply {

                setText(textElement.text)

                setSingleLine(false)

                minLines = 2

                maxLines = 5

                hint =
                    "Select text and choose a color"

                setPadding(
                    40,
                    20,
                    40,
                    20
                )

                /*
                 * This dialog is only for selecting
                 * text ranges.
                 *
                 * The actual text remains controlled
                 * by TextElement.
                 */
                isFocusable = true
                isFocusableInTouchMode = true
                isCursorVisible = true
            }

        val container =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    30,
                    10,
                    30,
                    10
                )

                addView(
                    editText,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }

        val dialog =
            AlertDialog.Builder(this)
                .setTitle("Multi Color Text")
                .setView(container)
                .setNegativeButton(
                    "Cancel",
                    null
                )
                .setPositiveButton(
                    "Done",
                    null
                )
                .create()

        dialog.setOnShowListener {

            val doneButton =
                dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
                )

            doneButton.setOnClickListener {

                Log.d(
                    TAG,
                    "Multi-color dialog completed"
                )

                dialog.dismiss()
            }

            /*
             * We add a separate color action
             * dynamically to the dialog.
             */
            val chooseColorButton =
                dialog.getButton(
                    AlertDialog.BUTTON_NEGATIVE
                )

            chooseColorButton.text =
                "Choose Color"

            chooseColorButton.setOnClickListener {

                val start =
                    editText.selectionStart

                val end =
                    editText.selectionEnd

                Log.d(
                    TAG,
                    "Selected text range: $start -> $end"
                )

                if (
                    start < 0 ||
                    end < 0 ||
                    start == end
                ) {

                    Toast.makeText(
                        this,
                        "Please select some text first",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                val selectionStart =
                    minOf(start, end)

                val selectionEnd =
                    maxOf(start, end)

                val currentColor =
                    textElement.getColorAt(
                        selectionStart
                    )

                Log.d(
                    TAG,
                    "Opening color picker. " +
                            "range=$selectionStart..$selectionEnd " +
                            "currentColor=$currentColor"
                )

                ColorPickerDialog(
                    context = this,
                    initialColor = currentColor
                ) { selectedColor ->

                    Log.d(
                        TAG,
                        "Applying color $selectedColor " +
                                "to range " +
                                "$selectionStart..$selectionEnd"
                    )

                    photoEditorView
                        .updateTextElementColorRange(
                            textElement = textElement,
                            start = selectionStart,
                            end = selectionEnd,
                            newColor = selectedColor
                        )

                    /*
                     * Restore the selection because the
                     * color picker temporarily takes focus.
                     */
                    editText.requestFocus()

                    editText.setSelection(
                        selectionStart,
                        selectionEnd
                    )
                }.show()
            }
        }

        dialog.show()

        /*
         * Select the complete text initially.
         *
         * This makes the first color operation easy:
         * open Multi Color -> Choose Color.
         */
        editText.requestFocus()

        editText.setSelection(
            0,
            editText.text.length
        )
    }

    private fun updateTextMultiColorButton(
        element: EditorElement?
    ) {
        if (element is TextElement) {
            textToolsScroll.visibility = View.VISIBLE
            btnTextMultiColor.visibility = View.VISIBLE
            btnTextMultiColor.text = "Multi Color"
        } else {
            btnTextMultiColor.visibility = View.GONE
        }
    }

    private fun updateTextEditButton(
        element: EditorElement?
    ) {
        if (element is TextElement) {
            textToolsScroll.visibility = View.VISIBLE
            btnTextEdit.visibility = View.VISIBLE
            btnTextEdit.text = "Edit"
        } else {
            btnTextEdit.visibility = View.GONE
        }
    }

    // -------------------------------------------------------------------------
    // CROP TOOLS UI
    // -------------------------------------------------------------------------

    private fun updateCropTools(isCropMode: Boolean) {

        cropToolsScroll.visibility =
            if (isCropMode) {
                View.VISIBLE
            } else {
                View.GONE
            }

        Log.d(
            TAG,
            "Crop tools visibility: " +
                    if (isCropMode) "VISIBLE" else "GONE"
        )
    }

    private fun updateCropButtons() {

        if (!photoEditorView.isCropMode()) {
            return
        }

        btnCropFree.text =
            if (photoEditorView.isFreeCropMode()) {
                "Free: On"
            } else {
                "Free"
            }

        btnCropOneToOne.text =
            if (photoEditorView.isOneToOneCropMode()) {
                "1:1: On"
            } else {
                "1:1"
            }

        btnCropFourToThree.text =
            if (photoEditorView.isFourToThreeCropMode()) {
                "4:3: On"
            } else {
                "4:3"
            }

        btnCropSixteenToNine.text =
            if (photoEditorView.isSixteenToNineCropMode()) {
                "16:9: On"
            } else {
                "16:9"
            }

        btnCropOriginal.text =
            if (photoEditorView.isOriginalRatioCropMode()) {
                "Original: On"
            } else {
                "Original"
            }
    }

}
