package com.omrmaster.app.ui.dashboard

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omrmaster.app.omr.OmrLayoutSpec
import com.omrmaster.app.ui.components.StatCard
import com.omrmaster.app.ui.navigation.BottomTab
import com.omrmaster.app.ui.theme.PrimaryBlue
import com.omrmaster.app.ui.theme.SecondaryTeal
import com.omrmaster.app.ui.theme.SuccessGreen
import com.omrmaster.app.viewmodel.OmrViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: OmrViewModel,
    modifier: Modifier = Modifier,
    onNavigateTab: (BottomTab) -> Unit,
    onOpenAnswerKey: (Long) -> Unit
) {
    val exams by viewModel.exams.collectAsState()
    val allResults by viewModel.allResults.collectAsState()

    val avgScore = if (allResults.isNotEmpty()) {
        String.format(Locale.US, "%.1f", allResults.map { it.obtainedMarks }.average())
    } else "0.0"

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("OMR মাস্টার ড্যাশবোর্ড", fontWeight = FontWeight.Bold)
                    Text(
                        "অফলাইন ওএমআর স্ক্যানার ও মূল্যায়ন",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "মোট এক্সাম",
                        value = OmrLayoutSpec.toBengaliNumber(exams.size),
                        icon = Icons.Default.Assignment,
                        tint = PrimaryBlue,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "মূল্যায়িত শীট",
                        value = OmrLayoutSpec.toBengaliNumber(allResults.size),
                        icon = Icons.Default.FactCheck,
                        tint = SecondaryTeal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "সর্বমোট গড় নম্বর",
                        value = avgScore,
                        icon = Icons.Default.TrendingUp,
                        tint = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PrimaryBlue),
                        onClick = { onNavigateTab(BottomTab.SCANNER) }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = Color.White)
                            Text(
                                "দ্রুত স্ক্যান শুরু করুন",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "দ্রুত অ্যাকশন",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onNavigateTab(BottomTab.EXAMS) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("নতুন এক্সাম")
                    }
                    FilledTonalButton(
                        onClick = { onNavigateTab(BottomTab.SCANNER) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("বাল্ক আপলোড")
                    }
                }
            }

            item {
                Text(
                    "সাম্প্রতিক এক্সামসমূহ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (exams.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("এখনো কোনো এক্সাম তৈরি করা হয়নি।")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { onNavigateTab(BottomTab.EXAMS) }) {
                                Text("প্রথম এক্সাম তৈরি করুন")
                            }
                        }
                    }
                }
            } else {
                items(exams.take(5)) { exam ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        onClick = { onOpenAnswerKey(exam.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exam.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "${exam.questionCount}টি প্রশ্ন • ${exam.setCount} সেট • " +
                                        if (exam.negativeMarking) "নেগেটিভ: -${exam.negativePenalty}" else "নেগেটিভ নেই",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { onOpenAnswerKey(exam.id) }) {
                                Text("উত্তরপত্র")
                            }
                        }
                    }
                }
            }
        }
    }
}
