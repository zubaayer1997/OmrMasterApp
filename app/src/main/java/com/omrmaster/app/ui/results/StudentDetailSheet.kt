package com.omrmaster.app.ui.results

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omrmaster.app.data.local.ExamEntity
import com.omrmaster.app.data.local.StudentResultEntity
import com.omrmaster.app.omr.OmrLayoutSpec
import com.omrmaster.app.ui.theme.ErrorRed
import com.omrmaster.app.ui.theme.SuccessGreen
import com.omrmaster.app.ui.theme.WarningAmber
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailSheet(
    exam: ExamEntity,
    result: StudentResultEntity,
    onDismiss: () -> Unit,
    onOverrideAnswer: (questionIndex: Int, newAnswerCode: Int) -> Unit,
    onDelete: () -> Unit
) {
    val answers = result.getAnswersList(exam.questionCount)
    val answerKey = exam.getAnswerKeyMap()[result.setCode] ?: List(exam.questionCount) { -1 }
    var editingQuestionIdx by remember { mutableStateOf<Int?>(null) }

    val previewBitmap = remember(result.annotatedImagePath) {
        val f = File(result.annotatedImagePath)
        if (result.annotatedImagePath.isNotBlank() && f.exists()) {
            BitmapFactory.decodeFile(f.absolutePath)
        } else null
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "রোল: ${result.rollNumber}  |  সেকশন: ${result.section}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "সেট: ${result.setCode}  •  মোট নম্বর: ${String.format(Locale.US, "%.2f", result.obtainedMarks)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = SuccessGreen
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "মুছুন", tint = ErrorRed)
                }
            }

            Text(
                "যেকোনো প্রশ্নে ট্যাপ করে ম্যানুয়াল ওভাররাইড (সংশোধন) করতে পারবেন।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (previewBitmap != null) {
                    item {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Annotated OMR",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .padding(bottom = 6.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                items(exam.questionCount) { qIdx ->
                    val studentAns = answers.getOrElse(qIdx) { -1 }
                    val correctAns = answerKey.getOrElse(qIdx) { -1 }
                    val correctLabel = if (correctAns in 0..3) StudentResultEntity.OPTION_LABELS[correctAns] else "-"

                    val (statusText, statusColor, bgColor) = when {
                        studentAns == StudentResultEntity.ANSWER_MULTIPLE -> Triple(
                            "একাধিক ভরাট (বাতিল)",
                            WarningAmber,
                            Color(0xFFFFF8E5)
                        )
                        studentAns == StudentResultEntity.ANSWER_SKIPPED -> Triple(
                            "ফাঁকা (সঠিক: $correctLabel)",
                            Color.Gray,
                            Color(0xFFF3F4F6)
                        )
                        correctAns in 0..3 && studentAns == correctAns -> Triple(
                            "${StudentResultEntity.OPTION_LABELS[studentAns]} (সঠিক)",
                            SuccessGreen,
                            Color(0xFFECFDF3)
                        )
                        else -> Triple(
                            "${StudentResultEntity.OPTION_LABELS.getOrElse(studentAns) { "?" }} (সঠিক: $correctLabel)",
                            ErrorRed,
                            Color(0xFFFEF3F2)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor, RoundedCornerShape(10.dp))
                            .clickable {
                                editingQuestionIdx = if (editingQuestionIdx == qIdx) null else qIdx
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "প্রশ্ন ${OmrLayoutSpec.toBengaliNumber(qIdx + 1)}",
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(statusText, color = statusColor, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "সংশোধন",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (editingQuestionIdx == qIdx) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StudentResultEntity.OPTION_LABELS.forEachIndexed { optIdx, lbl ->
                                    FilterChip(
                                        selected = studentAns == optIdx,
                                        onClick = {
                                            onOverrideAnswer(qIdx, optIdx)
                                            editingQuestionIdx = null
                                        },
                                        label = { Text(lbl) }
                                    )
                                }
                                FilterChip(
                                    selected = studentAns == StudentResultEntity.ANSWER_SKIPPED,
                                    onClick = {
                                        onOverrideAnswer(qIdx, StudentResultEntity.ANSWER_SKIPPED)
                                        editingQuestionIdx = null
                                    },
                                    label = { Text("ফাঁকা") }
                                )
                                FilterChip(
                                    selected = studentAns == StudentResultEntity.ANSWER_MULTIPLE,
                                    onClick = {
                                        onOverrideAnswer(qIdx, StudentResultEntity.ANSWER_MULTIPLE)
                                        editingQuestionIdx = null
                                    },
                                    label = { Text("একাধিক") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
