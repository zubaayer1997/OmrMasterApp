package com.omrmaster.app.omr

import kotlin.math.ceil

data class NormalizedBubble(
    val nx: Float,
    val ny: Float,
    val nRadius: Float,
    val label: String
)

data class QuestionRowSpec(
    val questionNumber: Int,
    val labelNx: Float,
    val labelNy: Float,
    val bubbles: List<NormalizedBubble>
)

data class SheetGeometry(
    val anchorTopLeft: Pair<Float, Float>,
    val anchorTopRight: Pair<Float, Float>,
    val anchorBottomRight: Pair<Float, Float>,
    val anchorBottomLeft: Pair<Float, Float>,
    val anchorSizeRatio: Float,
    val orientationMark: Pair<Float, Float>,
    val rollGrid: List<List<NormalizedBubble>>, // 4 columns x 10 digits (0..9)
    val sectionBubbles: List<NormalizedBubble>, // A, B, C, D
    val setBubbles: List<NormalizedBubble>, // ক, খ, গ, ঘ
    val questionRows: List<QuestionRowSpec>,
    val columnsCount: Int
)

object OmrLayoutSpec {
    const val CANONICAL_WIDTH = 1200
    const val CANONICAL_HEIGHT = 1700

    val BENGALI_DIGITS = listOf("০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯")
    val OPTION_BENGALI = listOf("ক", "খ", "গ", "ঘ")
    val SECTION_LABELS = listOf("A", "B", "C", "D")

    fun toBengaliNumber(number: Int): String {
        return number.toString().map { ch ->
            if (ch in '0'..'9') BENGALI_DIGITS[ch - '0'] else ch.toString()
        }.joinToString("")
    }

    fun getColumnCount(questionCount: Int): Int = when {
        questionCount <= 10 -> 2
        questionCount <= 15 -> 3
        else -> 4
    }

    fun buildGeometry(questionCount: Int): SheetGeometry {
        val anchorTL = 0.032f to 0.025f
        val anchorTR = 0.968f to 0.025f
        val anchorBR = 0.968f to 0.975f
        val anchorBL = 0.032f to 0.975f
        val anchorSize = 0.028f
        val orientationMark = 0.075f to 0.025f

        // Left Metadata Block: Roll (4 digits x 10 rows)
        val rollStartX = 0.088f
        val rollColStep = 0.056f
        val rollStartY = 0.255f
        val rollRowStep = 0.038f
        val metaBubbleRadius = 0.0135f

        val rollGrid = (0 until 4).map { col ->
            (0..9).map { digit ->
                NormalizedBubble(
                    nx = rollStartX + col * rollColStep,
                    ny = rollStartY + digit * rollRowStep,
                    nRadius = metaBubbleRadius,
                    label = BENGALI_DIGITS[digit]
                )
            }
        }

        // Section (A, B, C, D)
        val secStartX = 0.088f
        val secStepX = 0.056f
        val secY = 0.725f
        val sectionBubbles = SECTION_LABELS.mapIndexed { idx, label ->
            NormalizedBubble(
                nx = secStartX + idx * secStepX,
                ny = secY,
                nRadius = metaBubbleRadius,
                label = label
            )
        }

        // Set Code (ক, খ, গ, ঘ)
        val setY = 0.855f
        val setBubbles = OPTION_BENGALI.mapIndexed { idx, label ->
            NormalizedBubble(
                nx = secStartX + idx * secStepX,
                ny = setY,
                nRadius = metaBubbleRadius,
                label = label
            )
        }

        // Right Answer Grid Block (Dynamic 2, 3, or 4 columns)
        val cols = getColumnCount(questionCount)
        val rowsPerCol = ceil(questionCount.toFloat() / cols).toInt().coerceAtLeast(1)

        val gridLeft = 0.345f
        val gridRight = 0.955f
        val gridWidth = gridRight - gridLeft
        val colWidth = gridWidth / cols

        val gridTop = 0.215f
        val gridBottom = 0.935f
        val availableHeight = gridBottom - gridTop
        val rowStep = minOf(0.034f, availableHeight / rowsPerCol.coerceAtLeast(1))
        val ansBubbleRadius = minOf(0.0115f, colWidth * 0.072f, rowStep * 0.39f)

        val rows = (1..questionCount).map { qNum ->
            val colIdx = (qNum - 1) / rowsPerCol
            val rowIdx = (qNum - 1) % rowsPerCol
            val colX = gridLeft + colIdx * colWidth
            val cy = gridTop + rowIdx * rowStep + rowStep * 0.5f

            val labelNx = colX + colWidth * 0.14f
            val firstBubbleNx = colX + colWidth * 0.33f
            val bubbleGap = (colWidth * 0.61f) / 3f

            val bubbles = OPTION_BENGALI.mapIndexed { optIdx, optLabel ->
                NormalizedBubble(
                    nx = firstBubbleNx + optIdx * bubbleGap,
                    ny = cy,
                    nRadius = ansBubbleRadius,
                    label = optLabel
                )
            }

            QuestionRowSpec(
                questionNumber = qNum,
                labelNx = labelNx,
                labelNy = cy,
                bubbles = bubbles
            )
        }

        return SheetGeometry(
            anchorTopLeft = anchorTL,
            anchorTopRight = anchorTR,
            anchorBottomRight = anchorBR,
            anchorBottomLeft = anchorBL,
            anchorSizeRatio = anchorSize,
            orientationMark = orientationMark,
            rollGrid = rollGrid,
            sectionBubbles = sectionBubbles,
            setBubbles = setBubbles,
            questionRows = rows,
            columnsCount = cols
        )
    }
}
