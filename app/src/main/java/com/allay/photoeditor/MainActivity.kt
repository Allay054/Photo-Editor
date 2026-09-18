package com.allay.photoeditor

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.content.ContentValues
import android.content.res.ColorStateList
import android.graphics.PointF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
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
import com.allay.photoeditor.model.FilterType
import com.allay.photoeditor.model.ShapeElement
import com.allay.photoeditor.model.ShapeType
import com.allay.photoeditor.model.TextElement
import com.allay.photoeditor.editor.shape.ShapeController
import com.allay.photoeditor.editor.annotation.AnnotationController
import com.allay.photoeditor.editor.export.ExportFormat
import com.allay.photoeditor.utils.ColorPickerDialog
import com.allay.photoeditor.utils.StrokeWidthPickerDialog


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

    private val shapeController = ShapeController()

    companion object {

        private const val TAG =
            "PhotoEditor"
    }

    // -------------------------------------------------------------------------
    // VIEWS
    // -------------------------------------------------------------------------

    private lateinit var photoEditorView:
            PhotoEditorView

    private lateinit var btnUndo:
            Button

    private lateinit var btnRedo:
            Button

    private lateinit var btnSave:
            Button

    // Number of global undo steps at the last saved state.
    private var savedUndoCount = 0

    private lateinit var btnSelectImage:
            Button

    private lateinit var btnAddText:
            Button

    private lateinit var btnAddShape:
            Button

    private lateinit var shapeToolsScroll:
            View

    private lateinit var btnShapeRectangle:
            Button

    private lateinit var btnShapeCircle:
            Button

    private lateinit var btnShapeRoundedRectangle:
            Button

    private lateinit var btnShapeTriangle:
            Button

    private lateinit var btnShapeLine:
            Button

    private lateinit var btnShapeArrow:
            Button

    private lateinit var btnShapePointer:
            Button

    private lateinit var btnShapeColor:
            Button

    private lateinit var btnShapeStrokeWidth:
            Button

    private lateinit var btnShapeFill:
            Button

    private lateinit var btnDelete:
            Button

    private lateinit var btnCrop:
            Button

    private lateinit var btnTransform:
            Button

    // -------------------------------------------------------------------------
    // ADJUSTMENT TOOLS - PHASE 7.1
    // -------------------------------------------------------------------------

    private lateinit var btnAdjust:
            Button

    private lateinit var adjustmentToolsScroll:
            View

    private lateinit var btnAdjustmentReset:
            Button

    private lateinit var btnAdjustmentCancel:
            Button

    private lateinit var btnAdjustmentApply:
            Button

    private lateinit var brightnessSeekBar:
            SeekBar

    private lateinit var contrastSeekBar:
            SeekBar

    private lateinit var saturationSeekBar:
            SeekBar

    private lateinit var exposureSeekBar:
            SeekBar

    private lateinit var temperatureSeekBar:
            SeekBar

    private lateinit var highlightsSeekBar:
            SeekBar

    private lateinit var shadowsSeekBar:
            SeekBar

    private lateinit var brightnessValueText:
            TextView

    private lateinit var contrastValueText:
            TextView

    private lateinit var saturationValueText:
            TextView

    private lateinit var exposureValueText:
            TextView

    private lateinit var temperatureValueText:
            TextView

    private lateinit var highlightsValueText:
            TextView

    private lateinit var shadowsValueText:
            TextView

    private var isUpdatingAdjustmentControls = false

    // -------------------------------------------------------------------------
    // FILTER TOOLS - PHASE 7.2
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // ANNOTATION TOOLS - PHASE 9.2
    // -------------------------------------------------------------------------

    private lateinit var btnAnnotation: Button
    private lateinit var annotationToolsScroll: View
    private lateinit var btnAnnotationFreehand: Button
    private lateinit var btnAnnotationPen: Button
    private lateinit var btnAnnotationHighlighter: Button
    private lateinit var btnAnnotationEraser: Button
    private lateinit var btnAnnotationBlur: Button
    private lateinit var btnAnnotationPixelate: Button
    private lateinit var btnAnnotationUndo: Button
    private lateinit var btnAnnotationRedo: Button
    private lateinit var btnAnnotationCancel: Button
    private lateinit var btnAnnotationColor: Button
    private lateinit var btnAnnotationStrokeSize: Button
    private lateinit var btnAnnotationApply: Button

    private lateinit var btnFilters: Button
    private lateinit var filterToolsScroll: View
    private lateinit var btnFilterOriginal: Button
    private lateinit var btnFilterGrayscale: Button
    private lateinit var btnFilterBlackWhite: Button
    private lateinit var btnFilterSepia: Button
    private lateinit var btnFilterVintage: Button
    private lateinit var btnFilterWarm: Button
    private lateinit var btnFilterCool: Button
    private lateinit var btnFilterCancel: Button
    private lateinit var btnFilterApply: Button

    private var selectedFilterType = FilterType.ORIGINAL

    private lateinit var btnFlipHorizontal:
            Button

    private lateinit var btnFlipVertical:
            Button

    private lateinit var btnRotateLeft:
            Button

    private lateinit var btnRotateRight:
            Button

    private lateinit var mainToolsScroll: View

    // -------------------------------------------------------------------------
    // LAYER TOOLS - PHASE 10.2
    // -------------------------------------------------------------------------

    private lateinit var btnLayers: Button
    private lateinit var layerPanel: View
    private lateinit var layerListContainer: LinearLayout
    private lateinit var btnLayerBringToFront: Button
    private lateinit var btnLayerBringForward: Button
    private lateinit var btnLayerSendBackward: Button
    private lateinit var btnLayerSendToBack: Button
    private lateinit var btnLayerDuplicate: Button
    private lateinit var btnLayerDelete: Button
    private lateinit var btnLayerVisibility: Button
    private lateinit var btnLayerLock: Button
    private lateinit var btnLayerClose: Button

    private lateinit var rotationToolsScroll: View
    private lateinit var btnRotationCancel: Button
    private lateinit var btnRotationApply: Button

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

            photoEditorView.exitFreehandMode()
            annotationToolsScroll.visibility = View.GONE
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

        btnUndo =
            findViewById(
                R.id.btnUndo
            )

        btnRedo =
            findViewById(
                R.id.btnRedo
            )

        btnSave =
            findViewById(
                R.id.btnSave
            )

        btnSelectImage =
            findViewById(
                R.id.btnSelectImage
            )

        btnCrop =
            findViewById(
                R.id.btnCrop
            )

        btnTransform =
            findViewById(
                R.id.btnTransform
            )

        btnAdjust =
            findViewById(
                R.id.btnAdjust
            )

        adjustmentToolsScroll =
            findViewById(
                R.id.adjustmentToolsScroll
            )

        btnAdjustmentReset =
            findViewById(
                R.id.btnAdjustmentReset
            )

        btnAdjustmentCancel =
            findViewById(
                R.id.btnAdjustmentCancel
            )

        btnAdjustmentApply =
            findViewById(
                R.id.btnAdjustmentApply
            )

        brightnessSeekBar = findViewById(R.id.seekBrightness)
        contrastSeekBar = findViewById(R.id.seekContrast)
        saturationSeekBar = findViewById(R.id.seekSaturation)
        exposureSeekBar = findViewById(R.id.seekExposure)
        temperatureSeekBar = findViewById(R.id.seekTemperature)
        highlightsSeekBar = findViewById(R.id.seekHighlights)
        shadowsSeekBar = findViewById(R.id.seekShadows)
        brightnessValueText = findViewById(R.id.txtBrightnessValue)
        contrastValueText = findViewById(R.id.txtContrastValue)
        saturationValueText = findViewById(R.id.txtSaturationValue)
        exposureValueText = findViewById(R.id.txtExposureValue)
        temperatureValueText = findViewById(R.id.txtTemperatureValue)
        highlightsValueText = findViewById(R.id.txtHighlightsValue)
        shadowsValueText = findViewById(R.id.txtShadowsValue)

        btnFilters = findViewById(R.id.btnFilters)
        filterToolsScroll = findViewById(R.id.filterToolsScroll)
        btnFilterOriginal = findViewById(R.id.btnFilterOriginal)
        btnFilterGrayscale = findViewById(R.id.btnFilterGrayscale)
        btnFilterBlackWhite = findViewById(R.id.btnFilterBlackWhite)
        btnFilterSepia = findViewById(R.id.btnFilterSepia)
        btnFilterVintage = findViewById(R.id.btnFilterVintage)
        btnFilterWarm = findViewById(R.id.btnFilterWarm)
        btnFilterCool = findViewById(R.id.btnFilterCool)
        btnFilterCancel = findViewById(R.id.btnFilterCancel)
        btnFilterApply = findViewById(R.id.btnFilterApply)

        btnAnnotation = findViewById(R.id.btnAnnotation)
        annotationToolsScroll = findViewById(R.id.annotationToolsScroll)
        btnAnnotationFreehand = findViewById(R.id.btnAnnotationFreehand)
        btnAnnotationPen = findViewById(R.id.btnAnnotationPen)
        btnAnnotationHighlighter = findViewById(R.id.btnAnnotationHighlighter)
        btnAnnotationEraser = findViewById(R.id.btnAnnotationEraser)
        btnAnnotationBlur = findViewById(R.id.btnAnnotationBlur)
        btnAnnotationPixelate = findViewById(R.id.btnAnnotationPixelate)
        btnAnnotationUndo = findViewById(R.id.btnAnnotationUndo)
        btnAnnotationRedo = findViewById(R.id.btnAnnotationRedo)
        btnAnnotationCancel = findViewById(R.id.btnAnnotationCancel)
        btnAnnotationColor = findViewById(R.id.btnAnnotationColor)
        btnAnnotationStrokeSize = findViewById(R.id.btnAnnotationStrokeSize)
        btnAnnotationApply = findViewById(R.id.btnAnnotationApply)

        btnFlipHorizontal =
            findViewById(
                R.id.btnFlipHorizontal
            )

        btnFlipVertical =
            findViewById(
                R.id.btnFlipVertical
            )

        btnRotateLeft =
            findViewById(
                R.id.btnRotateLeft
            )

        btnRotateRight =
            findViewById(
                R.id.btnRotateRight
            )

        mainToolsScroll = findViewById(R.id.mainToolsScroll)

        btnLayers = findViewById(R.id.btnLayers)
        layerPanel = findViewById(R.id.layerPanel)
        layerListContainer = findViewById(R.id.layerListContainer)
        btnLayerBringToFront = findViewById(R.id.btnLayerBringToFront)
        btnLayerBringForward = findViewById(R.id.btnLayerBringForward)
        btnLayerSendBackward = findViewById(R.id.btnLayerSendBackward)
        btnLayerSendToBack = findViewById(R.id.btnLayerSendToBack)
        btnLayerDuplicate = findViewById(R.id.btnLayerDuplicate)
        btnLayerDelete = findViewById(R.id.btnLayerDelete)
        btnLayerVisibility = findViewById(R.id.btnLayerVisibility)
        btnLayerLock = findViewById(R.id.btnLayerLock)
        btnLayerClose = findViewById(R.id.btnLayerClose)

        rotationToolsScroll = findViewById(R.id.rotationToolsScroll)
        btnRotationCancel = findViewById(R.id.btnRotationCancel)
        btnRotationApply = findViewById(R.id.btnRotationApply)

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

        btnAddShape =
            findViewById(
                R.id.btnAddShape
            )

        shapeToolsScroll =
            findViewById(
                R.id.shapeToolsScroll
            )

        btnShapeRectangle = findViewById(R.id.btnShapeRectangle)
        btnShapeCircle = findViewById(R.id.btnShapeCircle)
        btnShapeRoundedRectangle = findViewById(R.id.btnShapeRoundedRectangle)
        btnShapeTriangle = findViewById(R.id.btnShapeTriangle)
        btnShapeLine = findViewById(R.id.btnShapeLine)
        btnShapeArrow = findViewById(R.id.btnShapeArrow)
        btnShapePointer = findViewById(R.id.btnShapePointer)
        btnShapeColor = findViewById(R.id.btnShapeColor)
        btnShapeStrokeWidth = findViewById(R.id.btnShapeStrokeWidth)
        btnShapeFill = findViewById(R.id.btnShapeFill)

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

        photoEditorView.onHistoryChanged = {
            runOnUiThread {
                updateHistoryButtons()
                updateSaveButton()
            }
        }

        updateHistoryButtons()
        updateSaveButton()

        layerPanel.visibility = View.GONE
        updateLayerPanel()

        updateCropTools(false)
        adjustmentToolsScroll.visibility = View.GONE
        filterToolsScroll.visibility = View.GONE
        shapeToolsScroll.visibility = View.GONE
        annotationToolsScroll.visibility = View.GONE
        updateAnnotationHistoryButtons()
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
    // EXPORT - PHASE 12.3 FORMAT & QUALITY
    // -------------------------------------------------------------------------

    private fun showExportDialog(
        onSaved: (() -> Unit)? = null
    ) {
        if (photoEditorView.getCurrentBitmap() == null) {
            Toast.makeText(
                this,
                "Please select an image first",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
        }

        val formatTitle = TextView(this).apply {
            text = "Format"
            textSize = 16f
            setPadding(0, 8, 0, 8)
        }

        val pngRadio = RadioButton(this).apply {
            text = "PNG (lossless)"
            isChecked = true
        }

        val jpegRadio = RadioButton(this).apply {
            text = "JPEG"
        }

        val qualityLabel = TextView(this).apply {
            text = "JPEG Quality: 95"
            textSize = 14f
            setPadding(0, 12, 0, 4)
        }

        val qualitySeekBar = SeekBar(this).apply {
            // SeekBar progress remains API-21 compatible.
            // 0..90 maps to JPEG quality 10..100.
            max = 90
            progress = 85
            isEnabled = false
        }

        container.addView(formatTitle)
        container.addView(pngRadio)
        container.addView(jpegRadio)
        container.addView(qualityLabel)
        container.addView(qualitySeekBar)

        fun updateQualityEnabled() {
            val jpegSelected = jpegRadio.isChecked
            qualitySeekBar.isEnabled = jpegSelected
            qualityLabel.isEnabled = jpegSelected
        }

        pngRadio.setOnClickListener {
            pngRadio.isChecked = true
            jpegRadio.isChecked = false
            updateQualityEnabled()
        }

        jpegRadio.setOnClickListener {
            pngRadio.isChecked = false
            jpegRadio.isChecked = true
            updateQualityEnabled()
        }

        qualitySeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    qualityLabel.text =
                        "JPEG Quality: ${progress + 10}"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            }
        )

        updateQualityEnabled()

        AlertDialog.Builder(this)
            .setTitle("Export Image")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val format =
                    if (jpegRadio.isChecked) {
                        ExportFormat.JPEG
                    } else {
                        ExportFormat.PNG
                    }

                val quality =
                    (qualitySeekBar.progress + 10).coerceIn(10, 100)

                if (saveEditedImage(format, quality)) {
                    savedUndoCount = photoEditorView.undoCount()
                    updateSaveButton()
                    onSaved?.invoke()
                }
            }
            .show()
    }

    private fun saveEditedImage(
        format: ExportFormat,
        jpegQuality: Int = 95
    ): Boolean {
        if (photoEditorView.getCurrentBitmap() == null) {
            Toast.makeText(
                this,
                "Please select an image first",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        val exportedBitmap = photoEditorView.exportFinalBitmap()

        if (exportedBitmap == null) {
            Toast.makeText(
                this,
                "Unable to export image",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        val safeQuality = jpegQuality.coerceIn(10, 100)
        val isJpeg = format == ExportFormat.JPEG

        val extension = if (isJpeg) "jpg" else "png"
        val mimeType = if (isJpeg) "image/jpeg" else "image/png"
        val compressFormat =
            if (isJpeg) {
                android.graphics.Bitmap.CompressFormat.JPEG
            } else {
                android.graphics.Bitmap.CompressFormat.PNG
            }

        val fileName =
            "PhotoEditor_${System.currentTimeMillis()}.$extension"

        var saveSucceeded = false

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        fileName
                    )
                    put(
                        MediaStore.Images.Media.MIME_TYPE,
                        mimeType
                    )
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "Pictures/PhotoEditor"
                    )
                    put(
                        MediaStore.Images.Media.IS_PENDING,
                        1
                    )
                }

                val uri = contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                )

                if (uri == null) {
                    Toast.makeText(
                        this,
                        "Unable to create image file",
                        Toast.LENGTH_SHORT
                    ).show()
                    return false
                }

                try {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        if (!exportedBitmap.compress(
                                compressFormat,
                                if (isJpeg) safeQuality else 100,
                                outputStream
                            )
                        ) {
                            throw IllegalStateException(
                                "Bitmap compression failed"
                            )
                        }
                    } ?: throw IllegalStateException(
                        "Unable to open output stream"
                    )

                    val completedValues = ContentValues().apply {
                        put(
                            MediaStore.Images.Media.IS_PENDING,
                            0
                        )
                    }

                    contentResolver.update(
                        uri,
                        completedValues,
                        null,
                        null
                    )

                    Toast.makeText(
                        this,
                        "Image saved to Pictures/PhotoEditor",
                        Toast.LENGTH_SHORT
                    ).show()

                    saveSucceeded = true
                } catch (exception: Exception) {
                    contentResolver.delete(uri, null, null)
                    throw exception
                }
            } else {
                val picturesDirectory =
                    getExternalFilesDir(
                        android.os.Environment.DIRECTORY_PICTURES
                    )

                if (picturesDirectory == null) {
                    throw IllegalStateException(
                        "Pictures directory unavailable"
                    )
                }

                val photoEditorDirectory =
                    java.io.File(
                        picturesDirectory,
                        "PhotoEditor"
                    ).apply {
                        if (!exists()) {
                            mkdirs()
                        }
                    }

                val outputFile =
                    java.io.File(
                        photoEditorDirectory,
                        fileName
                    )

                outputFile.outputStream().use { outputStream ->
                    if (!exportedBitmap.compress(
                            compressFormat,
                            if (isJpeg) safeQuality else 100,
                            outputStream
                        )
                    ) {
                        throw IllegalStateException(
                            "Bitmap compression failed"
                        )
                    }
                }

                Toast.makeText(
                    this,
                    "Image saved to app Pictures/PhotoEditor",
                    Toast.LENGTH_SHORT
                ).show()

                saveSucceeded = true
            }
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Failed to save exported image",
                exception
            )

            Toast.makeText(
                this,
                "Failed to save image",
                Toast.LENGTH_SHORT
            ).show()
        } finally {
            if (!exportedBitmap.isRecycled) {
                exportedBitmap.recycle()
            }
        }

        return saveSucceeded
    }

    // -------------------------------------------------------------------------
    // BACK / FINISH
    // -------------------------------------------------------------------------

    override fun onBackPressed() {
        super.onBackPressed()
        if (!hasUnsavedChanges()) {
            finish()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Unsaved changes")
            .setMessage("Do you want to save your changes before leaving?")
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Discard") { _, _ ->
                finish()
            }
            .setPositiveButton("Save") { _, _ ->
                showExportDialog {
                    finish()
                }
            }
            .show()
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
        // GLOBAL HISTORY - PHASE 11.7
        // ---------------------------------------------------------------------

        btnUndo.setOnClickListener {
            if (!photoEditorView.undo()) {
                updateHistoryButtons()
            }
        }

        btnRedo.setOnClickListener {
            if (!photoEditorView.redo()) {
                updateHistoryButtons()
            }
        }

        // ---------------------------------------------------------------------
        // EXPORT - PHASE 12.3 FORMAT & QUALITY
        // ---------------------------------------------------------------------

        btnSave.setOnClickListener {
            showExportDialog()
        }

        // ---------------------------------------------------------------------
        // LAYERS - PHASE 10.2
        // ---------------------------------------------------------------------

        btnLayers.setOnClickListener {
            if (photoEditorView.getCurrentBitmap() == null) {
                Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (photoEditorView.isCropMode() ||
                photoEditorView.isRotationMode() ||
                photoEditorView.isAdjustmentMode() ||
                photoEditorView.isFilterMode() ||
                photoEditorView.isFreehandMode()
            ) {
                Toast.makeText(this, "Finish or cancel the current edit mode first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (layerPanel.visibility == View.VISIBLE) {
                closeLayerPanel()
            } else {
                updateLayerPanel()
                layerPanel.visibility = View.VISIBLE
            }
        }

        btnLayerBringToFront.setOnClickListener {
            reorderSelectedLayer { photoEditorView.bringSelectedElementToFront() }
        }
        btnLayerBringForward.setOnClickListener {
            reorderSelectedLayer { photoEditorView.bringSelectedElementForward() }
        }
        btnLayerSendBackward.setOnClickListener {
            reorderSelectedLayer { photoEditorView.sendSelectedElementBackward() }
        }
        btnLayerSendToBack.setOnClickListener {
            reorderSelectedLayer { photoEditorView.sendSelectedElementToBack() }
        }
        btnLayerDuplicate.setOnClickListener {
            if (photoEditorView.duplicateSelectedElement()) {
                updateLayerPanel()
                Toast.makeText(this, "Layer duplicated", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Select a layer to duplicate", Toast.LENGTH_SHORT).show()
            }
        }

        btnLayerDelete.setOnClickListener {
            val selected = photoEditorView.getSelectedElement()
            if (selected == null) {
                Toast.makeText(this, "Select a layer to delete", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val oldCount = photoEditorView.getLayerCount()
            photoEditorView.deleteSelectedElement()

            if (photoEditorView.getLayerCount() < oldCount) {
                updateLayerPanel()
                Toast.makeText(this, "Layer deleted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Layer could not be deleted", Toast.LENGTH_SHORT).show()
            }
        }

        btnLayerVisibility.setOnClickListener {
            val selected = photoEditorView.getSelectedElement()
            if (selected == null) {
                Toast.makeText(this, "Select a layer first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (photoEditorView.toggleSelectedElementVisibility()) {
                updateLayerPanel()
                val state = if (selected.isVisible) "shown" else "hidden"
                Toast.makeText(this, "Layer $state", Toast.LENGTH_SHORT).show()
            }
        }
        btnLayerLock.setOnClickListener {
            val selected = photoEditorView.getSelectedElement()
            if (selected == null) {
                Toast.makeText(this, "Select a layer first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (photoEditorView.toggleSelectedElementLock()) {
                updateLayerPanel()
                val state = if (selected.isLocked) "locked" else "unlocked"
                Toast.makeText(this, "Layer $state", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Layer lock could not be changed", Toast.LENGTH_SHORT).show()
            }
        }

        btnLayerClose.setOnClickListener { closeLayerPanel() }

        // ---------------------------------------------------------------------
        // SELECT IMAGE
        // ---------------------------------------------------------------------

        btnSelectImage.setOnClickListener {

            if (photoEditorView.isRotationMode()) {
                Toast.makeText(this, "Apply or cancel Rotation first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

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

            if (photoEditorView.isRotationMode()) {
                Toast.makeText(this, "Apply or cancel Rotation first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

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
        // TRANSFORM MODE - PHASE 6.9
        // ---------------------------------------------------------------------

        btnTransform.setOnClickListener {
            Log.d(TAG, "Transform button clicked")

            if (photoEditorView.getCurrentBitmap() == null) {
                Toast.makeText(
                    this,
                    "Please select an image first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (photoEditorView.isCropMode()) {
                Toast.makeText(
                    this,
                    "Finish or cancel Crop Mode first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            photoEditorView.enterTransformMode()
        }

        // ---------------------------------------------------------------------
        // ADJUSTMENT MODE - PHASE 7.1
        // ---------------------------------------------------------------------

        btnAdjust.setOnClickListener {
            Log.d(TAG, "Adjust button clicked")

            if (photoEditorView.getCurrentBitmap() == null) {
                Toast.makeText(
                    this,
                    "Please select an image first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (photoEditorView.isCropMode() || photoEditorView.isRotationMode()) {
                Toast.makeText(
                    this,
                    "Finish or cancel the current edit mode first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            photoEditorView.enterAdjustmentMode()
        }

        btnAdjustmentReset.setOnClickListener {
            Log.d(TAG, "Reset adjustments clicked")
            photoEditorView.resetAdjustments()
            updateAdjustmentControls(photoEditorView.getAdjustmentState())
        }

        btnAdjustmentCancel.setOnClickListener {
            Log.d(TAG, "Cancel adjustments clicked")
            photoEditorView.cancelAdjustments()
        }

        btnAdjustmentApply.setOnClickListener {
            Log.d(TAG, "Apply adjustments clicked")
            photoEditorView.applyAdjustments()
        }

        brightnessSeekBar.max = 100
        contrastSeekBar.max = 100
        saturationSeekBar.max = 100
        exposureSeekBar.max = 100
        temperatureSeekBar.max = 100
        highlightsSeekBar.max = 100
        shadowsSeekBar.max = 100

        brightnessSeekBar.setOnSeekBarChangeListener(
            createAdjustmentSeekBarListener { value ->
                updateAdjustmentState(brightness = value)
            }
        )

        contrastSeekBar.setOnSeekBarChangeListener(
            createAdjustmentSeekBarListener { value ->
                updateAdjustmentState(contrast = value)
            }
        )

        saturationSeekBar.setOnSeekBarChangeListener(
            createAdjustmentSeekBarListener { value ->
                updateAdjustmentState(saturation = value)
            }
        )

        exposureSeekBar.setOnSeekBarChangeListener(
            createAdjustmentSeekBarListener { value ->
                updateAdjustmentState(exposure = value)
            }
        )

        temperatureSeekBar.setOnSeekBarChangeListener(
            createAdjustmentSeekBarListener { value ->
                updateAdjustmentState(temperature = value)
            }
        )

        highlightsSeekBar.setOnSeekBarChangeListener(
            createAdjustmentSeekBarListener { value ->
                updateAdjustmentState(highlights = value)
            }
        )

        shadowsSeekBar.setOnSeekBarChangeListener(
            createAdjustmentSeekBarListener { value ->
                updateAdjustmentState(shadows = value)
            }
        )

        updateAdjustmentControls(photoEditorView.getAdjustmentState())

        // ---------------------------------------------------------------------
        // ANNOTATION APPEARANCE - PHASE 9.4 / 9.5
        // ---------------------------------------------------------------------

        fun updateAnnotationColor(color: Int) {
            // Store the selected color in PhotoEditorView so every newly
            // created annotation uses the exact color chosen by the user.
            photoEditorView.setAnnotationColor(color)

            btnAnnotationColor.text = "Color"
            btnAnnotationColor.backgroundTintList = ColorStateList.valueOf(color)

            val luminance =
                (0.299f * Color.red(color) +
                        0.587f * Color.green(color) +
                        0.114f * Color.blue(color)) / 255f

            btnAnnotationColor.setTextColor(
                if (luminance > 0.55f) Color.BLACK else Color.WHITE
            )
        }

        fun updateAnnotationStrokeWidth(strokeWidth: Float) {
            // Store the selected size in the same controller that creates the
            // annotation element. This guarantees new Freehand/Pen strokes
            // receive the selected width.
            photoEditorView.setAnnotationStrokeWidth(strokeWidth)
            btnAnnotationStrokeSize.text = "Size: ${strokeWidth.toInt()}px"
        }

        btnAnnotationColor.setOnClickListener {
            Log.d(TAG, "Opening annotation color picker")

            ColorPickerDialog(
                context = this,
                initialColor = photoEditorView.getAnnotationColor()
            ) { selectedColor ->
                Log.d(TAG, "Selected annotation color: $selectedColor")
                updateAnnotationColor(selectedColor)
            }.show()
        }

        updateAnnotationColor(photoEditorView.getAnnotationColor())
        updateAnnotationStrokeWidth(photoEditorView.getAnnotationStrokeWidth())

        btnAnnotationStrokeSize.setOnClickListener {
            val currentSize = photoEditorView.getAnnotationStrokeWidth()

            StrokeWidthPickerDialog(
                context = this,
                initialStrokeWidth = currentSize,
                minStrokeWidth = AnnotationController.MIN_STROKE_WIDTH,
                maxStrokeWidth = AnnotationController.MAX_STROKE_WIDTH
            ) { selectedStrokeWidth ->
                Log.d(
                    TAG,
                    "Selected annotation stroke width: $selectedStrokeWidth"
                )
                updateAnnotationStrokeWidth(selectedStrokeWidth)
            }.show()
        }

        // ---------------------------------------------------------------------
        // ANNOTATION MODE - PHASE 9.2
        // ---------------------------------------------------------------------

        btnAnnotation.setOnClickListener {
            Log.d(TAG, "Annotation button clicked")

            if (photoEditorView.getCurrentBitmap() == null) {
                Toast.makeText(
                    this,
                    "Please select an image first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (photoEditorView.isCropMode() ||
                photoEditorView.isRotationMode() ||
                photoEditorView.isAdjustmentMode() ||
                filterToolsScroll.visibility == View.VISIBLE
            ) {
                Toast.makeText(
                    this,
                    "Finish or cancel the current edit mode first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (annotationToolsScroll.visibility == View.VISIBLE) {
                photoEditorView.exitFreehandMode()
                annotationToolsScroll.visibility = View.GONE
                mainToolsScroll.visibility = View.VISIBLE
            } else {
                photoEditorView.enterAnnotationSelectionMode()
                annotationToolsScroll.visibility = View.VISIBLE
                mainToolsScroll.visibility = View.GONE
            }
        }

        btnAnnotationFreehand.setOnClickListener {
            Log.d(TAG, "Freehand annotation selected")

            if (photoEditorView.getCurrentBitmap() == null) {
                Toast.makeText(
                    this,
                    "Please select an image first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (photoEditorView.isCropMode() ||
                photoEditorView.isRotationMode() ||
                photoEditorView.isAdjustmentMode() ||
                filterToolsScroll.visibility == View.VISIBLE
            ) {
                Toast.makeText(
                    this,
                    "Finish or cancel the current edit mode first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (photoEditorView.enterFreehandMode()) {
                annotationToolsScroll.visibility = View.VISIBLE
                mainToolsScroll.visibility = View.GONE
                Toast.makeText(
                    this,
                    "Freehand drawing enabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnAnnotationPen.setOnClickListener {
            Log.d(TAG, "Pen annotation selected")

            if (photoEditorView.getCurrentBitmap() == null) {
                Toast.makeText(
                    this,
                    "Please select an image first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (photoEditorView.isCropMode() ||
                photoEditorView.isRotationMode() ||
                photoEditorView.isAdjustmentMode() ||
                filterToolsScroll.visibility == View.VISIBLE
            ) {
                Toast.makeText(
                    this,
                    "Finish or cancel the current edit mode first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (photoEditorView.enterPenMode()) {
                annotationToolsScroll.visibility = View.VISIBLE
                mainToolsScroll.visibility = View.GONE
                Toast.makeText(
                    this,
                    "Pen drawing enabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnAnnotationHighlighter.setOnClickListener {
            Log.d(TAG, "Highlighter annotation selected")

            if (photoEditorView.getCurrentBitmap() == null) {
                Toast.makeText(
                    this,
                    "Please select an image first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (photoEditorView.isCropMode() ||
                photoEditorView.isRotationMode() ||
                photoEditorView.isAdjustmentMode() ||
                filterToolsScroll.visibility == View.VISIBLE
            ) {
                Toast.makeText(
                    this,
                    "Finish or cancel the current edit mode first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (photoEditorView.enterHighlighterMode()) {
                annotationToolsScroll.visibility = View.VISIBLE
                mainToolsScroll.visibility = View.GONE
                Toast.makeText(
                    this,
                    "Highlighter enabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnAnnotationEraser.setOnClickListener {
            Log.d(TAG, "Eraser annotation selected")

            if (photoEditorView.getCurrentBitmap() == null) {
                Toast.makeText(
                    this,
                    "Please select an image first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (photoEditorView.isCropMode() ||
                photoEditorView.isRotationMode() ||
                photoEditorView.isAdjustmentMode() ||
                filterToolsScroll.visibility == View.VISIBLE
            ) {
                Toast.makeText(
                    this,
                    "Finish or cancel the current edit mode first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (photoEditorView.enterEraserMode()) {
                annotationToolsScroll.visibility = View.VISIBLE
                mainToolsScroll.visibility = View.GONE
                Toast.makeText(
                    this,
                    "Eraser enabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnAnnotationBlur.setOnClickListener {
            Log.d(TAG, "Blur annotation selected")

            if (photoEditorView.getCurrentBitmap() == null) {
                Toast.makeText(
                    this,
                    "Please select an image first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (photoEditorView.isCropMode() ||
                photoEditorView.isRotationMode() ||
                photoEditorView.isAdjustmentMode() ||
                filterToolsScroll.visibility == View.VISIBLE
            ) {
                Toast.makeText(
                    this,
                    "Finish or cancel the current edit mode first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (photoEditorView.enterBlurMode()) {
                annotationToolsScroll.visibility = View.VISIBLE
                mainToolsScroll.visibility = View.GONE
                Toast.makeText(
                    this,
                    "Blur enabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnAnnotationPixelate.setOnClickListener {
            Log.d(TAG, "Pixelate annotation selected")

            if (photoEditorView.getCurrentBitmap() == null) {
                Toast.makeText(
                    this,
                    "Please select an image first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (photoEditorView.isCropMode() ||
                photoEditorView.isRotationMode() ||
                photoEditorView.isAdjustmentMode() ||
                filterToolsScroll.visibility == View.VISIBLE
            ) {
                Toast.makeText(
                    this,
                    "Finish or cancel the current edit mode first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (photoEditorView.enterPixelateMode()) {
                annotationToolsScroll.visibility = View.VISIBLE
                mainToolsScroll.visibility = View.GONE
                Toast.makeText(
                    this,
                    "Pixelate enabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // ---------------------------------------------------------------------
        // ANNOTATION UNDO / REDO - PHASE 9
        // ---------------------------------------------------------------------

        btnAnnotationUndo.setOnClickListener {
            Log.d(TAG, "Annotation Undo clicked")

            if (photoEditorView.undoAnnotation()) {
                Toast.makeText(
                    this,
                    "Annotation undone",
                    Toast.LENGTH_SHORT
                ).show()
            }

            updateAnnotationHistoryButtons()
        }

        btnAnnotationRedo.setOnClickListener {
            Log.d(TAG, "Annotation Redo clicked")

            if (photoEditorView.redoAnnotation()) {
                Toast.makeText(
                    this,
                    "Annotation redone",
                    Toast.LENGTH_SHORT
                ).show()
            }

            updateAnnotationHistoryButtons()
        }

        btnAnnotationApply.setOnClickListener {
            Log.d(TAG, "Apply annotation clicked")

            if (photoEditorView.getCurrentBitmap() == null) {
                Toast.makeText(
                    this,
                    "Please select an image first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (photoEditorView.isCropMode() ||
                photoEditorView.isRotationMode() ||
                photoEditorView.isAdjustmentMode() ||
                filterToolsScroll.visibility == View.VISIBLE
            ) {
                Toast.makeText(
                    this,
                    "Finish or cancel the current edit mode first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (photoEditorView.applyAnnotations()) {
                annotationToolsScroll.visibility = View.GONE
                mainToolsScroll.visibility = View.VISIBLE
                updateAnnotationHistoryButtons()

                Toast.makeText(
                    this,
                    "Annotations applied",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "No annotations to apply",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnAnnotationCancel.setOnClickListener {
            Log.d(TAG, "Cancel annotation mode clicked")
            photoEditorView.exitFreehandMode()
            annotationToolsScroll.visibility = View.GONE
            mainToolsScroll.visibility = View.VISIBLE
        }

        // ---------------------------------------------------------------------
        // FILTER MODE - PHASE 7.2
        // ---------------------------------------------------------------------

        btnFilters.setOnClickListener {
            Log.d(TAG, "Filters button clicked")
            if (photoEditorView.getCurrentBitmap() == null) {
                Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (photoEditorView.isCropMode() || photoEditorView.isRotationMode() || photoEditorView.isAdjustmentMode()) {
                Toast.makeText(this, "Finish or cancel the current edit mode first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selectedFilterType = FilterType.ORIGINAL
            updateFilterButtons()
            photoEditorView.enterFilterMode()
        }

        btnFilterOriginal.setOnClickListener { selectFilter(FilterType.ORIGINAL) }
        btnFilterGrayscale.setOnClickListener { selectFilter(FilterType.GRAYSCALE) }
        btnFilterBlackWhite.setOnClickListener { selectFilter(FilterType.BLACK_WHITE) }
        btnFilterSepia.setOnClickListener { selectFilter(FilterType.SEPIA) }
        btnFilterVintage.setOnClickListener { selectFilter(FilterType.VINTAGE) }
        btnFilterWarm.setOnClickListener { selectFilter(FilterType.WARM) }
        btnFilterCool.setOnClickListener { selectFilter(FilterType.COOL) }
        btnFilterCancel.setOnClickListener {
            Log.d(TAG, "Cancel filters clicked")
            photoEditorView.cancelFilterMode()
        }

        btnFilterApply.setOnClickListener {
            Log.d(TAG, "Apply filter clicked: $selectedFilterType")
            photoEditorView.applyFilter()
        }

        // ---------------------------------------------------------------------
        // IMAGE TRANSFORM - PHASE 6.1 / 6.2
        // ---------------------------------------------------------------------

        btnFlipHorizontal.setOnClickListener {

            Log.d(
                TAG,
                "Horizontal flip button clicked"
            )

            if (photoEditorView.getCurrentBitmap() == null) {
                Toast.makeText(
                    this,
                    "Please select an image first",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            if (photoEditorView.isCropMode()) {
                Toast.makeText(
                    this,
                    "Finish or cancel Crop Mode first",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            photoEditorView.flipHorizontal()
        }

        // ---------------------------------------------------------------------
        // IMAGE TRANSFORM - PHASE 6.2
        // ---------------------------------------------------------------------

        btnFlipVertical.setOnClickListener {

            Log.d(
                TAG,
                "Vertical flip button clicked"
            )

            if (photoEditorView.getCurrentBitmap() == null) {
                Toast.makeText(
                    this,
                    "Please select an image first",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            if (photoEditorView.isCropMode()) {
                Toast.makeText(
                    this,
                    "Finish or cancel Crop Mode first",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            photoEditorView.flipVertical()
        }

        // ---------------------------------------------------------------------
        // IMAGE TRANSFORM - PHASE 6.3
        // ---------------------------------------------------------------------

        btnRotateLeft.setOnClickListener {

            Log.d(
                TAG,
                "Rotate left 90 degrees button clicked"
            )

            if (photoEditorView.getCurrentBitmap() == null) {
                Toast.makeText(
                    this,
                    "Please select an image first",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            if (photoEditorView.isCropMode()) {
                Toast.makeText(
                    this,
                    "Finish or cancel Crop Mode first",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            photoEditorView.rotateLeft90()
        }

        btnRotateRight.setOnClickListener {

            Log.d(
                TAG,
                "Rotate right 90 degrees button clicked"
            )

            if (photoEditorView.getCurrentBitmap() == null) {
                Toast.makeText(
                    this,
                    "Please select an image first",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            if (photoEditorView.isCropMode()) {
                Toast.makeText(
                    this,
                    "Finish or cancel Crop Mode first",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            photoEditorView.rotateRight90()
        }

        // ---------------------------------------------------------------------
        // ROTATION SESSION - PHASE 6.5 / 6.6
        // ---------------------------------------------------------------------

        btnRotationCancel.setOnClickListener {
            Log.d(TAG, "Cancel rotation clicked")
            photoEditorView.cancelRotation()
        }

        btnRotationApply.setOnClickListener {
            Log.d(TAG, "Apply rotation clicked")
            photoEditorView.applyRotation()
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

            if (photoEditorView.isRotationMode()) {
                Toast.makeText(this, "Apply or cancel Rotation first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showAddTextDialog()
        }

        // ---------------------------------------------------------------------
        // SHAPES - PHASE 8
        // ---------------------------------------------------------------------
        btnAddShape.setOnClickListener {
            if (photoEditorView.isRotationMode()) {
                Toast.makeText(this, "Apply or cancel Rotation first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (photoEditorView.getCurrentBitmap() == null) {
                Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            shapeToolsScroll.visibility =
                if (shapeToolsScroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        btnShapeRectangle.setOnClickListener { addShape(ShapeType.RECTANGLE) }
        btnShapeCircle.setOnClickListener { addShape(ShapeType.CIRCLE) }
        btnShapeRoundedRectangle.setOnClickListener { addShape(ShapeType.ROUNDED_RECTANGLE) }
        btnShapeTriangle.setOnClickListener { addShape(ShapeType.TRIANGLE) }
        btnShapeLine.setOnClickListener { addShape(ShapeType.LINE) }
        btnShapeArrow.setOnClickListener { addShape(ShapeType.ARROW) }
        btnShapePointer.setOnClickListener { addShape(ShapeType.POINTER) }
        btnShapeColor.setOnClickListener { showShapeColorDialog() }
        btnShapeStrokeWidth.setOnClickListener { showShapeStrokeWidthDialog() }
        btnShapeFill.setOnClickListener { toggleSelectedShapeFill() }

        // ---------------------------------------------------------------------
        // DELETE SELECTED ELEMENT
        // ---------------------------------------------------------------------

        btnDelete.setOnClickListener {

            Log.d(
                TAG,
                "Delete button clicked"
            )

            if (photoEditorView.isRotationMode()) {
                Toast.makeText(this, "Apply or cancel Rotation first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

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
            updateLayerPanel()
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

            shapeToolsScroll.visibility =
                if (element is ShapeElement) View.VISIBLE else View.GONE
            updateShapeStyleButtons(element)
        }

        photoEditorView.onAnnotationHistoryChanged = {
            updateAnnotationHistoryButtons()
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
        // FILTER MODE CHANGED - PHASE 7.2
        // ---------------------------------------------------------------------

        photoEditorView.onFilterModeChanged = { isFilterMode ->
            Log.d(TAG, "Filter mode changed: $isFilterMode")
            closeLayerPanel()
            filterToolsScroll.visibility = if (isFilterMode) View.VISIBLE else View.GONE
            annotationToolsScroll.visibility = View.GONE
            mainToolsScroll.visibility = if (isFilterMode) View.GONE else View.VISIBLE
            rotationToolsScroll.visibility = View.GONE
            cropToolsScroll.visibility = View.GONE
            textToolsScroll.visibility = View.GONE
            shapeToolsScroll.visibility = View.GONE
            adjustmentToolsScroll.visibility = View.GONE
            btnSelectImage.isEnabled = !isFilterMode
            btnAddText.isEnabled = !isFilterMode
            btnAddShape.isEnabled = !isFilterMode
            btnCrop.isEnabled = !isFilterMode
            btnTransform.isEnabled = !isFilterMode
            btnAdjust.isEnabled = !isFilterMode
            btnFilters.isEnabled = !isFilterMode
            btnFilterApply.isEnabled = isFilterMode
            btnDelete.isEnabled = !isFilterMode && photoEditorView.getSelectedElement() != null
            if (isFilterMode) {
                updateFilterButtons()
            } else {
                updateDeleteButton()
                updateTextEditButton(photoEditorView.getSelectedElement())
                updateTextColorButton(photoEditorView.getSelectedElement())
                updateTextSizeButton(photoEditorView.getSelectedElement())
                updateTextBoldButton(photoEditorView.getSelectedElement())
                updateTextItalicButton(photoEditorView.getSelectedElement())
                updateTextAlignmentButton(photoEditorView.getSelectedElement())
                updateTextBackgroundButton(photoEditorView.getSelectedElement())
                updateTextBackgroundColorButton(photoEditorView.getSelectedElement())
                updateTextFontButton(photoEditorView.getSelectedElement())
                updateTextMultiColorButton(photoEditorView.getSelectedElement())
            }
        }

        // ---------------------------------------------------------------------
        // ROTATION MODE CHANGED
        // ---------------------------------------------------------------------

        photoEditorView.onRotationModeChanged = { isRotationMode ->
            Log.d(TAG, "Rotation mode changed: $isRotationMode")
            closeLayerPanel()
            updateRotationTools(isRotationMode)

            annotationToolsScroll.visibility = View.GONE
            mainToolsScroll.visibility = if (isRotationMode) View.GONE else View.VISIBLE

            btnSelectImage.isEnabled = !isRotationMode
            btnAddText.isEnabled = !isRotationMode
            btnAddShape.isEnabled = !isRotationMode
            btnCrop.isEnabled = !isRotationMode
            btnAdjust.isEnabled = !isRotationMode
//            btnFlipHorizontal.isEnabled = !isRotationMode
//            btnFlipVertical.isEnabled = !isRotationMode
            btnFlipHorizontal.isEnabled = true
            btnFlipVertical.isEnabled = true
            btnDelete.isEnabled = !isRotationMode && photoEditorView.getSelectedElement() != null
            btnRotateLeft.isEnabled = true
            btnRotateRight.isEnabled = true

            if (!isRotationMode) {
                updateDeleteButton()
                updateTextEditButton(photoEditorView.getSelectedElement())
                updateTextColorButton(photoEditorView.getSelectedElement())
                updateTextSizeButton(photoEditorView.getSelectedElement())
                updateTextBoldButton(photoEditorView.getSelectedElement())
                updateTextItalicButton(photoEditorView.getSelectedElement())
                updateTextAlignmentButton(photoEditorView.getSelectedElement())
                updateTextBackgroundButton(photoEditorView.getSelectedElement())
                updateTextBackgroundColorButton(photoEditorView.getSelectedElement())
                updateTextFontButton(photoEditorView.getSelectedElement())
                updateTextMultiColorButton(photoEditorView.getSelectedElement())
            }
        }

        // ---------------------------------------------------------------------
        // CROP MODE CHANGED
        // ---------------------------------------------------------------------

        // ---------------------------------------------------------------------
        // ADJUSTMENT MODE CHANGED - PHASE 7.1
        // ---------------------------------------------------------------------
        photoEditorView.onAdjustmentModeChanged = { isAdjustmentMode ->
            Log.d(TAG, "Adjustment mode changed: $isAdjustmentMode")
            closeLayerPanel()

            if (isAdjustmentMode) {
                updateAdjustmentControls(photoEditorView.getAdjustmentState())
            }

            adjustmentToolsScroll.visibility =
                if (isAdjustmentMode) View.VISIBLE else View.GONE

            annotationToolsScroll.visibility = View.GONE
            mainToolsScroll.visibility =
                if (isAdjustmentMode) View.GONE else View.VISIBLE

            rotationToolsScroll.visibility = View.GONE
            cropToolsScroll.visibility = View.GONE
            textToolsScroll.visibility = View.GONE
            shapeToolsScroll.visibility = View.GONE

            btnSelectImage.isEnabled = !isAdjustmentMode
            btnAddText.isEnabled = !isAdjustmentMode
            btnAddShape.isEnabled = !isAdjustmentMode
            btnCrop.isEnabled = !isAdjustmentMode
            btnTransform.isEnabled = !isAdjustmentMode
            btnAdjust.isEnabled = !isAdjustmentMode
            btnFilters.isEnabled = !isAdjustmentMode
            btnDelete.isEnabled =
                !isAdjustmentMode &&
                        photoEditorView.getSelectedElement() != null

            if (!isAdjustmentMode) {
                updateDeleteButton()
                updateTextEditButton(photoEditorView.getSelectedElement())
                updateTextColorButton(photoEditorView.getSelectedElement())
                updateTextSizeButton(photoEditorView.getSelectedElement())
                updateTextBoldButton(photoEditorView.getSelectedElement())
                updateTextItalicButton(photoEditorView.getSelectedElement())
                updateTextAlignmentButton(photoEditorView.getSelectedElement())
                updateTextBackgroundButton(photoEditorView.getSelectedElement())
                updateTextBackgroundColorButton(photoEditorView.getSelectedElement())
                updateTextFontButton(photoEditorView.getSelectedElement())
                updateTextMultiColorButton(photoEditorView.getSelectedElement())
            }
        }

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
            annotationToolsScroll.visibility = View.GONE

            if (isCropMode) {
                shapeToolsScroll.visibility = View.GONE
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
    // SHAPE STYLING - PHASE 8.6
    // -------------------------------------------------------------------------

    private fun updateShapeStyleButtons(element: EditorElement?) {
        val shape = element as? ShapeElement
        val visible = shape != null
        btnShapeColor.visibility = if (visible) View.VISIBLE else View.GONE
        btnShapeStrokeWidth.visibility = if (visible) View.VISIBLE else View.GONE
        btnShapeFill.visibility = if (visible) View.VISIBLE else View.GONE

        if (shape != null) {
            btnShapeColor.text = "Color"
            btnShapeStrokeWidth.text = "Stroke: ${shape.strokeWidth.toInt()}"
            btnShapeFill.text = if (shape.isFilled) "Fill: On" else "Fill: Off"
        }
    }

    private fun getSelectedShape(): ShapeElement? =
        photoEditorView.getSelectedElement() as? ShapeElement

    private fun showShapeColorDialog() {
        val shape = getSelectedShape() ?: return
        val colors = intArrayOf(
            Color.WHITE,
            Color.BLACK,
            Color.RED,
            Color.GREEN,
            Color.BLUE,
            Color.YELLOW,
            Color.CYAN,
            Color.MAGENTA
        )
        val names = arrayOf(
            "White", "Black", "Red", "Green",
            "Blue", "Yellow", "Cyan", "Magenta"
        )
        AlertDialog.Builder(this)
            .setTitle("Shape Color")
            .setItems(names) { dialog, which ->
                shape.color = colors[which]
                updateShapeStyleButtons(shape)
                photoEditorView.invalidate()
                dialog.dismiss()
            }
            .show()
    }

    private fun showShapeStrokeWidthDialog() {
        val shape = getSelectedShape() ?: return
        val widths = floatArrayOf(2f, 4f, 6f, 8f, 12f, 16f, 20f)
        val labels = widths.map { "${it.toInt()} px" }.toTypedArray()
        var selected = widths.indices.minByOrNull { kotlin.math.abs(widths[it] - shape.strokeWidth) } ?: 1

        AlertDialog.Builder(this)
            .setTitle("Stroke Width")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                selected = which
                shape.strokeWidth = widths[which]
                updateShapeStyleButtons(shape)
                photoEditorView.invalidate()
                dialog.dismiss()
            }
            .show()
    }

    private fun toggleSelectedShapeFill() {
        val shape = getSelectedShape() ?: return
        shape.isFilled = !shape.isFilled
        updateShapeStyleButtons(shape)
        photoEditorView.invalidate()
    }

    // -------------------------------------------------------------------------
    // CREATE SHAPE ELEMENT - PHASE 8
    // -------------------------------------------------------------------------

    private fun addShape(shapeType: ShapeType) {
        if (photoEditorView.isRotationMode()) {
            Toast.makeText(this, "Apply or cancel Rotation first", Toast.LENGTH_SHORT).show()
            return
        }

        val imageWidth = photoEditorView.getImageWidth().toFloat()
        val imageHeight = photoEditorView.getImageHeight().toFloat()

        if (imageWidth <= 0f || imageHeight <= 0f) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
            return
        }

        val position = PointF(
            imageWidth / 2f,
            imageHeight / 2f
        )

        val shape = shapeController.createShape(
            shapeType = shapeType,
            position = position
        )

        photoEditorView.addElement(shape)
        shapeToolsScroll.visibility = View.VISIBLE
        updateDeleteButton()
        updateShapeStyleButtons(shape)

        Log.d(
            TAG,
            "Shape added: $shapeType"
        )
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
    // GLOBAL UNDO / REDO - PHASE 11.7
    // -------------------------------------------------------------------------

    private fun updateSaveButton() {
        val hasUnsavedChanges =
            photoEditorView.getCurrentBitmap() != null &&
                    photoEditorView.undoCount() != savedUndoCount

        btnSave.visibility =
            if (hasUnsavedChanges) View.VISIBLE else View.GONE
    }

    private fun hasUnsavedChanges(): Boolean =
        photoEditorView.getCurrentBitmap() != null &&
                photoEditorView.undoCount() != savedUndoCount

    private fun updateHistoryButtons() {
        val canUndo = photoEditorView.canUndo()
        val canRedo = photoEditorView.canRedo()

        btnUndo.isEnabled = canUndo
        btnRedo.isEnabled = canRedo

        btnUndo.alpha = if (canUndo) 1f else 0.45f
        btnRedo.alpha = if (canRedo) 1f else 0.45f

        updateSaveButton()

        btnUndo.contentDescription =
            if (canUndo) "Undo last edit" else "Undo unavailable"

        btnRedo.contentDescription =
            if (canRedo) "Redo last edit" else "Redo unavailable"
    }

    // -------------------------------------------------------------------------
    // ANNOTATION UNDO / REDO
    // -------------------------------------------------------------------------

    private fun updateAnnotationHistoryButtons() {

        val canUndo =
            photoEditorView.canUndoAnnotation()

        val canRedo =
            photoEditorView.canRedoAnnotation()

        // GONE removes unavailable buttons from the toolbar layout,
        // so they do not reserve horizontal space.
        btnAnnotationUndo.visibility =
            if (canUndo) View.VISIBLE else View.GONE

        btnAnnotationRedo.visibility =
            if (canRedo) View.VISIBLE else View.GONE

        btnAnnotationUndo.isEnabled = canUndo
        btnAnnotationRedo.isEnabled = canRedo
    }

    // -------------------------------------------------------------------------
    // LAYER PANEL - PHASE 10.2
    // -------------------------------------------------------------------------

    private fun closeLayerPanel() {
        layerPanel.visibility = View.GONE
    }

    private fun reorderSelectedLayer(action: () -> Boolean) {
        val selectedElement = photoEditorView.getSelectedElement()

        if (selectedElement == null) {
            Toast.makeText(
                this,
                "Please select a layer first",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val oldIndex = photoEditorView.getLayerIndex(selectedElement)
        val changed = action()
        val newIndex = photoEditorView.getLayerIndex(selectedElement)

        Log.d(
            TAG,
            "Layer reordered: oldIndex=$oldIndex, newIndex=$newIndex, changed=$changed"
        )

        updateLayerPanel()

        if (changed) {
            Toast.makeText(
                this,
                "Layer order updated",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                this,
                "Layer is already at that position",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateLayerPanel() {
        if (!::layerListContainer.isInitialized) return

        layerListContainer.removeAllViews()

        val layerCount = photoEditorView.getLayerCount()
        val selectedElement = photoEditorView.getSelectedElement()

        // ---------------------------------------------------------------------
        // SELECTED-LAYER ACTIONS
        // ---------------------------------------------------------------------

        val hasSelection = selectedElement != null

        btnLayerBringToFront.isEnabled = hasSelection
        btnLayerBringForward.isEnabled = hasSelection
        btnLayerSendBackward.isEnabled = hasSelection
        btnLayerSendToBack.isEnabled = hasSelection
        btnLayerDuplicate.isEnabled = hasSelection
        btnLayerDelete.isEnabled = hasSelection
        btnLayerVisibility.isEnabled = hasSelection
        btnLayerVisibility.text =
            if (selectedElement?.isVisible == false) {
                "Show Layer"
            } else {
                "Hide Layer"
            }

        btnLayerLock.isEnabled = hasSelection
        btnLayerLock.text =
            if (selectedElement?.isLocked == true) {
                "Unlock Layer"
            } else {
                "Lock Layer"
            }

        if (layerCount == 0) {
            layerListContainer.addView(TextView(this).apply {
                text = "No layers yet"
                textSize = 14f
                setTextColor(Color.LTGRAY)
                gravity = android.view.Gravity.CENTER
                setPadding(12, 20, 12, 20)
            })
            return
        }

        // ---------------------------------------------------------------------
        // LAYER LIST
        // ---------------------------------------------------------------------
        //
        // The editor stores layers bottom -> top.
        // The panel displays them top -> bottom so the visual order matches
        // what the user sees on the canvas.
        //
        // Each row clearly communicates:
        //   ✓ selected state
        //   👁 visibility state
        //   🔒 lock state
        //
        // Selection is still performed by tapping anywhere on the row.
        // ---------------------------------------------------------------------

        for (index in layerCount - 1 downTo 0) {
            val element = photoEditorView.getLayer(index) ?: continue
            val selected = element === selectedElement

            val typeName = when (element) {
                is TextElement -> "Text"
                is ShapeElement -> element.shapeType.name
                    .lowercase()
                    .replace('_', ' ')
                    .replaceFirstChar { it.uppercase() }
                else -> element::class.simpleName ?: "Element"
            }

            val visibilityIcon =
                if (element.isVisible) "👁" else "◌"

            val lockIcon =
                if (element.isLocked) "🔒" else "🔓"

            val selectedPrefix =
                if (selected) "✓ " else ""

            val button = Button(this).apply {
                text = "$selectedPrefix${index + 1}. $typeName    $visibilityIcon  $lockIcon"

                isAllCaps = false
                textSize = 13f
                minHeight = 48
                minimumHeight = 48
                setPadding(10, 0, 10, 0)

                // Make the selected layer immediately obvious without adding
                // another drawable/resource file.
                if (selected) {
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.rgb(65, 65, 65))
                } else {
                    setTextColor(Color.LTGRAY)
                }

                contentDescription =
                    buildString {
                        append(if (selected) "Selected " else "")
                        append("Layer ${index + 1}, $typeName, ")
                        append(if (element.isVisible) "visible" else "hidden")
                        append(", ")
                        append(if (element.isLocked) "locked" else "unlocked")
                    }

                setOnClickListener {
                    if (photoEditorView.selectLayer(index)) {
                        updateLayerPanel()
                    }
                }
            }

            layerListContainer.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 6
                }
            )
        }
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
    // TRANSFORM TOOLS UI
    // -------------------------------------------------------------------------

    private fun updateRotationTools(isRotationMode: Boolean) {
        rotationToolsScroll.visibility =
            if (isRotationMode) View.VISIBLE else View.GONE

        if (isRotationMode) {
            textToolsScroll.visibility = View.GONE
            cropToolsScroll.visibility = View.GONE
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

    private fun createAdjustmentSeekBarListener(
        onChanged: (Int) -> Unit
    ): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (
                    fromUser &&
                    !isUpdatingAdjustmentControls &&
                    photoEditorView.isAdjustmentMode()
                ) {
                    onChanged(progress)
                }
            }

            override fun onStartTrackingTouch(
                seekBar: SeekBar?
            ) {
                seekBar?.parent?.requestDisallowInterceptTouchEvent(true)

                Log.d(
                    TAG,
                    "Adjustment SeekBar drag started"
                )
            }

            override fun onStopTrackingTouch(
                seekBar: SeekBar?
            ) {
                seekBar?.parent?.requestDisallowInterceptTouchEvent(false)

                Log.d(
                    TAG,
                    "Adjustment SeekBar drag stopped"
                )
            }
        }
    }

    private fun updateAdjustmentState(
        brightness: Int? = null,
        contrast: Int? = null,
        saturation: Int? = null,
        exposure: Int? = null,
        temperature: Int? = null,
        highlights: Int? = null,
        shadows: Int? = null
    ) {
        if (!photoEditorView.isAdjustmentMode()) return

        val current = photoEditorView.getAdjustmentState()

        val updated = current.copy(
            brightness = brightness?.toFloat() ?: current.brightness,
            contrast = contrast?.toFloat() ?: current.contrast,
            saturation = saturation?.toFloat() ?: current.saturation,
            exposure = exposure?.toFloat() ?: current.exposure,
            temperature = temperature?.toFloat() ?: current.temperature,
            highlights = highlights?.toFloat() ?: current.highlights,
            shadows = shadows?.toFloat() ?: current.shadows
        )

        photoEditorView.setAdjustmentState(updated)
        updateAdjustmentValueLabels(updated)
    }

    private fun updateAdjustmentControls(
        state: com.allay.photoeditor.model.AdjustmentState
    ) {
        isUpdatingAdjustmentControls = true

        brightnessSeekBar.progress = state.brightness.toInt().coerceIn(0, 100)
        contrastSeekBar.progress = state.contrast.toInt().coerceIn(0, 100)
        saturationSeekBar.progress = state.saturation.toInt().coerceIn(0, 100)
        exposureSeekBar.progress = state.exposure.toInt().coerceIn(0, 100)
        temperatureSeekBar.progress = state.temperature.toInt().coerceIn(0, 100)
        highlightsSeekBar.progress = state.highlights.toInt().coerceIn(0, 100)
        shadowsSeekBar.progress = state.shadows.toInt().coerceIn(0, 100)

        updateAdjustmentValueLabels(state)

        isUpdatingAdjustmentControls = false
    }

    private fun updateAdjustmentValueLabels(
        state: com.allay.photoeditor.model.AdjustmentState
    ) {
        brightnessValueText.text = state.brightness.toInt().toString()
        contrastValueText.text = state.contrast.toInt().toString()
        saturationValueText.text = state.saturation.toInt().toString()
        exposureValueText.text = state.exposure.toInt().toString()
        temperatureValueText.text = state.temperature.toInt().toString()
        highlightsValueText.text = state.highlights.toInt().toString()
        shadowsValueText.text = state.shadows.toInt().toString()
    }

    private fun selectFilter(filterType: FilterType) {
        if (!photoEditorView.isFilterMode()) return

        selectedFilterType = filterType
        photoEditorView.setFilter(filterType)
        updateFilterButtons()

        Log.d(TAG, "Filter selected: $filterType")
    }

    private fun updateFilterButtons() {
        btnFilterOriginal.text =
            if (selectedFilterType == FilterType.ORIGINAL) "✓ Original" else "Original"

        btnFilterGrayscale.text =
            if (selectedFilterType == FilterType.GRAYSCALE) "✓ Grayscale" else "Grayscale"

        btnFilterBlackWhite.text =
            if (selectedFilterType == FilterType.BLACK_WHITE) "✓ B&W" else "B&W"

        btnFilterSepia.text =
            if (selectedFilterType == FilterType.SEPIA) "✓ Sepia" else "Sepia"

        btnFilterVintage.text =
            if (selectedFilterType == FilterType.VINTAGE) "✓ Vintage" else "Vintage"

        btnFilterWarm.text =
            if (selectedFilterType == FilterType.WARM) "✓ Warm" else "Warm"

        btnFilterCool.text =
            if (selectedFilterType == FilterType.COOL) "✓ Cool" else "Cool"
    }
}
