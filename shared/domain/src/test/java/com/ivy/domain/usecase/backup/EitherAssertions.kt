package com.ivy.domain.usecase.backup

import arrow.core.Either

/** Fails the test with the error, rather than with a cast exception that says nothing. */
internal fun <A, B> Either<A, B>.shouldBeRight(): B = when (this) {
    is Either.Right -> value
    is Either.Left -> error("expected a success but got $value")
}

internal fun <A, B> Either<A, B>.shouldBeLeft(): A = when (this) {
    is Either.Left -> value
    is Either.Right -> error("expected a failure but got $value")
}
