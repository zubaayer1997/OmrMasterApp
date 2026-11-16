package com.omrmaster.app.ui.exams

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omrmaster.app.data.local.ExamEntity
import com.omrmaster.app.ui.components.viewPdfUri
import com.omrmaster.app.viewmodel.OmrViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    viewModel: OmrViewModel,
    modifier: Modifier = Modifier,
    onOpenAnswerKey: (Long) -> Unit
) {
    val exams by viewModel.exams.collectAsState()
    val context = LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var pdfExamTarget by remember { mutableStateOf<ExamEntity?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("এক্সাম ও উত্তরপত্র সেটআপ", fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("নতুন এক্সাম") }
            )
        }
    ) { innerPadding ->
        if (exams.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("নতুন এক্সাম তৈরি করতে নিচের '+ নতুন এক্সাম' বাটনে ট্যাপ করুন।")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(exams, key = { it.id }) { exam ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        exam.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val meta = buildString {
                                        if (exam.className.isNotBlank()) append("শ্রেণি: ${exam.className}  ")
                                        if (exam.subject.isNotBlank()) append("বিষয়: ${exam.subject}")
                                    }
                                    if (meta.isNotBlank()) {
                                        Text(
                                            meta,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.deleteExam(exam) }) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "মুছুন",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Text(
                                "প্রশ্ন: ${exam.questionCount}টি  •  সেট: ${exam.getSetCodes().joinToString(", ")}  •  " +
                                    if (exam.negativeMarking) "নেগেটিভ: -${exam.negativePenalty}" else "নেগেটিভ নেই",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onOpenAnswerKey(exam.id) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("উত্তর সেট করুন")
                                }
                                Button(
                                    onClick = { pdfExamTarget = exam },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("OMR PDF")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateExamDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, subject, className, qCount, setCount, neg, penalty ->
                viewModel.createExam(name, subject, className, qCount, setCount, neg, penalty) { newId ->
                    showCreateDialog = false
                    onOpenAnswerKey(newId)
                }
            }
        )
    }

    pdfExamTarget?.let { targetExam ->
        var paperSaver by remember { mutableStateOf(targetExam.questionCount <= 20) }
        AlertDialog(
            onDismissRequest = { pdfExamTarget = null },
            title = { Text("প্রিন্টেবল OMR শীট (PDF)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("এক্সাম: ${targetExam.name} (${targetExam.questionCount}টি প্রশ্ন)")
                    if (targetExam.questionCount <= 20) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Paper Saver Mode", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "একটি A4 কাগজে ২টি OMR শীট প্রিন্ট হবে",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(checked = paperSaver, onCheckedChange = { paperSaver = it })
                        }
                    } else {
                        Text(
                            "২০টির বেশি প্রশ্ন থাকায় পূর্ণ A4 সাইজের শীট তৈরি হবে।",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.generatePdf(targetExam, paperSaver) { uri ->
                            pdfExamTarget = null
                            viewPdfUri(context, uri)
                        }
                    }
                ) {
                    Text("PDF তৈরি ও খুলুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { pdfExamTarget = null }) {
                    Text("বন্ধ করুন")
                }
            }
        )
    }
}
