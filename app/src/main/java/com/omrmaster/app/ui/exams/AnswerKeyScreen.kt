package com.omrmaster.app.ui.exams

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omrmaster.app.data.local.ExamEntity
import com.omrmaster.app.omr.OmrLayoutSpec
import com.omrmaster.app.ui.theme.PrimaryBlue
import com.omrmaster.app.viewmodel.OmrViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnswerKeyScreen(
    exam: ExamEntity,
    viewModel: OmrViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sets = exam.getSetCodes()
    var selectedSet by remember { mutableStateOf(sets.first()) }
    val keyMap = remember(exam) { exam.getAnswerKeyMap() }

    val workingAnswers = remember(exam, selectedSet) {
        mutableStateListOf<Int>().apply {
            addAll(keyMap[selectedSet] ?: List(exam.questionCount) { -1 })
        }
    }

    val masterKeyPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.scanMasterKeyFromUri(uri, exam, selectedSet) { ok, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("উত্তরপত্র সেটআপ", fontWeight = FontWeight.Bold)
                        Text(exam.name, style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveAnswerKey(exam, selectedSet, workingAnswers.toList())
                            Toast.makeText(context, "সেট '$selectedSet'-এর উত্তর সেভ হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("সেভ")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (sets.size > 1) {
                TabRow(selectedTabIndex = sets.indexOf(selectedSet).coerceAtLeast(0)) {
                    sets.forEach { setCode ->
                        Tab(
                            selected = selectedSet == setCode,
                            onClick = {
                                viewModel.saveAnswerKey(exam, selectedSet, workingAnswers.toList())
                                selectedSet = setCode
                            },
                            text = { Text("সেট $setCode") }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "সঠিক বৃত্তে ট্যাপ করুন (${workingAnswers.count { it in 0..3 }}/${exam.questionCount})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FilledTonalButton(
                    onClick = {
                        masterKeyPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan Master Key")
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(exam.questionCount) { qIdx ->
                    val currentAns = workingAnswers.getOrElse(qIdx) { -1 }
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "প্রশ্ন ${OmrLayoutSpec.toBengaliNumber(qIdx + 1)}",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(88.dp)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                OmrLayoutSpec.OPTION_BENGALI.forEachIndexed { optIdx, label ->
                                    val selected = currentAns == optIdx
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (selected) PrimaryBlue else Color.Transparent)
                                            .border(
                                                1.5.dp,
                                                if (selected) PrimaryBlue else Color.Gray,
                                                CircleShape
                                            )
                                            .clickable {
                                                workingAnswers[qIdx] = if (selected) -1 else optIdx
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (selected) Color.White else Color.Black,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
