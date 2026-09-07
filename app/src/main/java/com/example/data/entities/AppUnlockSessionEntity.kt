package com.example.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A paid access window for one controlled app.
 *
 * The user spends reward credits up-front to create a session; the app stays locked
 * unless a session for it is currently active (i.e. [endedAt] is null and there is
 * budget left). Time is consumed only while the app is actually in the foreground,
 * so leaving the app pauses the meter instead of wasting the purchase.
 */
@Entity(
    tableName = "app_unlock_sessions",
    indices = [Index("packageName"), Index("endedAt")]
)
data class AppUnlockSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val appName: String,
    /** Credits debited from the wallet to open this session. */
    val creditsSpent: Int,
    /** Foreground seconds purchased. */
    val grantedSeconds: Int,
    /** Foreground seconds already used. Persisted periodically so a process kill cannot refund time. */
    val consumedSeconds: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
    /** Last time the meter was persisted; used to recover elapsed time across restarts. */
    val lastTickAt: Long = System.currentTimeMillis(),
    /** Null while the session is live. Set when the budget runs out or the user/app config ends it. */
    val endedAt: Long? = null,
    /** EXHAUSTED, CANCELLED, DESELECTED, MODE_CHANGED */
    val endReason: String? = null
) {
    val isActive: Boolean get() = endedAt == null && consumedSeconds < grantedSeconds
    val remainingSeconds: Int get() = (grantedSeconds - consumedSeconds).coerceAtLeast(0)
}
