package com.ivy.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Timeout for `runTest` in property-based tests.
 *
 * Property tests run many generated cases per assertion, so they take far longer
 * than a regular unit test. `runTest` defaults to 60s, which shared CI runners
 * overshoot under load and report as `UncompletedCoroutinesError`.
 */
val PropertyTestTimeout: Duration = 5.minutes
