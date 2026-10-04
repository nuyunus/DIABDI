package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Student
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY id ASC")
    fun getAllStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudentById(id: String): Student?

    @Query("SELECT * FROM students WHERE id = :id AND nisn = :nisn LIMIT 1")
    suspend fun getStudentByIdAndNisn(id: String, nisn: String): Student?

    @Query("SELECT * FROM students WHERE nisn = :nisn LIMIT 1")
    suspend fun getStudentByNisn(nisn: String): Student?

    @Query("SELECT * FROM students WHERE id = :searchKey OR nisn = :searchKey LIMIT 1")
    suspend fun findByIdOrNisn(searchKey: String): Student?

    @Query("SELECT * FROM students WHERE unitPendidikan = :unit ORDER BY name ASC")
    fun getStudentsByUnit(unit: String): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE name LIKE '%' || :query || '%' OR id LIKE '%' || :query || '%' OR nisn LIKE '%' || :query || '%' OR className LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchStudents(query: String): Flow<List<Student>>

    @Query("SELECT COUNT(*) FROM students")
    fun getStudentCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM students")
    suspend fun getStudentCountDirect(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<Student>)

    @Update
    suspend fun updateStudent(student: Student)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteStudent(id: String)

    @Query("DELETE FROM students")
    suspend fun deleteAllStudents()
}
