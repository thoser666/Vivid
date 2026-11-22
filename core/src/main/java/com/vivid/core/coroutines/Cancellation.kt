package com.vivid.core.coroutines

import java.util.concurrent.CancellationException

/** Keep coroutine cancellation out of recoverable error handling in feature scopes. */
fun Exception.rethrowCancellation() {
    if (this is CancellationException) throw this
}
