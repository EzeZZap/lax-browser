package com.javzzap.laxbrowser.engine.gecko

import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Suspends until the result completes. Listeners run on the thread that created the result. */
suspend fun <T> GeckoResult<T>.await(): T? = suspendCancellableCoroutine { continuation ->
    accept(
        { value -> if (continuation.isActive) continuation.resume(value) },
        { error -> if (continuation.isActive) continuation.resumeWithException(error ?: IllegalStateException("GeckoResult failed")) },
    )
}
