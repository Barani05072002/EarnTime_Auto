package com.example.data.entities

/**
 * String values persisted in [ControlledAppEntity.mode]. Kept as the original literals so
 * existing rows keep their meaning across the upgrade.
 */
object ControlledAppModes {
    /** Not controlled — the app is ignored entirely. */
    const val FREE = "FREE"

    /** Locked by default; opens only while a paid unlock session is running. */
    const val REWARD = "REWARD"

    /** Locked always. Credits cannot buy access. */
    const val ALWAYS_BLOCKED = "ALWAYS_BLOCKED"
}

/** Values persisted in [CreditTransactionEntity.type]. */
object CreditTypes {
    const val TASK_REWARD = "TASK_REWARD"
    const val HABIT_REWARD = "HABIT_REWARD"

    /** Legacy per-minute drain kept so historical rows still render. */
    const val APP_USAGE = "APP_USAGE"

    /** Up-front purchase of an access window. */
    const val APP_UNLOCK = "APP_UNLOCK"
    const val EXPIRATION = "EXPIRATION"
    const val ADMIN_ADJUSTMENT = "ADMIN_ADJUSTMENT"
    const val REVERSAL = "REVERSAL"
}

/** Values persisted in [AppUnlockSessionEntity.endReason]. */
object UnlockEndReasons {
    /** The purchased time ran out. */
    const val EXHAUSTED = "EXHAUSTED"

    /** The user ended the session early from the app. */
    const val CANCELLED = "CANCELLED"

    /** The app was unchecked in the controlled-apps list. */
    const val DESELECTED = "DESELECTED"

    /** The app was switched to a different control mode. */
    const val MODE_CHANGED = "MODE_CHANGED"
}
