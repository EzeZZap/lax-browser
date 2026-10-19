package com.javzzap.laxbrowser.engine

import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import kotlin.concurrent.thread

/**
 * Minimal loopback HTTP server for engine tests. Every path returns [STORAGE_PAGE], which reports
 * the values in localStorage and cookies through the document title, then stores `?set=` values.
 */
class TestHttpServer {
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

    fun url(path: String) = "http://127.0.0.1:${socket.localPort}$path"

    fun start(): TestHttpServer {
        thread(isDaemon = true, name = "TestHttpServer") {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: SocketException) {
                    break
                }
                thread(isDaemon = true) {
                    client.use { c ->
                        val reader = c.getInputStream().bufferedReader()
                        val requestLine = reader.readLine() ?: return@use
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val path = requestLine.split(' ').getOrNull(1).orEmpty()
                        val (status, body) = if (path.startsWith("/favicon")) "404 Not Found" to "" else "200 OK" to STORAGE_PAGE
                        val bytes = body.toByteArray()
                        val header = "HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\n" +
                            "Content-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
                        c.getOutputStream().apply {
                            write(header.toByteArray())
                            write(bytes)
                            flush()
                        }
                    }
                }
            }
        }
        return this
    }

    fun stop() {
        socket.close()
    }

    companion object {
        private val STORAGE_PAGE = """
            <!doctype html><html><head><title>loading</title></head><body><script>
            const params = new URLSearchParams(location.search);
            const stored = localStorage.getItem('k') || 'none';
            const match = document.cookie.match(/(?:^|; )c=([^;]*)/);
            const cookie = match ? match[1] : 'none';
            const value = params.get('set');
            if (value) {
              localStorage.setItem('k', value);
              document.cookie = 'c=' + value + '; max-age=86400; path=/';
            }
            document.title = 'result:ls=' + stored + ';cookie=' + cookie;
            </script></body></html>
        """.trimIndent()
    }
}
