package com.omrmaster.app.data.repository

import com.omrmaster.app.data.local.ExamDao
import com.omrmaster.app.data.local.ExamEntity
import com.omrmaster.app.data.local.StudentResultDao
import com.omrmaster.app.data.local.StudentResultEntity
import kotlinx.coroutines.flow.Flow

class OmrRepository(
    private val examDao: ExamDao,
    private val resultDao: StudentResultDao
) {
    val allExams: Flow<List<ExamEntity>> = examDao.observeAllExams()
    val allResults: Flow<List<StudentResultEntity>> = resultDao.observeAllResults()
    val totalResultsCount: Flow<Int> = resultDao.observeTotalResultCount()

    fun observeResultsForExam(examId: Long): Flow<List<StudentResultEntity>> =
        resultDao.observeResultsByExam(examId)

    suspend fun getExamById(id: Long): ExamEntity? = examDao.getExamById(id)

    suspend fun createExam(exam: ExamEntity): Long = examDao.insertExam(exam)

    suspend fun updateExam(exam: ExamEntity) = examDao.updateExam(exam)

    suspend fun deleteExam(exam: ExamEntity) = examDao.deleteExam(exam)

    suspend fun saveResult(result: StudentResultEntity): Long = resultDao.insertResult(result)

    suspend fun updateResult(result: StudentResultEntity) = resultDao.updateResult(result)

    suspend fun deleteResult(result: StudentResultEntity) = resultDao.deleteResult(result)
}
