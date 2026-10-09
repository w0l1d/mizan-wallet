package com.ivy.domain.usecase.backup

/**
 * The few facts about this device and this build that a snapshot has to record so a future
 * version of the app can tell whether it is able to read it.
 *
 * It is an interface so the domain layer never reaches for `Build.MODEL` or `BuildConfig`; the
 * app module binds the real one.
 */
interface BackupEnvironment {
    /** Goes into the snapshot's name, so a folder shared between devices stays legible. */
    val deviceName: String

    /** Recorded for support, never compared. */
    val appVersion: String

    /** Compared on restore: a snapshot written by a newer schema is refused, not guessed at. */
    val dataSchemaVersion: Int
}
