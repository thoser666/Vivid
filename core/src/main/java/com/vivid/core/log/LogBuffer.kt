package com.vivid.core.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ringpuffer für die letzten [capacity] Log-Zeilen (In-App-Log).
 *
 * Thread-safe über `synchronized` (Timber-Trees können von beliebigen Threads
 * aufgerufen werden); die UI liest über [entries] (StateFlow) oder [snapshot].
 */
class LogBuffer(
    private val capacity: Int = DEFAULT_CAPACITY,
) {

    private val deque = ArrayDeque<LogEntry>()

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Live-Ansicht des Puffers (älteste zuerst) für die UI. */
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    @Synchronized
    fun add(entry: LogEntry) {
        if (deque.size == capacity) {
            deque.removeFirst()
        }
        deque.addLast(entry)
        _entries.value = deque.toList()
    }

    @Synchronized
    fun clear() {
        deque.clear()
        _entries.value = emptyList()
    }

    /** Einfrierende Kopie des aktuellen Puffer-Inhalts (für Export/Share). */
    @Synchronized
    fun snapshot(): List<LogEntry> = deque.toList()

    companion object {
        const val DEFAULT_CAPACITY = 500

        /** App-weite Instanz — von VividApplication (Timber-Tree) und der UI genutzt. */
        val instance: LogBuffer = LogBuffer()
    }
}