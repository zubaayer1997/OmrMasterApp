package com.omrmaster.app.omr

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.omrmaster.app.data.local.ExamEntity
import com.omrmaster.app.data.local.StudentResultEntity
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object ReportExporter {

    fun exportCsvReport(
        context: Context,
        exam: ExamEntity,
        results: List<StudentResultEntity>
    ): Uri {
        val sorted = results.sortedWith(
            compareByDescending<StudentResultEntity> { it.obtainedMarks }
                .thenBy { it.rollNumber }
        )

        val sb = StringBuilder()
        // UTF-8 BOM so Bengali opens properly in Excel
        sb.append("\uFEFF")
        sb.append("মেধাক্রম,রোল নম্বর,সেকশন,সেট কোড,সঠিক,ভুল,একাধিক ভরাট,ফাঁকা,প্রাপ্ত নম্বর\n")

        sorted.forEachIndexed { index, r ->
            sb.append("${index + 1},\"${r.rollNumber}\",${r.section},${r.setCode},${r.correctCount},${r.wrongCount},${r.multipleCount},${r.skippedCount},${String.format(Locale.US, "%.2f", r.obtainedMarks)}\n")
        }

        val safeName = exam.name.replace(Regex("[^a-zA-Z0-9\\u0980-\\u09FF_-]"), "_")
        val file = File(File(context.cacheDir, "reports").apply { mkdirs() }, "Merit_${safeName}.csv")
        file.writeText(sb.toString(), Charsets.UTF_8)

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun exportPdfMeritReport(
        context: Context,
        exam: ExamEntity,
        results: List<StudentResultEntity>
    ): Uri {
        val sorted = results.sortedWith(
            compareByDescending<StudentResultEntity> { it.obtainedMarks }
                .thenBy { it.rollNumber }
        )

        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 45, 95)
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 10.5f
            textAlign = Paint.Align.CENTER
        }

        val headerBgPaint = Paint().apply {
            color = Color.rgb(230, 238, 250)
            style = Paint.Style.FILL
        }

        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9.5f
            textAlign = Paint.Align.CENTER
        }

        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.8f
        }

        val highest = sorted.maxOfOrNull { it.obtainedMarks } ?: 0.0
        val average = if (sorted.isNotEmpty()) sorted.map { it.obtainedMarks }.average() else 0.0

        val colXs = listOf(45f, 95f, 165f, 220f, 275f, 330f, 390f, 460f, 535f)
        val headers = listOf("ক্রম", "রোল", "সেকশন", "সেট", "সঠিক", "ভুল", "অবৈধ", "ফাঁকা", "নম্বর")

        var pageNum = 1
        var idx = 0
        while (idx < sorted.size || pageNum == 1) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            var y = 45f
            if (pageNum == 1) {
                canvas.drawText("ফলাফল ও মেধা তালিকা: ${exam.name}", pageWidth / 2f, y, titlePaint)
                y += 18f
                canvas.drawText(
                    "মোট পরীক্ষার্থী: ${sorted.size}   |   সর্বোচ্চ নম্বর: ${String.format(Locale.US, "%.2f", highest)}   |   গড় নম্বর: ${String.format(Locale.US, "%.2f", average)}",
                    pageWidth / 2f,
                    y,
                    subPaint
                )
                y += 22f
            } else {
                canvas.drawText("${exam.name} (পৃষ্ঠা $pageNum)", pageWidth / 2f, y, subPaint)
                y += 22f
            }

            // Table Header
            canvas.drawRect(25f, y - 14f, pageWidth - 25f, y + 8f, headerBgPaint)
            headers.forEachIndexed { hIdx, label ->
                canvas.drawText(label, colXs[hIdx], y, headerTextPaint)
            }
            y += 20f

            while (idx < sorted.size && y < pageHeight - 40f) {
                val r = sorted[idx]
                val values = listOf(
                    (idx + 1).toString(),
                    r.rollNumber,
                    r.section,
                    r.setCode,
                    r.correctCount.toString(),
                    r.wrongCount.toString(),
                    r.multipleCount.toString(),
                    r.skippedCount.toString(),
                    String.format(Locale.US, "%.2f", r.obtainedMarks)
                )
                values.forEachIndexed { vIdx, txt ->
                    canvas.drawText(txt, colXs[vIdx], y, cellPaint)
                }
                canvas.drawLine(25f, y + 5f, pageWidth - 25f, y + 5f, linePaint)
                y += 20f
                idx++
            }

            document.finishPage(page)
            pageNum++
            if (sorted.isEmpty()) break
        }

        val safeName = exam.name.replace(Regex("[^a-zA-Z0-9\\u0980-\\u09FF_-]"), "_")
        val file = File(File(context.cacheDir, "reports").apply { mkdirs() }, "Merit_${safeName}.pdf")
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
