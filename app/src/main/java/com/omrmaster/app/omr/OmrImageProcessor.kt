package com.omrmaster.app.omr

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.omrmaster.app.data.local.ExamEntity
import com.omrmaster.app.data.local.StudentResultEntity
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

data class EvaluatedOmrOutput(
    val success: Boolean,
    val errorMessage: String? = null,
    val rollNumber: String = "0000",
    val section: String = "A",
    val setCode: String = "ক",
    val answers: List<Int> = emptyList(),
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val multipleCount: Int = 0,
    val skippedCount: Int = 0,
    val obtainedMarks: Double = 0.0,
    val annotatedBitmap: Bitmap? = null,
    val savedImagePath: String = ""
)

object OmrImageProcessor {

    private var initialized = false

    fun ensureInitialized(): Boolean {
        if (!initialized) {
            initialized = OpenCVLoader.initLocal()
        }
        return initialized
    }

    fun evaluateStudentSheet(
        context: Context,
        bitmap: Bitmap,
        exam: ExamEntity,
        saveToGallery: Boolean = true
    ): EvaluatedOmrOutput {
        if (!ensureInitialized()) {
            return EvaluatedOmrOutput(false, "OpenCV ইঞ্জিন লোড করা যায়নি।")
        }

        val geometry = OmrLayoutSpec.buildGeometry(exam.questionCount)
        val warpedRgba = warpToCanonicalSheet(bitmap, geometry)
            ?: return EvaluatedOmrOutput(
                false,
                "৪টি কোণার কালো বর্গক্ষেত্র স্পষ্টভাবে শনাক্ত করা যায়নি। পর্যাপ্ত আলোতে পুরো শীটের ছবি তুলুন।"
            )

        val gray = Mat()
        Imgproc.cvtColor(warpedRgba, gray, Imgproc.COLOR_RGBA2GRAY)
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        val binaryInv = Mat()
        Imgproc.adaptiveThreshold(
            blurred,
            binaryInv,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV,
            45,
            12.0
        )

        val w = OmrLayoutSpec.CANONICAL_WIDTH.toFloat()
        val h = OmrLayoutSpec.CANONICAL_HEIGHT.toFloat()

        // 1. Read Roll Number (4 digits)
        val rollDigits = geometry.rollGrid.map { colBubbles ->
            detectSingleSelection(binaryInv, colBubbles, w, h, minFill = 0.30, minDelta = 0.14)
        }
        val rollString = rollDigits.joinToString("") { idx ->
            if (idx in 0..9) idx.toString() else "0"
        }

        // 2. Read Section (A, B, C, D)
        val secIdx = detectSingleSelection(binaryInv, geometry.sectionBubbles, w, h, minFill = 0.30, minDelta = 0.14)
        val section = if (secIdx in OmrLayoutSpec.SECTION_LABELS.indices) {
            OmrLayoutSpec.SECTION_LABELS[secIdx]
        } else {
            "A"
        }

        // 3. Read Set Code (ক, খ, গ, ঘ)
        val availableSets = exam.getSetCodes()
        val setIdx = detectSingleSelection(binaryInv, geometry.setBubbles, w, h, minFill = 0.30, minDelta = 0.14)
        val detectedSet = if (setIdx in OmrLayoutSpec.OPTION_BENGALI.indices) {
            OmrLayoutSpec.OPTION_BENGALI[setIdx]
        } else {
            availableSets.first()
        }
        val effectiveSet = if (detectedSet in availableSets) detectedSet else availableSets.first()

        // 4. Answer Key for the detected set
        val answerKey = exam.getAnswerKeyMap()[effectiveSet]
            ?: List(exam.questionCount) { -1 }

        // 5. Evaluate all questions & prepare annotated Bitmap
        val annotatedBitmap = Bitmap.createBitmap(
            OmrLayoutSpec.CANONICAL_WIDTH,
            OmrLayoutSpec.CANONICAL_HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(warpedRgba, annotatedBitmap)

        val canvas = Canvas(annotatedBitmap)
        val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 175, 65)
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
        }
        val greenHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 175, 65)
            style = Paint.Style.STROKE
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
        }
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 25, 25)
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
        }
        val yellowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 175, 0)
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
        }
        val yellowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(55, 255, 210, 0)
            style = Paint.Style.FILL
        }

        val studentAnswers = MutableList(exam.questionCount) { StudentResultEntity.ANSWER_SKIPPED }
        var correctCount = 0
        var wrongCount = 0
        var multipleCount = 0
        var skippedCount = 0

        geometry.questionRows.forEachIndexed { qIdx, row ->
            val filledIndices = detectFilledBubblesInRow(binaryInv, row.bubbles, w, h)
            val correctOpt = answerKey.getOrElse(qIdx) { -1 }

            when {
                filledIndices.size >= 2 -> {
                    studentAnswers[qIdx] = StudentResultEntity.ANSWER_MULTIPLE
                    multipleCount++

                    val firstB = row.bubbles.first()
                    val lastB = row.bubbles.last()
                    val rPx = firstB.nRadius * w
                    val box = RectF(
                        firstB.nx * w - rPx * 1.4f,
                        firstB.ny * h - rPx * 1.4f,
                        lastB.nx * w + rPx * 1.4f,
                        lastB.ny * h + rPx * 1.4f
                    )
                    canvas.drawRoundRect(box, 8f, 8f, yellowFillPaint)
                    canvas.drawRoundRect(box, 8f, 8f, yellowStrokePaint)

                    filledIndices.forEach { bIdx ->
                        val b = row.bubbles[bIdx]
                        canvas.drawCircle(b.nx * w, b.ny * h, b.nRadius * w * 1.32f, yellowStrokePaint)
                    }
                }

                filledIndices.size == 1 -> {
                    val chosen = filledIndices.first()
                    studentAnswers[qIdx] = chosen
                    val chosenBubble = row.bubbles[chosen]
                    val cx = chosenBubble.nx * w
                    val cy = chosenBubble.ny * h
                    val cr = chosenBubble.nRadius * w * 1.38f

                    if (correctOpt in 0..3 && chosen == correctOpt) {
                        correctCount++
                        canvas.drawCircle(cx, cy, cr, greenPaint)
                    } else if (correctOpt in 0..3) {
                        wrongCount++
                        canvas.drawCircle(cx, cy, cr, redPaint)
                        val correctBubble = row.bubbles[correctOpt]
                        canvas.drawCircle(
                            correctBubble.nx * w,
                            correctBubble.ny * h,
                            correctBubble.nRadius * w * 1.32f,
                            greenHintPaint
                        )
                    } else {
                        canvas.drawCircle(cx, cy, cr, yellowStrokePaint)
                    }
                }

                else -> {
                    studentAnswers[qIdx] = StudentResultEntity.ANSWER_SKIPPED
                    skippedCount++
                    if (correctOpt in 0..3) {
                        val correctBubble = row.bubbles[correctOpt]
                        canvas.drawCircle(
                            correctBubble.nx * w,
                            correctBubble.ny * h,
                            correctBubble.nRadius * w * 1.28f,
                            greenHintPaint
                        )
                    }
                }
            }
        }

        val rawMarks = if (exam.negativeMarking) {
            correctCount - (wrongCount * exam.negativePenalty)
        } else {
            correctCount.toDouble()
        }
        val finalMarks = Math.round(rawMarks * 100.0) / 100.0

        drawTopHeaderOverlay(canvas, rollString, section, finalMarks)

        warpedRgba.release()
        gray.release()
        blurred.release()
        binaryInv.release()

        val savedPath = if (saveToGallery) {
            saveAnnotatedImage(context, annotatedBitmap, exam.name, rollString)
        } else {
            ""
        }

        return EvaluatedOmrOutput(
            success = true,
            rollNumber = rollString,
            section = section,
            setCode = effectiveSet,
            answers = studentAnswers,
            correctCount = correctCount,
            wrongCount = wrongCount,
            multipleCount = multipleCount,
            skippedCount = skippedCount,
            obtainedMarks = finalMarks,
            annotatedBitmap = annotatedBitmap,
            savedImagePath = savedPath
        )
    }

    fun extractMasterAnswerKey(bitmap: Bitmap, questionCount: Int): Pair<List<Int>?, String?> {
        if (!ensureInitialized()) {
            return null to "OpenCV ইঞ্জিন লোড করা যায়নি।"
        }
        val geometry = OmrLayoutSpec.buildGeometry(questionCount)
        val warpedRgba = warpToCanonicalSheet(bitmap, geometry)
            ?: return null to "মাস্টার কী শীটের ৪টি কালো বর্গক্ষেত্র শনাক্ত করা যায়নি।"

        val gray = Mat()
        Imgproc.cvtColor(warpedRgba, gray, Imgproc.COLOR_RGBA2GRAY)
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        val binaryInv = Mat()
        Imgproc.adaptiveThreshold(
            blurred,
            binaryInv,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV,
            45,
            12.0
        )

        val w = OmrLayoutSpec.CANONICAL_WIDTH.toFloat()
        val h = OmrLayoutSpec.CANONICAL_HEIGHT.toFloat()

        val detectedKey = geometry.questionRows.map { row ->
            val filled = detectFilledBubblesInRow(binaryInv, row.bubbles, w, h)
            if (filled.size == 1) filled.first() else -1
        }

        warpedRgba.release()
        gray.release()
        blurred.release()
        binaryInv.release()

        return if (detectedKey.any { it in 0..3 }) {
            detectedKey to null
        } else {
            null to "কোনো ভরাট করা উত্তর পাওয়া যায়নি।"
        }
    }

    private fun drawTopHeaderOverlay(
        canvas: Canvas,
        roll: String,
        section: String,
        marks: Double
    ) {
        val w = canvas.width.toFloat()
        val bannerRect = RectF(85f, 10f, w - 85f, 66f)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(242, 245, 250, 255)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 80, 160)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(bannerRect, 12f, 12f, bgPaint)
        canvas.drawRoundRect(bannerRect, 12f, 12f, borderPaint)

        val marksFormatted = if (marks % 1.0 == 0.0) {
            val intMarks = marks.toInt()
            if (intMarks in 0..9) "0$intMarks" else intMarks.toString()
        } else {
            String.format(Locale.US, "%.2f", marks)
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 25, 45)
            textSize = 31f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val overlayText = "Roll: $roll    Section: $section    Marks: $marksFormatted"
        val yOffset = -(textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(overlayText, w / 2f, bannerRect.centerY() + yOffset, textPaint)
    }

    private fun detectFilledBubblesInRow(
        binaryInv: Mat,
        bubbles: List<NormalizedBubble>,
        w: Float,
        h: Float
    ): List<Int> {
        val ratios = bubbles.map { computeBubbleFillRatio(binaryInv, it, w, h) }
        val minRatio = ratios.minOrNull() ?: 0.0
        val filled = mutableListOf<Int>()

        ratios.forEachIndexed { idx, r ->
            if (r >= 0.34 && (r >= minRatio + 0.16 || r >= 0.48)) {
                filled.add(idx)
            }
        }
        return filled
    }

    private fun detectSingleSelection(
        binaryInv: Mat,
        bubbles: List<NormalizedBubble>,
        w: Float,
        h: Float,
        minFill: Double,
        minDelta: Double
    ): Int {
        val ratios = bubbles.map { computeBubbleFillRatio(binaryInv, it, w, h) }
        val sorted = ratios.sorted()
        val maxIdx = ratios.indices.maxByOrNull { ratios[it] } ?: -1
        if (maxIdx == -1) return -1
        val maxVal = ratios[maxIdx]
        val secondVal = if (sorted.size >= 2) sorted[sorted.size - 2] else 0.0
        return if (maxVal >= minFill && (maxVal - secondVal >= minDelta * 0.6 || maxVal >= 0.50)) {
            maxIdx
        } else {
            -1
        }
    }

    private fun computeBubbleFillRatio(
        binaryInv: Mat,
        bubble: NormalizedBubble,
        w: Float,
        h: Float
    ): Double {
        val cx = (bubble.nx * w).toInt()
        val cy = (bubble.ny * h).toInt()
        val r = max(4, (bubble.nRadius * w * 0.74f).toInt())

        val x1 = (cx - r).coerceIn(0, binaryInv.cols() - 1)
        val y1 = (cy - r).coerceIn(0, binaryInv.rows() - 1)
        val x2 = (cx + r).coerceIn(x1 + 1, binaryInv.cols())
        val y2 = (cy + r).coerceIn(y1 + 1, binaryInv.rows())

        val roi = binaryInv.submat(y1, y2, x1, x2)
        val mask = Mat.zeros(roi.size(), CvType.CV_8UC1)
        val center = Point((roi.cols() / 2.0), (roi.rows() / 2.0))
        Imgproc.circle(mask, center, r, Scalar(255.0), -1)

        val masked = Mat()
        Core.bitwise_and(roi, mask, masked)
        val whitePixels = Core.countNonZero(masked).toDouble()
        val maskPixels = Core.countNonZero(mask).toDouble().coerceAtLeast(1.0)

        roi.release()
        mask.release()
        masked.release()

        return whitePixels / maskPixels
    }

    private fun warpToCanonicalSheet(bitmap: Bitmap, geometry: SheetGeometry): Mat? {
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)

        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

        val blockSize = max(25, ((minOf(gray.cols(), gray.rows()) / 35) or 1))
        val thresh = Mat()
        Imgproc.adaptiveThreshold(
            blurred,
            thresh,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV,
            blockSize,
            10.0
        )

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.morphologyEx(thresh, thresh, Imgproc.MORPH_CLOSE, kernel)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val imgArea = (rgba.cols() * rgba.rows()).toDouble()
        val candidates = mutableListOf<Pair<Point, Double>>()

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < imgArea * 0.00018 || area > imgArea * 0.035) continue

            val rect = Imgproc.boundingRect(contour)
            val aspect = rect.width.toDouble() / rect.height.coerceAtLeast(1)
            if (aspect < 0.55 || aspect > 1.85) continue

            val rectArea = (rect.width * rect.height).toDouble()
            val extent = area / rectArea
            if (extent < 0.62) continue

            val pts2f = MatOfPoint2f(*contour.toArray())
            val peri = Imgproc.arcLength(pts2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(pts2f, approx, 0.05 * peri, true)
            if (approx.total() in 4..6) {
                val roi = blurred.submat(rect)
                val meanBrightness = Core.mean(roi).`val`[0]
                roi.release()
                if (meanBrightness < 140.0) {
                    val center = Point(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0)
                    candidates.add(center to area)
                }
            }
            pts2f.release()
            approx.release()
        }

        gray.release()
        blurred.release()
        thresh.release()
        hierarchy.release()
        kernel.release()

        if (candidates.size < 4) {
            rgba.release()
            return null
        }

        val wImg = rgba.cols().toDouble()
        val hImg = rgba.rows().toDouble()

        val tl = closestCandidate(candidates, Point(0.0, 0.0))
        val tr = closestCandidate(candidates, Point(wImg, 0.0))
        val br = closestCandidate(candidates, Point(wImg, hImg))
        val bl = closestCandidate(candidates, Point(0.0, hImg))

        val distinctCorners = setOf(tl, tr, br, bl)
        if (distinctCorners.size < 4) {
            rgba.release()
            return null
        }

        val topWidth = hypot(tr.x - tl.x, tr.y - tl.y)
        val leftHeight = hypot(bl.x - tl.x, bl.y - tl.y)
        if (topWidth < wImg * 0.25 || leftHeight < hImg * 0.25) {
            rgba.release()
            return null
        }

        val srcPts = MatOfPoint2f(tl, tr, br, bl)
        val cw = OmrLayoutSpec.CANONICAL_WIDTH.toDouble()
        val ch = OmrLayoutSpec.CANONICAL_HEIGHT.toDouble()

        val dstPts = MatOfPoint2f(
            Point(geometry.anchorTopLeft.first * cw, geometry.anchorTopLeft.second * ch),
            Point(geometry.anchorTopRight.first * cw, geometry.anchorTopRight.second * ch),
            Point(geometry.anchorBottomRight.first * cw, geometry.anchorBottomRight.second * ch),
            Point(geometry.anchorBottomLeft.first * cw, geometry.anchorBottomLeft.second * ch)
        )

        val transform = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        val warped = Mat(OmrLayoutSpec.CANONICAL_HEIGHT, OmrLayoutSpec.CANONICAL_WIDTH, rgba.type())
        Imgproc.warpPerspective(rgba, warped, transform, Size(cw, ch))

        srcPts.release()
        dstPts.release()
        transform.release()
        rgba.release()

        if (isSheetUpsideDown(warped, geometry)) {
            Core.rotate(warped, warped, Core.ROTATE_180)
        }

        return warped
    }

    private fun isSheetUpsideDown(warpedRgba: Mat, geometry: SheetGeometry): Boolean {
        val gray = Mat()
        Imgproc.cvtColor(warpedRgba, gray, Imgproc.COLOR_RGBA2GRAY)
        val w = warpedRgba.cols()
        val h = warpedRgba.rows()

        val topX = (geometry.orientationMark.first * w).toInt()
        val topY = (geometry.orientationMark.second * h).toInt()
        val botX = ((1f - geometry.orientationMark.first) * w).toInt()
        val botY = ((1f - geometry.orientationMark.second) * h).toInt()

        val topMean = samplePatchMean(gray, topX, topY, 10)
        val botMean = samplePatchMean(gray, botX, botY, 10)
        gray.release()

        return botMean + 25.0 < topMean && abs(botMean - topMean) > 25.0
    }

    private fun samplePatchMean(gray: Mat, cx: Int, cy: Int, halfSize: Int): Double {
        val x1 = (cx - halfSize).coerceIn(0, gray.cols() - 2)
        val y1 = (cy - halfSize).coerceIn(0, gray.rows() - 2)
        val x2 = (cx + halfSize).coerceIn(x1 + 1, gray.cols())
        val y2 = (cy + halfSize).coerceIn(y1 + 1, gray.rows())
        val roi = gray.submat(y1, y2, x1, x2)
        val mean = Core.mean(roi).`val`[0]
        roi.release()
        return mean
    }

    private fun closestCandidate(
        candidates: List<Pair<Point, Double>>,
        targetCorner: Point
    ): Point {
        return candidates.minBy { (pt, _) ->
            hypot(pt.x - targetCorner.x, pt.y - targetCorner.y)
        }.first
    }

    private fun saveAnnotatedImage(
        context: Context,
        bitmap: Bitmap,
        examName: String,
        roll: String
    ): String {
        val safeExam = examName.replace(Regex("[^a-zA-Z0-9\\u0980-\\u09FF_-]"), "_")
        val fileName = "OMR_${safeExam}_Roll_${roll}_${System.currentTimeMillis()}.jpg"

        val internalDir = File(context.filesDir, "evaluated_sheets").apply { mkdirs() }
        val internalFile = File(internalDir, fileName)
        FileOutputStream(internalFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/OMR_Master/$safeExam"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            }
        } catch (_: Exception) {
            // App-internal copy is already saved safely
        }

        return internalFile.absolutePath
    }
}
