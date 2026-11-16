package com.omrmaster.app.omr

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.omrmaster.app.data.local.ExamEntity
import java.io.File
import java.io.FileOutputStream

object OmrPdfGenerator {

    // A4 points at 72 DPI
    private const val A4_PORTRAIT_W = 595
    private const val A4_PORTRAIT_H = 842
    private const val A4_LANDSCAPE_W = 842
    private const val A4_LANDSCAPE_H = 595

    fun generateOmrPdf(
        context: Context,
        exam: ExamEntity,
        paperSaverMode: Boolean
    ): Uri {
        val usePaperSaver = paperSaverMode && exam.questionCount <= 20
        val document = PdfDocument()
        val geometry = OmrLayoutSpec.buildGeometry(exam.questionCount)

        if (usePaperSaver) {
            val pageInfo = PdfDocument.PageInfo.Builder(A4_LANDSCAPE_W, A4_LANDSCAPE_H, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            // Left Half Sheet (A5 Portrait)
            drawSingleSheet(
                canvas = canvas,
                originX = 0f,
                originY = 0f,
                sheetWidth = A4_LANDSCAPE_W / 2f,
                sheetHeight = A4_LANDSCAPE_H.toFloat(),
                exam = exam,
                geometry = geometry
            )

            // Dashed Cut Line in the middle
            val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                strokeWidth = 1f
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
            }
            canvas.drawLine(
                A4_LANDSCAPE_W / 2f,
                10f,
                A4_LANDSCAPE_W / 2f,
                A4_LANDSCAPE_H - 10f,
                cutPaint
            )

            // Right Half Sheet (Identical A5 Portrait)
            drawSingleSheet(
                canvas = canvas,
                originX = A4_LANDSCAPE_W / 2f,
                originY = 0f,
                sheetWidth = A4_LANDSCAPE_W / 2f,
                sheetHeight = A4_LANDSCAPE_H.toFloat(),
                exam = exam,
                geometry = geometry
            )
            document.finishPage(page)
        } else {
            val pageInfo = PdfDocument.PageInfo.Builder(A4_PORTRAIT_W, A4_PORTRAIT_H, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            drawSingleSheet(
                canvas = canvas,
                originX = 0f,
                originY = 0f,
                sheetWidth = A4_PORTRAIT_W.toFloat(),
                sheetHeight = A4_PORTRAIT_H.toFloat(),
                exam = exam,
                geometry = geometry
            )
            document.finishPage(page)
        }

        val safeName = exam.name.replace(Regex("[^a-zA-Z0-9\\u0980-\\u09FF_-]"), "_")
        val fileName = "OMR_${safeName}_${exam.questionCount}Q.pdf"

        val resultUri = savePdfToStorage(context, document, fileName)
        document.close()
        return resultUri
    }

    private fun drawSingleSheet(
        canvas: Canvas,
        originX: Float,
        originY: Float,
        sheetWidth: Float,
        sheetHeight: Float,
        exam: ExamEntity,
        geometry: SheetGeometry
    ) {
        fun x(nx: Float) = originX + nx * sheetWidth
        fun y(ny: Float) = originY + ny * sheetHeight
        val scale = sheetWidth / 595f

        val blackFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.1f * scale
        }

        val thinBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            style = Paint.Style.STROKE
            strokeWidth = 0.7f * scale
        }

