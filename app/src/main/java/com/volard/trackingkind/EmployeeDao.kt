package com.volard.trackingkind

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query


@Dao
interface EmployeeDao {
    @get:Query("SELECT * FROM employee")
    val all: List<Any?>?

    @Query("SELECT * FROM employee WHERE uid IN (:userIds)")
    fun loadAllByIds(userIds: IntArray?): List<Employee?>?

    @Query(
        "SELECT * FROM employee WHERE first_name LIKE :first AND " +
                "last_name LIKE :last LIMIT 1"
    )
    fun findByName(first: String?, last: String?): Employee?

    @Insert
    fun insertAll(vararg employees: Employee?)

    @Delete
    fun delete(employee: Employee?)
}