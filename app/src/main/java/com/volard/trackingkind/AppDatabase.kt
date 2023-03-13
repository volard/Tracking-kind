package com.volard.trackingkind

import androidx.room.Database
import androidx.room.RoomDatabase


/**
 * This class holds app's database.
 * That's main access point to to the persisted data
 */
@Database(entities = [Employee::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao?
}