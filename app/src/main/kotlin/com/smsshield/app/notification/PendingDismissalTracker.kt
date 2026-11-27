package com.smsshield.app.notification

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class PendingDismissalTracker {

    private val sequence = AtomicLong(0L)
    private val latestTokenByKey = ConcurrentHashMap<String, Long>()

    fun recordNotification(key: String): Long {
        val token = sequence.incrementAndGet()
        latestTokenByKey[key] = token
        return token
    }

    fun isStillLatest(key: String, token: Long): Boolean {
        return latestTokenByKey[key] == token
    }

    fun clear(key: String, expectedToken: Long? = null) {
        if (expectedToken == null) {
            latestTokenByKey.remove(key)
        } else {
            latestTokenByKey.remove(key, expectedToken)
        }
    }
}
