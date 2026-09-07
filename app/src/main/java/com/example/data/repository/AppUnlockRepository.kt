package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.entities.AppUnlockSessionEntity
import com.example.data.entities.ControlledAppModes
import com.example.data.entities.CreditTransactionEntity
import com.example.data.entities.CreditTypes
import kotlinx.coroutines.flow.Flow

/** Outcome of trying to buy access to a controlled app. */
sealed interface UnlockResult {
    data class Success(val session: AppUnlockSessionEntity) : UnlockResult
    data class InsufficientCredits(val required: Int, val available: Int) : UnlockResult
    data class AlreadyUnlocked(val session: AppUnlockSessionEntity) : UnlockResult
    data class Rejected(val reason: String) : UnlockResult
}

class AppUnlockRepository(private val database: AppDatabase) {

    private val sessionDao = database.appUnlockSessionDao()
    private val creditDao = database.creditTransactionDao()
    private val controlledAppDao = database.controlledAppDao()

    val activeSessions: Flow<List<AppUnlockSessionEntity>> = sessionDao.getActiveSessions()

    fun recentSessions(limit: Int = 50): Flow<List<AppUnlockSessionEntity>> =
        sessionDao.getRecentSessions(limit)

    suspend fun getActiveSession(packageName: String): AppUnlockSessionEntity? =
        sessionDao.getActiveSessionFor(packageName)

    suspend fun getActiveSessions(): List<AppUnlockSessionEntity> = sessionDao.getActiveSessionsSync()

    /**
     * Debit [minutes] credits and open an access window, atomically. One credit buys one
     * minute of foreground use, matching the "min" unit shown on the wallet.
     *
     * The whole thing runs in a single DB transaction so the wallet can never be charged
     * without a session existing, and vice versa.
     */
    suspend fun unlock(packageName: String, minutes: Int): UnlockResult {
        if (minutes <= 0) return UnlockResult.Rejected("Choose at least 1 minute")

        return database.withTransaction {
            val controlledApp = controlledAppDao.getAppByPackage(packageName)
                ?: return@withTransaction UnlockResult.Rejected("This app is not under control")

            if (!controlledApp.isSelected) {
                return@withTransaction UnlockResult.Rejected("${controlledApp.appName} is not a controlled app")
            }
            if (controlledApp.mode == ControlledAppModes.ALWAYS_BLOCKED) {
                return@withTransaction UnlockResult.Rejected(
                    "${controlledApp.appName} is always blocked and cannot be unlocked with credits"
                )
            }

            sessionDao.getActiveSessionFor(packageName)?.let {
                return@withTransaction UnlockResult.AlreadyUnlocked(it)
            }

            val balance = creditDao.getLatestBalanceSync() ?: 0
            if (balance < minutes) {
                return@withTransaction UnlockResult.InsufficientCredits(minutes, balance)
            }

            val now = System.currentTimeMillis()
            creditDao.insertTransaction(
                CreditTransactionEntity(
                    amount = -minutes,
                    type = CreditTypes.APP_UNLOCK,
                    reason = "Unlocked ${controlledApp.appName} for $minutes min",
                    appPackage = packageName,
                    timestamp = now,
                    balanceAfterTransaction = balance - minutes
                )
            )

            val session = AppUnlockSessionEntity(
                packageName = packageName,
                appName = controlledApp.appName,
                creditsSpent = minutes,
                grantedSeconds = minutes * 60,
                startedAt = now,
                lastTickAt = now
            )
            val id = sessionDao.insert(session)
            UnlockResult.Success(session.copy(id = id.toInt()))
        }
    }

    /** Persist consumed foreground time for a live session. */
    suspend fun saveProgress(sessionId: Int, consumedSeconds: Int) {
        sessionDao.updateProgress(sessionId, consumedSeconds, System.currentTimeMillis())
    }

    /**
     * Close a session. Any unused time is returned to the wallet so the ledger stays
     * balanced when the window is cut short by something other than the user using it up.
     */
    suspend fun endSession(sessionId: Int, consumedSeconds: Int, reason: String, refundUnused: Boolean) {
        database.withTransaction {
            // Look up by id: an exhausted session no longer matches the "active" query.
            val session = sessionDao.getById(sessionId) ?: return@withTransaction
            if (session.endedAt != null) return@withTransaction
            val consumed = consumedSeconds.coerceIn(0, session.grantedSeconds)
            val now = System.currentTimeMillis()

            sessionDao.updateProgress(sessionId, consumed, now)
            sessionDao.endSession(sessionId, now, reason)

            if (refundUnused) {
                // Refund whole unused minutes only; partial minutes are treated as spent.
                val refund = (session.grantedSeconds - consumed) / 60
                if (refund > 0) {
                    val balance = creditDao.getLatestBalanceSync() ?: 0
                    creditDao.insertTransaction(
                        CreditTransactionEntity(
                            amount = refund,
                            type = CreditTypes.REVERSAL,
                            reason = "Refund of unused time on ${session.appName}",
                            appPackage = session.packageName,
                            timestamp = now,
                            balanceAfterTransaction = balance + refund
                        )
                    )
                }
            }
        }
    }

    /** Close every live session for an app, e.g. when it is deselected or set to always-blocked. */
    suspend fun endSessionsFor(packageName: String, reason: String, refundUnused: Boolean) {
        val live = sessionDao.getActiveSessionsSync().filter { it.packageName == packageName }
        live.forEach { endSession(it.id, it.consumedSeconds, reason, refundUnused) }
        // Belt and braces: close anything the filter above missed (e.g. already exhausted rows).
        sessionDao.endSessionsFor(packageName, System.currentTimeMillis(), reason)
    }
}
