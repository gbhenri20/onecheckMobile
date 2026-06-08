package com.example.onecheck.ui

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CancellationException

fun Context.showError(throwable: Throwable, screen: ErrorScreen, duration: Int = Toast.LENGTH_LONG) {
    if (throwable is CancellationException) return
    Toast.makeText(this, throwable.userMessage(this, screen), duration).show()
}

fun Context.showMessage(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}
