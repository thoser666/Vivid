package com.vivid.feature.chat.twitch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.Socket
import javax.inject.Inject
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

interface IrcConnection : AutoCloseable {
    suspend fun connect()
    fun write(line: String)
    val incoming: Flow<String>
}

fun interface IrcConnectionFactory {
    fun create(): IrcConnection
}

class SocketIrcConnectionFactory @Inject constructor() : IrcConnectionFactory {
    override fun create(): IrcConnection = SocketIrcConnection()
}

class SocketIrcConnection(
    private val host: String = "irc.chat.twitch.tv",
    private val port: Int = 6697,
    private val useTls: Boolean = true,
) : IrcConnection {

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        close()
        val raw = Socket(host, port)
        try {
            val connected = if (useTls) {
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val ssl = factory.createSocket(raw, host, port, true) as SSLSocket
                ssl.startHandshake()
                ssl
            } else {
                raw
            }
            socket = connected
            reader = connected.getInputStream().bufferedReader(Charsets.UTF_8)
            writer = connected.getOutputStream().bufferedWriter(Charsets.UTF_8)
        } catch (e: Throwable) {
            runCatching { raw.close() }
            throw e
        }
    }

    override fun write(line: String) {
        val out = writer ?: return
        out.write(line)
        out.newLine()
        out.flush()
    }

    override val incoming: Flow<String> = flow {
        val input = reader ?: throw IllegalStateException("IRC connection is not connected")
        while (true) {
            val line = input.readLine() ?: break
            if (line.isNotEmpty()) emit(line)
        }
    }.flowOn(Dispatchers.IO)

    override fun close() {
        runCatching { reader?.close() }
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        reader = null
        writer = null
        socket = null
    }
}
