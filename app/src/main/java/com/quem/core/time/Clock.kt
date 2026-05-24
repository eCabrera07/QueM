package com.quem.core.time

import java.time.Instant

interface Clock {
    fun now(): Instant
}

class SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}

class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
