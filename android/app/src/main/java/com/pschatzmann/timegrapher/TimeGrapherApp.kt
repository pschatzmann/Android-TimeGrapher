package com.pschatzmann.timegrapher

import android.app.Application

class TimeGrapherApp : Application() {
    val engine: TimeGrapherEngine by lazy { TimeGrapherEngine(this) }
}
