@file:OptIn(kotlin.time.ExperimentalTime::class)

package tachiyomi.shim.compat

/*
 * kotlin.time.Instant <-> java.time.Instant, the JVM-only `kotlin.time.toJavaInstant` and
 * `toKotlinInstant` the preprocessor redirects here.
 */

fun kotlin.time.Instant.toJavaInstant(): java.time.Instant =
    java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

fun java.time.Instant.toKotlinInstant(): kotlin.time.Instant =
    kotlin.time.Instant.fromEpochSeconds(epochSecond, nano)
