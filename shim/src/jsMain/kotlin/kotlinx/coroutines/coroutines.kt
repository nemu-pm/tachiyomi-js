package kotlinx.coroutines

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

/*
 * kotlinx.coroutines for the sandbox. Every suspend function the shim offers completes without
 * suspending (HTTP goes through a synchronous bridge), so `launch`, `async`, `runBlocking` and
 * `withContext` all run their body to completion on the calling thread. A coroutine that does
 * suspend for real (a source awaiting something the host never resumes) surfaces as an
 * IllegalStateException from `runBlocking` or stays pending in a `Deferred`.
 */

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
annotation class DelicateCoroutinesApi

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
annotation class ExperimentalCoroutinesApi

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
annotation class InternalCoroutinesApi

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
annotation class FlowPreview

typealias CancellationException = kotlin.coroutines.cancellation.CancellationException

class TimeoutCancellationException(message: String) : CancellationException(message)

// Scopes

interface CoroutineScope {
    val coroutineContext: CoroutineContext
}

private class ContextScope(override val coroutineContext: CoroutineContext) : CoroutineScope

@Suppress("FunctionName")
fun CoroutineScope(context: CoroutineContext): CoroutineScope =
    ContextScope(if (context[Job] != null) context else context + Job())

@Suppress("FunctionName")
fun MainScope(): CoroutineScope = ContextScope(SupervisorJob() + Dispatchers.Main)

@DelicateCoroutinesApi
object GlobalScope : CoroutineScope {
    override val coroutineContext: CoroutineContext get() = EmptyCoroutineContext
}

val CoroutineScope.isActive: Boolean get() = coroutineContext[Job]?.isActive ?: true

fun CoroutineScope.cancel(cause: CancellationException? = null) {
    coroutineContext[Job]?.cancel(cause)
}

fun CoroutineScope.cancel(message: String, cause: Throwable? = null) = cancel(CancellationException(message, cause))

fun CoroutineScope.ensureActive() {
    coroutineContext[Job]?.let { if (!it.isActive) throw CancellationException("Job was cancelled") }
}

// Dispatchers

open class CoroutineDispatcher : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CoroutineDispatcher>

    open fun limitedParallelism(parallelism: Int, name: String? = null): CoroutineDispatcher = this

    open fun isDispatchNeeded(context: CoroutineContext): Boolean = false

    override fun toString(): String = "Dispatcher"
}

object Dispatchers {
    val Default: CoroutineDispatcher = CoroutineDispatcher()
    val Main: MainCoroutineDispatcher = MainCoroutineDispatcher()
    val IO: CoroutineDispatcher = CoroutineDispatcher()
    val Unconfined: CoroutineDispatcher = CoroutineDispatcher()
}

class MainCoroutineDispatcher : CoroutineDispatcher() {
    val immediate: MainCoroutineDispatcher get() = this
}

// Jobs

interface Job : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<Job>

    val isActive: Boolean
    val isCompleted: Boolean
    val isCancelled: Boolean
    val children: Sequence<Job>

    fun start(): Boolean
    fun cancel(cause: CancellationException? = null)
    suspend fun join()
    fun invokeOnCompletion(handler: (cause: Throwable?) -> Unit): DisposableHandle
}

fun Job.cancel(message: String, cause: Throwable? = null) = cancel(CancellationException(message, cause))

fun interface DisposableHandle {
    fun dispose()
}

interface CompletableJob : Job {
    fun complete(): Boolean
    fun completeExceptionally(exception: Throwable): Boolean
}

open class JobImpl(parent: Job? = null) : CompletableJob {
    override val key: CoroutineContext.Key<*> get() = Job

    private var state = State.ACTIVE
    private var cancelCause: CancellationException? = null
    private val childJobs = mutableListOf<Job>()
    private val handlers = mutableListOf<(Throwable?) -> Unit>()

    protected enum class State { ACTIVE, COMPLETED, CANCELLED }

    init {
        (parent as? JobImpl)?.childJobs?.add(this)
    }

    override val isActive: Boolean get() = state == State.ACTIVE
    override val isCompleted: Boolean get() = state != State.ACTIVE
    override val isCancelled: Boolean get() = state == State.CANCELLED
    override val children: Sequence<Job> get() = childJobs.asSequence()

    override fun start(): Boolean = false

    override fun cancel(cause: CancellationException?) {
        if (state != State.ACTIVE) return
        state = State.CANCELLED
        cancelCause = cause ?: CancellationException("Job was cancelled")
        childJobs.forEach { it.cancel(cause) }
        finish(cancelCause)
    }

    fun cancelChildren(cause: CancellationException? = null) {
        childJobs.forEach { it.cancel(cause) }
    }

    override suspend fun join() {}

    override fun invokeOnCompletion(handler: (cause: Throwable?) -> Unit): DisposableHandle {
        if (state != State.ACTIVE) handler(cancelCause) else handlers += handler
        return DisposableHandle { handlers -= handler }
    }

