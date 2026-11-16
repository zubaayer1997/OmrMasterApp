package com.omrmaster.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.omrmaster.app.data.local.ExamEntity
import com.omrmaster.app.ui.dashboard.DashboardScreen
import com.omrmaster.app.ui.exams.AnswerKeyScreen
import com.omrmaster.app.ui.exams.ExamsScreen
import com.omrmaster.app.ui.results.ResultsScreen
import com.omrmaster.app.ui.scanner.ScannerScreen
import com.omrmaster.app.viewmodel.OmrViewModel

enum class BottomTab(val title: String, val icon: ImageVector) {
    HOME("হোম", Icons.Default.Home),
    EXAMS("এক্সাম", Icons.Default.Assignment),
    SCANNER("স্ক্যান", Icons.Default.DocumentScanner),
    RESULTS("ফলাফল", Icons.Default.Assessment)
}

@Composable
fun OmrMainScaffold(viewModel: OmrViewModel) {
    var currentTab by remember { mutableStateOf(BottomTab.HOME) }
    var editingAnswerKeyExamId by remember { mutableStateOf<Long?>(null) }

    val exams by viewModel.exams.collectAsState()
    val editingExam: ExamEntity? = exams.find { it.id == editingAnswerKeyExamId }

    if (editingExam != null) {
        AnswerKeyScreen(
            exam = editingExam,
            viewModel = viewModel,
            onBack = { editingAnswerKeyExamId = null }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (currentTab) {
            BottomTab.HOME -> DashboardScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding),
                onNavigateTab = { currentTab = it },
                onOpenAnswerKey = { examId -> editingAnswerKeyExamId = examId }
            )

            BottomTab.EXAMS -> ExamsScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding),
                onOpenAnswerKey = { examId -> editingAnswerKeyExamId = examId }
            )

            BottomTab.SCANNER -> ScannerScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding),
                onGoToExams = { currentTab = BottomTab.EXAMS },
                onGoToResults = { currentTab = BottomTab.RESULTS }
            )

            BottomTab.RESULTS -> ResultsScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
