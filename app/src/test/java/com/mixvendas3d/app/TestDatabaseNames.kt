package com.mixvendas3d.app

import java.util.concurrent.atomic.AtomicInteger

private val testDatabaseSequence = AtomicInteger()

// Robolectric adds the test name to the parent path. Leave room for SQLite's
// journal and WAL suffixes within the Windows native path limit.
internal fun shortTestDatabaseName(tag: String): String =
    "${tag.take(4)}${testDatabaseSequence.incrementAndGet()}.db"