    override fun complete(): Boolean {
        if (state != State.ACTIVE) return false
        state = State.COMPLETED
        finish(null)
        return true
    }

    override fun completeExceptionally(exception: Throwable): Boolean {
        if (state != State.ACTIVE) return false
        state = State.CANCELLED
        cancelCause = exception as? CancellationException ?: CancellationException(exception.message, exception)
        finish(exception)
        return true
    }

    private fun finish(cause: Throwable?) {
        val list = handlers.toList()
        handlers.clear()
        list.forEach { it(cause) }
    }
}

@Suppress("FunctionName")
fun Job(parent: Job? = null): CompletableJob = JobImpl(parent)

@Suppress("FunctionName")
fun SupervisorJob(parent: Job? = null): CompletableJob = JobImpl(parent)

fun Job.cancelChildren(cause: CancellationException? = null) {
    (this as? JobImpl)?.cancelChildren(cause)
}

fun CoroutineContext.cancelChildren(cause: CancellationException? = null) {
    this[Job]?.cancelChildren(cause)
}

val CoroutineContext.isActive: Boolean get() = this[Job]?.isActive ?: true

fun CoroutineContext.ensureActive() {
    this[Job]?.let { if (!it.isActive) throw CancellationException("Job was cancelled") }
}

fun CoroutineContext.cancel(cause: CancellationException? = null) {
    this[Job]?.cancel(cause)
}

val CoroutineContext.job: Job get() = this[Job] ?: error("Current context doesn't contain Job in it: $this")

// Deferred

interface Deferred<out T> : Job {
    suspend fun await(): T
    fun getCompleted(): T
    fun getCompletionExceptionOrNull(): Throwable?
}

interface CompletableDeferred<T> : Deferred<T> {
    fun complete(value: T): Boolean
    fun completeExceptionally(exception: Throwable): Boolean
}

private class DeferredImpl<T>(parent: Job?) : JobImpl(parent), CompletableDeferred<T> {
    private var result: Result<T>? = null

    override fun complete(value: T): Boolean {
        if (result != null) return false
        result = Result.success(value)
        return complete()
    }

    override fun completeExceptionally(exception: Throwable): Boolean {
        if (result != null) return false
        result = Result.failure(exception)
        return super.completeExceptionally(exception)
    }

    override suspend fun await(): T = getCompleted()

    override fun getCompleted(): T {
        val r = result ?: throw IllegalStateException("Deferred has not completed; the sandbox cannot wait for it")
        return r.getOrThrow()
    }

    override fun getCompletionExceptionOrNull(): Throwable? = result?.exceptionOrNull()

    override fun cancel(cause: CancellationException?) {
        if (result == null) result = Result.failure(cause ?: CancellationException("Deferred was cancelled"))
        super.cancel(cause)
    }
}

@Suppress("FunctionName")
fun <T> CompletableDeferred(parent: Job? = null): CompletableDeferred<T> = DeferredImpl(parent)

@Suppress("FunctionName")
fun <T> CompletableDeferred(value: T): CompletableDeferred<T> = DeferredImpl<T>(null).apply { complete(value) }

suspend fun <T> Collection<Deferred<T>>.awaitAll(): List<T> = map { it.await() }

suspend fun <T> awaitAll(vararg deferreds: Deferred<T>): List<T> = deferreds.map { it.await() }

suspend fun Collection<Job>.joinAll() = forEach { it.join() }

suspend fun joinAll(vararg jobs: Job) = jobs.forEach { it.join() }

// Builders

enum class CoroutineStart { DEFAULT, LAZY, ATOMIC, UNDISPATCHED }

class CoroutineName(val name: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CoroutineName>

    override fun toString(): String = "CoroutineName($name)"
}

fun interface CoroutineExceptionHandler : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CoroutineExceptionHandler>

    override val key: CoroutineContext.Key<*> get() = Key

    fun handleException(context: CoroutineContext, exception: Throwable)
}

@Suppress("FunctionName")
fun CoroutineExceptionHandler(handler: (CoroutineContext, Throwable) -> Unit): CoroutineExceptionHandler = object : CoroutineExceptionHandler {
    override fun handleException(context: CoroutineContext, exception: Throwable) = handler(context, exception)
}

private fun CoroutineContext.handleUncaught(exception: Throwable) {
    if (exception is CancellationException) return
    val handler = this[CoroutineExceptionHandler]
    if (handler != null) handler.handleException(this, exception) else console.error("Uncaught coroutine exception", exception)
}

/** Runs [block] now; the returned job is complete unless the body suspended for real. */
fun CoroutineScope.launch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
): Job {
    val combined = coroutineContext + context
    val job = JobImpl(combined[Job])
    val scope = ContextScope(combined + job)
    block.startCoroutine(
        scope,
        Continuation(scope.coroutineContext) { result ->
            result.fold({ job.complete() }, { e ->
                job.completeExceptionally(e)
                combined.handleUncaught(e)
            })
        },
    )
    return job
}

