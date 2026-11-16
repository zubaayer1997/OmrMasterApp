package com.omrmaster.app.ui.exams

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun CreateExamDialog(
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        subject: String,
        className: String,
        questionCount: Int,
        setCount: Int,
        negativeMarking: Boolean,
        negativePenalty: Double
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var questionCountText by remember { mutableStateOf("20") }
    var setCount by remember { mutableIntStateOf(1) }
    var negativeMarking by remember { mutableStateOf(false) }
    var penaltyText by remember { mutableStateOf("0.25") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন এক্সাম তৈরি করুন", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("এক্সামের নাম * (যেমন: মডেল টেস্ট - ০১)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = className,
                        onValueChange = { className = it },
                        label = { Text("শ্রেণি") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("বিষয়") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = questionCountText,
                    onValueChange = { questionCountText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("মোট প্রশ্ন / পূর্ণমান (১ - ১০০)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("প্রশ্নের সেট সংখ্যা:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "১ সেট", 2 to "২ সেট", 4 to "৪ সেট").forEach { (count, label) ->
                        FilterChip(
                            selected = setCount == count,
                            onClick = { setCount = count },
                            label = { Text(label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("নেগেটিভ মার্কিং (হ্যাঁ / না)")
                    Switch(
                        checked = negativeMarking,
                        onCheckedChange = { negativeMarking = it }
                    )
                }

                if (negativeMarking) {
                    OutlinedTextField(
                        value = penaltyText,
                        onValueChange = { penaltyText = it },
                        label = { Text("প্রতি ভুল উত্তরের কর্তন (যেমন: 0.25)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qCount = questionCountText.toIntOrNull() ?: 0
                    val penalty = penaltyText.toDoubleOrNull() ?: 0.25
                    when {
                        name.isBlank() -> errorMsg = "অনুগ্রহ করে এক্সামের নাম লিখুন।"
                        qCount !in 1..100 -> errorMsg = "প্রশ্নের সংখ্যা ১ থেকে ১০০-এর মধ্যে হতে হবে।"
                        else -> onCreate(
                            name,
                            subject,
                            className,
                            qCount,
                            setCount,
                            negativeMarking,
                            penalty
                        )
                    }
                }
            ) {
                Text("তৈরি করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}
