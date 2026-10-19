package com.javzzap.laxbrowser.data

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Runs persistence jobs one at a time, in submission order, on a background dispatcher. */
class PersistenceQueue(scope: CoroutineScope, dispatcher: CoroutineDispatcher) {
    private val jobs = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch(dispatcher) {
            for (job in jobs) {
                try {
                    job()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e(TAG, "Persistence job failed", e)
                }
            }
        }
    }

    fun enqueue(job: suspend () -> Unit) {
        jobs.trySend(job)
    }

    /** Suspends until every job submitted before this call has finished. */
    suspend fun awaitIdle() {
        val done = CompletableDeferred<Unit>()
        enqueue { done.complete(Unit) }
        done.await()
    }

    companion object {
        private const val TAG = "PersistenceQueue"
    }
}
