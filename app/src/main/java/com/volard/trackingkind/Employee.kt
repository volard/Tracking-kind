package com.volard.trackingkind

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity
class Employee {
    @PrimaryKey
    var uid = 0

    @ColumnInfo(name = "first_name")
    var firstName: String? = null

    @ColumnInfo(name = "last_name")
    var lastName: String? = null

    @ColumnInfo(name = "last_time_responded")
    var lastTimeResponded: String? = null

    @ColumnInfo(name = "active")
    var active = false //    @ColumnInfo(name = "last_location")
    //    public List<Double> location;
}