fun <T> CoroutineScope.async(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T,
): Deferred<T> {
    val combined = coroutineContext + context
    val deferred = DeferredImpl<T>(combined[Job])
    val scope = ContextScope(combined + deferred)
    block.startCoroutine(
        scope,
        Continuation(scope.coroutineContext) { result ->
            result.fold({ deferred.complete(it) }, { deferred.completeExceptionally(it) })
        },
    )
    return deferred
}

/** Runs [block] to completion synchronously; throws if it suspends on something that never resumes. */
fun <T> runBlocking(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> T): T {
    var outcome: Result<T>? = null
    val scope = ContextScope(context + (context[Job] ?: JobImpl()))
    block.startCoroutine(scope, Continuation(scope.coroutineContext) { outcome = it })
    val result = outcome ?: throw IllegalStateException("runBlocking cannot wait for async operations in the sandbox")
    return result.getOrThrow()
}

suspend fun <T> withContext(context: CoroutineContext, block: suspend CoroutineScope.() -> T): T =
    block(ContextScope(currentContext() + context))

suspend fun <R> coroutineScope(block: suspend CoroutineScope.() -> R): R = block(ContextScope(currentContext()))

suspend fun <R> supervisorScope(block: suspend CoroutineScope.() -> R): R = block(ContextScope(currentContext()))

suspend fun <T> withTimeout(timeMillis: Long, block: suspend CoroutineScope.() -> T): T = block(ContextScope(currentContext()))

suspend fun <T> withTimeout(timeout: kotlin.time.Duration, block: suspend CoroutineScope.() -> T): T = withTimeout(timeout.inWholeMilliseconds, block)

suspend fun <T> withTimeoutOrNull(timeMillis: Long, block: suspend CoroutineScope.() -> T): T? = block(ContextScope(currentContext()))

suspend fun <T> withTimeoutOrNull(timeout: kotlin.time.Duration, block: suspend CoroutineScope.() -> T): T? = withTimeoutOrNull(timeout.inWholeMilliseconds, block)

private suspend fun currentContext(): CoroutineContext = suspendCoroutine { it.resume(it.context) }

/** Busy-waits, capped at five seconds: a source's retry back-off rather than a real scheduler. */
suspend fun delay(timeMillis: Long) {
    if (timeMillis <= 0) return
    val end = kotlin.js.Date.now() + minOf(timeMillis, 5_000L).toDouble()
    while (kotlin.js.Date.now() < end) { /* spin */ }
}

suspend fun delay(duration: kotlin.time.Duration) = delay(duration.inWholeMilliseconds)

suspend fun yield() {}

suspend fun currentCoroutineContext(): CoroutineContext = currentContext()

// Cancellable continuations

interface CancellableContinuation<in T> : Continuation<T> {
    val isActive: Boolean
    val isCompleted: Boolean
    val isCancelled: Boolean
    fun cancel(cause: Throwable? = null): Boolean
    fun invokeOnCancellation(handler: (cause: Throwable?) -> Unit)
    fun resume(value: T, onCancellation: ((cause: Throwable) -> Unit)?)
}

private class CancellableContinuationImpl<T>(private val delegate: Continuation<T>) : CancellableContinuation<T> {
    private var done = false
    private var cancelled = false
    private val handlers = mutableListOf<(Throwable?) -> Unit>()

    override val context: CoroutineContext get() = delegate.context
    override val isActive: Boolean get() = !done
    override val isCompleted: Boolean get() = done
    override val isCancelled: Boolean get() = cancelled

    override fun resumeWith(result: Result<T>) {
        if (done) return
        done = true
        delegate.resumeWith(result)
    }

    override fun resume(value: T, onCancellation: ((cause: Throwable) -> Unit)?) = resumeWith(Result.success(value))

    override fun cancel(cause: Throwable?): Boolean {
        if (done) return false
        cancelled = true
        handlers.forEach { it(cause) }
        resumeWith(Result.failure(cause as? CancellationException ?: CancellationException("Continuation was cancelled", cause)))
        return true
    }

    override fun invokeOnCancellation(handler: (cause: Throwable?) -> Unit) {
        handlers += handler
    }
}

suspend inline fun <T> suspendCancellableCoroutine(crossinline block: (CancellableContinuation<T>) -> Unit): T =
    suspendCoroutine { cont -> block(wrapCancellable(cont)) }

@PublishedApi
internal fun <T> wrapCancellable(cont: Continuation<T>): CancellableContinuation<T> = CancellableContinuationImpl(cont)

// Flow-free helpers some sources reach for

object NonCancellable : AbstractCoroutineContextElement(Job), Job {
    override val isActive: Boolean get() = true
    override val isCompleted: Boolean get() = false
    override val isCancelled: Boolean get() = false
    override val children: Sequence<Job> get() = emptySequence()
    override fun start(): Boolean = false
    override fun cancel(cause: CancellationException?) {}
    override suspend fun join() {}
    override fun invokeOnCompletion(handler: (cause: Throwable?) -> Unit): DisposableHandle = DisposableHandle {}
}
