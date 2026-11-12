package keiyoushi.network

// Ported from keiyoushi core network/RateLimit.kt. The sandbox runs calls one at a time on a
// worker, so the ReentrantLock/Condition pair becomes a blocking wait on SystemClock.

import android.os.SystemClock
import tachiyomi.shim.compat.java
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Adds a rate limit rule to the builder's [RateLimitInterceptor], creating it if this is the
 * first [rateLimit] call. Chain further calls for additional rules — the first rule whose
 * [shouldLimit] matches the request URL is applied, remaining rules are skipped, so define more
 * specific rules before broader ones.
 *
 * @param permits     Requests allowed per [period].
 * @param period      Sliding-window duration.
 * @param interval    Minimum gap between consecutive dispatches.
 * @param shouldLimit Whether this rule applies to a given URL.
 */
fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Duration = 1.seconds,
    interval: Duration = Duration.ZERO,
    shouldLimit: (HttpUrl) -> Boolean = { true },
): OkHttpClient.Builder = apply {
    val rule = RateLimitRule(permits, period, interval, shouldLimit)

    val existing = networkInterceptors().firstInstanceOrNull<RateLimitInterceptor>()
    if (existing != null) {
        existing.addRule(rule)
        return@apply
    }

    if (RateLimitInterceptor.TaggingInterceptor !in interceptors()) {
        addInterceptor(RateLimitInterceptor.TaggingInterceptor)
    }
    addNetworkInterceptor(RateLimitInterceptor(rule))
}

internal class RateLimitRule(
    val permits: Int,
    val period: Duration,
    val interval: Duration,
    val shouldLimit: (HttpUrl) -> Boolean,
)

internal class RateLimitInterceptor(
    rule: RateLimitRule,
) : Interceptor {

    private class RateLimitTag(
        var rateLimitApplied: Boolean = false,
    )

    object TaggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .tag(RateLimitTag::class.java, RateLimitTag())
                .build()

            return chain.proceed(request)
        }
    }

    private class RateLimit(
        val permits: Int,
        val period: Duration,
        val interval: Duration,
        val shouldLimit: (HttpUrl) -> Boolean,
    ) {
        val queue = ArrayDeque<Duration>(permits)
        var lastDispatchTime: Duration? = null
    }

    private val rateLimits = mutableListOf(RateLimit(rule.permits, rule.period, rule.interval, rule.shouldLimit))

    fun addRule(rule: RateLimitRule) {
        rateLimits += RateLimit(rule.permits, rule.period, rule.interval, rule.shouldLimit)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val call = chain.call()
        val request = chain.request()

        if (call.isCanceled()) throw IOException("Canceled")

        val rateLimit = rateLimits.firstOrNull { it.shouldLimit(request.url) }
            ?: return chain.proceed(request)

        val state = request.tag(RateLimitTag::class.java)

        if (state?.rateLimitApplied == true) {
            return chain.proceed(request)
        }

        rateLimit.acquireSlot(call)
        state?.rateLimitApplied = true

        return chain.proceed(request)
    }

    private fun RateLimit.acquireSlot(call: Call) {
        while (true) {
            if (call.isCanceled()) throw IOException("Canceled")
            val now = SystemClock.elapsedRealtime().milliseconds
            while (queue.isNotEmpty() && now - queue.first() >= period) {
                queue.removeFirst()
            }

            val windowWait = if (queue.size < permits) {
                Duration.ZERO
            } else {
                period - (now - queue.first())
            }
            val intervalWait = if (lastDispatchTime == null) {
                Duration.ZERO
            } else {
                interval - (now - lastDispatchTime!!)
            }
            val waitTime = maxOf(windowWait, intervalWait, Duration.ZERO)

            if (waitTime == Duration.ZERO) {
                val ts = SystemClock.elapsedRealtime().milliseconds
                queue.addLast(ts)
                lastDispatchTime = ts
                break
            }
            sleepBlocking(waitTime)
        }
    }
}

/** Blocks the worker for [duration]; the sandbox has no other thread to hand the wait to. */
private fun sleepBlocking(duration: Duration) {
    val until = SystemClock.elapsedRealtime() + duration.inWholeMilliseconds
    while (SystemClock.elapsedRealtime() < until) {
        // spin
    }
}
