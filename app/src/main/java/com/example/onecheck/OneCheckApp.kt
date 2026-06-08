package com.example.onecheck

import android.app.Application
import com.example.onecheck.data.OneCheckSession

class OneCheckApp : Application() {
    override fun onCreate() {
        super.onCreate()
        OneCheckSession.init(this)
    }
}
