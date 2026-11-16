package com.omrmaster.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * প্রতিটি প্রশ্নের ছাত্রের উত্তর কোড:
 * 0 = ক, 1 = খ, 2 = গ, 3 = ঘ
 * -1 = ফাঁকা (Skipped)
 * -2 = একাধিক ভরাট (Multiple / Invalid)
 */
@Entity(
    tableName = "student_results",
    foreignKeys = [
        ForeignKey(
            entity = ExamEntity::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("examId")]
)
data class StudentResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val examId: Long,
    val rollNumber: String,
    val section: String,
    val setCode: String,
    val studentAnswersCsv: String,
    val correctCount: Int,
    val wrongCount: Int,
    val multipleCount: Int,
    val skippedCount: Int,
    val obtainedMarks: Double,
    val annotatedImagePath: String = "",
    val scannedAt: Long = System.currentTimeMillis()
) {
    fun getAnswersList(questionCount: Int): List<Int> {
        val parts = studentAnswersCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
        return List(questionCount) { idx -> parts.getOrElse(idx) { -1 } }
    }

    companion object {
        const val ANSWER_SKIPPED = -1
        const val ANSWER_MULTIPLE = -2
        val OPTION_LABELS = listOf("ক", "খ", "গ", "ঘ")

        fun encodeAnswers(list: List<Int>): String = list.joinToString(",")
    }
}
