package com.example.logipass.security

object UnlockThrottle {
    fun delayMillis(failedAttempts: Int): Long = when {
        failedAttempts < 5 -> 0
        failedAttempts == 5 -> 30_000
        failedAttempts == 6 -> 60_000
        failedAttempts == 7 -> 300_000
        failedAttempts == 8 -> 900_000
        else -> 3_600_000
    }
}
