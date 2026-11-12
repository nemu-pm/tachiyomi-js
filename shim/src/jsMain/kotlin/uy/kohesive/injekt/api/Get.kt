package uy.kohesive.injekt.api

import uy.kohesive.injekt.Injekt

inline fun <reified T : Any> Injekt.get(): T = get(T::class)

inline fun <reified T : Any> Injekt.getInstance(): T = get(T::class)

inline fun <reified T : Any> Injekt.getOrNull(): T? = getOrNull(T::class)
