package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AttendanceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE date = :date AND sessionName = :session ORDER BY timestamp DESC")
    fun getRecordsByDateAndSession(date: String, session: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE date = :date ORDER BY timestamp DESC")
    fun getRecordsByDate(date: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE studentId = :studentId ORDER BY timestamp DESC")
    fun getRecordsByStudentId(studentId: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE studentId = :studentId ORDER BY timestamp DESC")
    suspend fun getRecordsByStudentIdDirect(studentId: String): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE studentId = :studentId AND sessionName = :session AND date = :date LIMIT 1")
    suspend fun getRecordForStudentSession(studentId: String, session: String, date: String): AttendanceRecord?

    @Query("SELECT * FROM attendance_records WHERE unitPendidikan = :unit AND date = :date ORDER BY timestamp DESC")
    fun getRecordsByUnitAndDate(unit: String, date: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedRecords(): List<AttendanceRecord>

    @Query("SELECT COUNT(*) FROM attendance_records WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM attendance_records WHERE date = :date AND sessionName = :session")
    fun getCountForDateAndSession(date: String, session: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: AttendanceRecord): Long

    @Update
    suspend fun updateRecord(record: AttendanceRecord)

    @Query("UPDATE attendance_records SET isSynced = 1, syncError = null WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query("UPDATE attendance_records SET isSynced = 0, syncError = :error WHERE id = :id")
    suspend fun markSyncFailed(id: Long, error: String)

    @Query("DELETE FROM attendance_records WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query("DELETE FROM attendance_records")
    suspend fun clearAll()
}
