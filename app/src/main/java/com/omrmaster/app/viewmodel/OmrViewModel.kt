package com.omrmaster.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omrmaster.app.data.local.ExamEntity
import com.omrmaster.app.data.local.StudentResultEntity
import com.omrmaster.app.data.repository.OmrRepository
import com.omrmaster.app.omr.EvaluatedOmrOutput
import com.omrmaster.app.omr.OmrImageProcessor
import com.omrmaster.app.omr.OmrPdfGenerator
import com.omrmaster.app.omr.ReportExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BatchScanState(
    val isProcessing: Boolean = false,
    val totalCount: Int = 0,
    val processedCount: Int = 0,
    val lastOutput: EvaluatedOmrOutput? = null,
    val errorMessages: List<String> = emptyList(),
    val statusBanner: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class OmrViewModel(
    application: Application,
    private val repository: OmrRepository
) : AndroidViewModel(application) {

    val exams: StateFlow<List<ExamEntity>> = repository.allExams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allResults: StateFlow<List<StudentResultEntity>> = repository.allResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedExamId = MutableStateFlow<Long?>(null)
    val selectedExamId: StateFlow<Long?> = _selectedExamId.asStateFlow()

    val selectedExam: StateFlow<ExamEntity?> = combine(exams, _selectedExamId) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val resultsForSelectedExam: StateFlow<List<StudentResultEntity>> = selectedExam
        .flatMapLatest { exam ->
            if (exam == null) flowOf(emptyList())
            else repository.observeResultsForExam(exam.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scanState = MutableStateFlow(BatchScanState())
    val scanState: StateFlow<BatchScanState> = _scanState.asStateFlow()

    init {
        viewModelScope.launch {
            exams.collect { list ->
                if (_selectedExamId.value == null && list.isNotEmpty()) {
                    _selectedExamId.value = list.first().id
                }
            }
        }
    }

    fun selectExam(examId: Long) {
        _selectedExamId.value = examId
    }

    fun createExam(
        name: String,
        subject: String,
        className: String,
        questionCount: Int,
        setCount: Int,
        negativeMarking: Boolean,
        negativePenalty: Double,
        onCreated: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val id = repository.createExam(
                ExamEntity(
                    name = name.trim(),
                    subject = subject.trim(),
                    className = className.trim(),
                    questionCount = questionCount.coerceIn(1, 100),
                    setCount = setCount,
                    negativeMarking = negativeMarking,
                    negativePenalty = negativePenalty
                )
            )
            _selectedExamId.value = id
            onCreated(id)
        }
    }

    fun deleteExam(exam: ExamEntity) {
        viewModelScope.launch {
            repository.deleteExam(exam)
        }
    }

    fun saveAnswerKey(exam: ExamEntity, setCode: String, answers: List<Int>) {
        viewModelScope.launch {
            val updated = exam.withUpdatedAnswerKey(setCode, answers)
            repository.updateExam(updated)
        }
    }

    fun scanMasterKeyFromUri(
        uri: Uri,
        exam: ExamEntity,
        setCode: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val bmp = loadBitmapFromUri(uri)
                    ?: return@withContext null to "ছবিটি লোড করা যায়নি।"
                val pair = OmrImageProcessor.extractMasterAnswerKey(bmp, exam.questionCount)
                bmp.recycle()
                pair
            }
            val detected = result.first
            if (detected != null) {
                saveAnswerKey(exam, setCode, detected)
                onComplete(true, "সেট '$setCode'-এর উত্তরপত্র সফলভাবে স্ক্যান ও সেভ হয়েছে!")
            } else {
                onComplete(false, result.second ?: "মাস্টার কী পড়া সম্ভব হয়নি।")
            }
        }
    }

    fun generatePdf(
        exam: ExamEntity,
        paperSaverMode: Boolean,
        onUriReady: (Uri) -> Unit
    ) {
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                OmrPdfGenerator.generateOmrPdf(getApplication(), exam, paperSaverMode)
            }
            onUriReady(uri)
        }
    }

    fun evaluateSingleCameraBitmap(bitmap: Bitmap, exam: ExamEntity) {
        viewModelScope.launch {
            _scanState.value = BatchScanState(
                isProcessing = true,
                totalCount = 1,
                processedCount = 0,
                statusBanner = "OMR শীট বিশ্লেষণ করা হচ্ছে..."
            )
            val output = withContext(Dispatchers.Default) {
                OmrImageProcessor.evaluateStudentSheet(getApplication(), bitmap, exam, saveToGallery = true)
            }
            if (output.success) {
                repository.saveResult(
                    StudentResultEntity(
                        examId = exam.id,
                        rollNumber = output.rollNumber,
                        section = output.section,
                        setCode = output.setCode,
                        studentAnswersCsv = StudentResultEntity.encodeAnswers(output.answers),
                        correctCount = output.correctCount,
                        wrongCount = output.wrongCount,
                        multipleCount = output.multipleCount,
                        skippedCount = output.skippedCount,
                        obtainedMarks = output.obtainedMarks,
                        annotatedImagePath = output.savedImagePath
                    )
                )
                _scanState.value = BatchScanState(
                    isProcessing = false,
                    totalCount = 1,
                    processedCount = 1,
                    lastOutput = output,
                    statusBanner = "সফলভাবে মূল্যায়ন সম্পন্ন ও গ্যালারিতে সেভ হয়েছে!"
                )
            } else {
                _scanState.value = BatchScanState(
                    isProcessing = false,
                    totalCount = 1,
                    processedCount = 1,
                    errorMessages = listOf(output.errorMessage ?: "স্ক্যান ব্যর্থ হয়েছে।"),
                    statusBanner = output.errorMessage
                )
            }
        }
    }

    fun evaluateBulkGalleryUris(uris: List<Uri>, exam: ExamEntity) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _scanState.value = BatchScanState(
                isProcessing = true,
                totalCount = uris.size,
                processedCount = 0,
                statusBanner = "${uris.size}টি ছবি কিউতে প্রসেস হচ্ছে..."
            )

            val errors = mutableListOf<String>()
            var lastSuccessOutput: EvaluatedOmrOutput? = null

            uris.forEachIndexed { index, uri ->
                val output = withContext(Dispatchers.Default) {
                    val bmp = loadBitmapFromUri(uri)
                    if (bmp == null) {
                        EvaluatedOmrOutput(false, "ছবি #${index + 1} পড়া যায়নি।")
                    } else {
                        val res = OmrImageProcessor.evaluateStudentSheet(
                            getApplication(),
                            bmp,
                            exam,
                            saveToGallery = true
                        )
                        bmp.recycle()
                        res
                    }
                }

                if (output.success) {
                    lastSuccessOutput = output
                    repository.saveResult(
                        StudentResultEntity(
                            examId = exam.id,
                            rollNumber = output.rollNumber,
                            section = output.section,
                            setCode = output.setCode,
                            studentAnswersCsv = StudentResultEntity.encodeAnswers(output.answers),
                            correctCount = output.correctCount,
                            wrongCount = output.wrongCount,
                            multipleCount = output.multipleCount,
                            skippedCount = output.skippedCount,
                            obtainedMarks = output.obtainedMarks,
                            annotatedImagePath = output.savedImagePath
                        )
                    )
                } else {
                    errors.add("ছবি #${index + 1}: ${output.errorMessage ?: "ব্যর্থ"}")
                }

                _scanState.value = _scanState.value.copy(
                    processedCount = index + 1,
                    lastOutput = lastSuccessOutput,
                    errorMessages = errors.toList(),
                    statusBanner = "প্রসেস সম্পন্ন: ${index + 1} / ${uris.size}"
                )
            }

            _scanState.value = _scanState.value.copy(
                isProcessing = false,
                statusBanner = "বাল্ক মূল্যায়ন শেষ! সফল: ${uris.size - errors.size}টি, ব্যর্থ: ${errors.size}টি"
            )
        }
    }

    fun overrideStudentAnswer(
        result: StudentResultEntity,
        exam: ExamEntity,
        questionIndex: Int,
        newAnswerCode: Int
    ) {
        viewModelScope.launch {
            val answers = result.getAnswersList(exam.questionCount).toMutableList()
            if (questionIndex !in answers.indices) return@launch
            answers[questionIndex] = newAnswerCode

            val answerKey = exam.getAnswerKeyMap()[result.setCode] ?: List(exam.questionCount) { -1 }
            var correct = 0
            var wrong = 0
            var multiple = 0
            var skipped = 0

            answers.forEachIndexed { idx, ans ->
                val key = answerKey.getOrElse(idx) { -1 }
                when (ans) {
                    StudentResultEntity.ANSWER_MULTIPLE -> multiple++
                    StudentResultEntity.ANSWER_SKIPPED -> skipped++
                    in 0..3 -> {
                        if (key in 0..3) {
                            if (ans == key) correct++ else wrong++
                        }
                    }
                }
            }

            val raw = if (exam.negativeMarking) {
                correct - (wrong * exam.negativePenalty)
            } else {
                correct.toDouble()
            }
            val finalMarks = Math.round(raw * 100.0) / 100.0

            repository.updateResult(
                result.copy(
                    studentAnswersCsv = StudentResultEntity.encodeAnswers(answers),
                    correctCount = correct,
                    wrongCount = wrong,
                    multipleCount = multiple,
                    skippedCount = skipped,
                    obtainedMarks = finalMarks
                )
            )
        }
    }

    fun deleteResult(result: StudentResultEntity) {
        viewModelScope.launch {
            repository.deleteResult(result)
        }
    }

    fun exportCsv(exam: ExamEntity, results: List<StudentResultEntity>, onUri: (Uri) -> Unit) {
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                ReportExporter.exportCsvReport(getApplication(), exam, results)
            }
            onUri(uri)
        }
    }

    fun exportPdf(exam: ExamEntity, results: List<StudentResultEntity>, onUri: (Uri) -> Unit) {
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                ReportExporter.exportPdfMeritReport(getApplication(), exam, results)
            }
            onUri(uri)
        }
    }

    fun clearScanStatus() {
        _scanState.value = BatchScanState()
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val resolver = getApplication<Application>().contentResolver
            val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val src = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(resolver, uri)
            }
            // Downscale ultra-large camera photos cleanly while keeping crisp detail
            val maxDim = 2200
            if (raw.width > maxDim || raw.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / raw.width, maxDim.toFloat() / raw.height)
                val scaled = Bitmap.createScaledBitmap(
                    raw,
                    (raw.width * ratio).toInt(),
                    (raw.height * ratio).toInt(),
                    true
                )
                if (scaled != raw) raw.recycle()
                scaled
            } else {
                raw
            }
        } catch (_: Exception) {
            null
        }
    }
}

class OmrViewModelFactory(
    private val application: Application,
    private val repository: OmrRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OmrViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OmrViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
