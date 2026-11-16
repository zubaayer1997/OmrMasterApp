package com.omrmaster.app.ui.results

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.omrmaster.app.data.local.StudentResultEntity
import com.omrmaster.app.ui.components.openOrShareUri
import com.omrmaster.app.ui.components.viewPdfUri
import com.omrmaster.app.ui.theme.PrimaryBlue
import com.omrmaster.app.ui.theme.SuccessGreen
import com.omrmaster.app.viewmodel.OmrViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    viewModel: OmrViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exams by viewModel.exams.collectAsState()
    val selectedExam by viewModel.selectedExam.collectAsState()
    val results by viewModel.resultsForSelectedExam.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var examDropdownExpanded by remember { mutableStateOf(false) }
    var selectedResultForModal by remember { mutableStateOf<StudentResultEntity?>(null) }

    val filteredResults = remember(results, searchQuery) {
        if (searchQuery.isBlank()) results
        else results.filter {
            it.rollNumber.contains(searchQuery.trim(), ignoreCase = true) ||
                it.section.contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    val highestScore = results.maxOfOrNull { it.obtainedMarks } ?: 0.0
    val averageScore = if (results.isNotEmpty()) results.map { it.obtainedMarks }.average() else 0.0

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("ফলাফল ও মেধা তালিকা", fontWeight = FontWeight.Bold) },
            actions = {
                val exam = selectedExam
                if (exam != null && results.isNotEmpty()) {
                    IconButton(onClick = {
                        viewModel.exportCsv(exam, results) { uri ->
                            openOrShareUri(context, uri, "text/csv", "Excel CSV এক্সপোর্ট")
                        }
                    }) {
                        Icon(Icons.Default.TableView, contentDescription = "CSV এক্সপোর্ট")
                    }
                    IconButton(onClick = {
                        viewModel.exportPdf(exam, results) { uri ->
                            viewPdfUri(context, uri)
                        }
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF রিপোর্ট")
                    }
                }
            }
        )

        if (exams.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("এখনো কোনো এক্সাম নেই।")
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Exam Selector Dropdown
            ExposedDropdownMenuBox(
                expanded = examDropdownExpanded,
                onExpandedChange = { examDropdownExpanded = !examDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedExam?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("এক্সাম অনুযায়ী ফিল্টার") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = examDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = examDropdownExpanded,
                    onDismissRequest = { examDropdownExpanded = false }
                ) {
                    exams.forEach { exam ->
                        DropdownMenuItem(
                            text = { Text(exam.name) },
                            onClick = {
                                viewModel.selectExam(exam.id)
                                examDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Summary Stats Strip
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    SummaryItem("মোট পরীক্ষার্থী", "${results.size}", PrimaryBlue)
                    SummaryItem("সর্বোচ্চ নম্বর", String.format(Locale.US, "%.2f", highestScore), SuccessGreen)
                    SummaryItem("গড় নম্বর", String.format(Locale.US, "%.2f", averageScore), Color.DarkGray)
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("রোল বা সেকশন দিয়ে খুঁজুন...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Merit List
            if (filteredResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("এই এক্সামে কোনো ফলাফল পাওয়া যায়নি।")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(filteredResults, key = { _, item -> item.id }) { index, res ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            onClick = { selectedResultForModal = res }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            "#${index + 1}",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryBlue
                                        )
                                    }
                                    Column {
                                        Text(
                                            "রোল: ${res.rollNumber}  (সেকশন: ${res.section})",
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "সেট: ${res.setCode} • সঠিক: ${res.correctCount} • ভুল: ${res.wrongCount} • একাধিক: ${res.multipleCount}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = String.format(Locale.US, "%.2f", res.obtainedMarks),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val currentExam = selectedExam
    val activeModalResult = results.find { it.id == selectedResultForModal?.id }
    if (currentExam != null && activeModalResult != null) {
        StudentDetailSheet(
            exam = currentExam,
            result = activeModalResult,
            onDismiss = { selectedResultForModal = null },
            onOverrideAnswer = { qIdx, newCode ->
                viewModel.overrideStudentAnswer(activeModalResult, currentExam, qIdx, newCode)
            },
            onDelete = {
                viewModel.deleteResult(activeModalResult)
                selectedResultForModal = null
            }
        )
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}