        val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 0.9f * scale
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 13.5f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9.5f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }

        val boldSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9.5f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val qNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 8.5f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }

        val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 7.4f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        // 1. Draw 4 Corner Black Square Anchors
        val anchorHalf = (geometry.anchorSizeRatio * sheetWidth) / 2f
        listOf(
            geometry.anchorTopLeft,
            geometry.anchorTopRight,
            geometry.anchorBottomRight,
            geometry.anchorBottomLeft
        ).forEach { (nx, ny) ->
            val cx = x(nx)
            val cy = y(ny)
            canvas.drawRect(
                cx - anchorHalf,
                cy - anchorHalf,
                cx + anchorHalf,
                cy + anchorHalf,
                blackFill
            )
        }

        // Small Orientation Indicator near Top-Left Anchor
        val oriHalf = anchorHalf * 0.45f
        val ox = x(geometry.orientationMark.first)
        val oy = y(geometry.orientationMark.second)
        canvas.drawRect(ox - oriHalf, oy - oriHalf, ox + oriHalf, oy + oriHalf, blackFill)

        // 2. Top Header Block (নাম, শ্রেণি, বিষয়)
        canvas.drawText(
            exam.name,
            x(0.5f),
            y(0.056f),
            titlePaint
        )

        val headerRect = RectF(x(0.055f), y(0.072f), x(0.945f), y(0.160f))
        canvas.drawRect(headerRect, borderPaint)

        // Student Name Line
        canvas.drawText("নাম:", x(0.072f), y(0.105f), labelPaint)
        canvas.drawLine(x(0.135f), y(0.108f), x(0.925f), y(0.108f), thinBorderPaint)

        // Class & Subject Line
        val classText = if (exam.className.isNotBlank()) "শ্রেণি: ${exam.className}" else "শ্রেণি: ........................."
        val subjectText = if (exam.subject.isNotBlank()) "বিষয়: ${exam.subject}" else "বিষয়: ...................................."
        canvas.drawText(classText, x(0.072f), y(0.142f), labelPaint)
        canvas.drawText(subjectText, x(0.45f), y(0.142f), labelPaint)
        canvas.drawText("পূর্ণমান: ${OmrLayoutSpec.toBengaliNumber(exam.questionCount)}", x(0.80f), y(0.142f), labelPaint)

        // 3. Left Metadata Block Box
        val metaRect = RectF(x(0.045f), y(0.175f), x(0.320f), y(0.945f))
        canvas.drawRect(metaRect, borderPaint)

        // Roll Header & 4 Digit Boxes
        canvas.drawText("রোল নম্বর", x(0.182f), y(0.197f), boldSmallPaint)
        val boxHalfW = 10f * scale
        val boxTop = y(0.207f)
        val boxBottom = y(0.233f)
        for (col in 0 until 4) {
            val cx = x(geometry.rollGrid[col][0].nx)
            canvas.drawRect(cx - boxHalfW, boxTop, cx + boxHalfW, boxBottom, thinBorderPaint)
        }

        // Roll Grid Bubbles (0..9)
        geometry.rollGrid.forEach { colBubbles ->
            colBubbles.forEach { b ->
                val cx = x(b.nx)
                val cy = y(b.ny)
                val r = b.nRadius * sheetWidth
                canvas.drawCircle(cx, cy, r, bubbleStroke)
                drawCenteredText(canvas, b.label, cx, cy, bubbleTextPaint)
            }
        }

        // Divider above Section
        canvas.drawLine(x(0.055f), y(0.650f), x(0.310f), y(0.650f), thinBorderPaint)
        canvas.drawText("সেকশন (Section)", x(0.182f), y(0.680f), boldSmallPaint)
        geometry.sectionBubbles.forEach { b ->
            val cx = x(b.nx)
            val cy = y(b.ny)
            val r = b.nRadius * sheetWidth
            canvas.drawCircle(cx, cy, r, bubbleStroke)
            drawCenteredText(canvas, b.label, cx, cy, bubbleTextPaint)
        }

        // Divider above Set Code
        canvas.drawLine(x(0.055f), y(0.775f), x(0.310f), y(0.775f), thinBorderPaint)
        canvas.drawText("সেট কোড", x(0.182f), y(0.808f), boldSmallPaint)
        geometry.setBubbles.forEach { b ->
            val cx = x(b.nx)
            val cy = y(b.ny)
            val r = b.nRadius * sheetWidth
            canvas.drawCircle(cx, cy, r, bubbleStroke)
            drawCenteredText(canvas, b.label, cx, cy, bubbleTextPaint)
        }

        // 4. Right Answer Grid Block
        val ansRect = RectF(x(0.335f), y(0.175f), x(0.955f), y(0.945f))
        canvas.drawRect(ansRect, borderPaint)
        canvas.drawText("উত্তরপত্র (সঠিক বৃত্তটি সম্পূর্ণ ভরাট করো)", x(0.645f), y(0.198f), boldSmallPaint)
        canvas.drawLine(x(0.335f), y(0.208f), x(0.955f), y(0.208f), thinBorderPaint)

        // Column dividers
        val gridWidth = 0.955f - 0.345f
        val colW = gridWidth / geometry.columnsCount
        for (c in 1 until geometry.columnsCount) {
            val divX = x(0.345f + c * colW)
            canvas.drawLine(divX, y(0.208f), divX, y(0.945f), thinBorderPaint)
        }

        // Question Rows & Bubbles
        geometry.questionRows.forEach { row ->
            val qLabel = "${OmrLayoutSpec.toBengaliNumber(row.questionNumber)}."
            drawRightAlignedVerticalCenteredText(
                canvas,
                qLabel,
                x(row.labelNx),
                y(row.labelNy),
                qNumPaint
            )

            row.bubbles.forEach { b ->
                val cx = x(b.nx)
                val cy = y(b.ny)
                val r = b.nRadius * sheetWidth
                canvas.drawCircle(cx, cy, r, bubbleStroke)
                drawCenteredText(canvas, b.label, cx, cy, bubbleTextPaint)
            }
        }
    }

    private fun drawCenteredText(canvas: Canvas, text: String, cx: Float, cy: Float, paint: Paint) {
        val yOffset = -(paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, cx, cy + yOffset, paint)
    }

    private fun drawRightAlignedVerticalCenteredText(
        canvas: Canvas,
        text: String,
        rx: Float,
        cy: Float,
        paint: Paint
    ) {
        val yOffset = -(paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, rx, cy + yOffset, paint)
    }

    private fun savePdfToStorage(context: Context, document: PdfDocument, fileName: String): Uri {
        val cacheDir = File(context.cacheDir, "omr_pdfs").apply { mkdirs() }
        val cacheFile = File(cacheDir, fileName)
        FileOutputStream(cacheFile).use { out ->
            document.writeTo(out)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OMR_Sheets")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    cacheFile.inputStream().copyTo(out)
                }
            }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )
    }
}
