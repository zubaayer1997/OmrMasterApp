package com.omrmaster.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentResultDao {
    @Query("SELECT * FROM student_results WHERE examId = :examId ORDER BY obtainedMarks DESC, rollNumber ASC")
    fun observeResultsByExam(examId: Long): Flow<List<StudentResultEntity>>

    @Query("SELECT * FROM student_results ORDER BY scannedAt DESC")
    fun observeAllResults(): Flow<List<StudentResultEntity>>

    @Query("SELECT COUNT(*) FROM student_results")
    fun observeTotalResultCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: StudentResultEntity): Long

    @Update
    suspend fun updateResult(result: StudentResultEntity)

    @Delete
    suspend fun deleteResult(result: StudentResultEntity)
}
