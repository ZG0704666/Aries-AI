package com.ai.assistance.operit.util

import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong

object HttpMultiPartDownloader {
    fun download(
        url: String,
        dest: File,
        threadCount: Int = 4,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ) {
        val safeThreads = threadCount.coerceIn(1, 8)

        val meta = probe(url)
        val total = meta.contentLength
        val supportsRanges = meta.acceptRanges

        if (total <= 0L || !supportsRanges || safeThreads == 1) {
            downloadSingle(url, dest, total, onProgress)
            return
        }

        downloadMulti(url, dest, total, safeThreads, onProgress)
    }

    private data class ProbeResult(
        val contentLength: Long,
        val acceptRanges: Boolean
    )

    private fun probe(url: String): ProbeResult {
        // Prefer HEAD, but some servers don't allow it.
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                setRequestProperty("Accept-Encoding", "identity")
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 15000
            }
            val code = conn.responseCode
            if (code in 200..399) {
                val len = conn.getHeaderFieldLong("Content-Length", -1L)
                val acceptRanges = conn.getHeaderField("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                return ProbeResult(len, acceptRanges)
            }
        } catch (_: Exception) {
            // ignore
        } finally {
            conn?.disconnect()
        }

        // Fallback GET with Range 0-0 to detect range support.
        var conn2: HttpURLConnection? = null
        try {
            conn2 = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Range", "bytes=0-0")
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 15000
            }
            val code = conn2.responseCode
            val acceptRangesHeader = conn2.getHeaderField("Accept-Ranges")
            val acceptRanges = (code == HttpURLConnection.HTTP_PARTIAL) ||
                (acceptRangesHeader?.contains("bytes", ignoreCase = true) == true)

            val contentRange = conn2.getHeaderField("Content-Range")
            val totalFromContentRange = parseTotalFromContentRange(contentRange)
            val total = when {
                totalFromContentRange > 0L -> totalFromContentRange
                else -> conn2.getHeaderFieldLong("Content-Length", -1L)
            }
            return ProbeResult(total, acceptRanges)
        } catch (_: Exception) {
            return ProbeResult(-1L, false)
        } finally {
            conn2?.disconnect()
        }
    }

    private fun parseTotalFromContentRange(contentRange: String?): Long {
        // format: bytes 0-0/12345
        if (contentRange.isNullOrBlank()) return -1L
        val slash = contentRange.lastIndexOf('/')
        if (slash <= 0 || slash >= contentRange.length - 1) return -1L
        return contentRange.substring(slash + 1).trim().toLongOrNull() ?: -1L
    }

    private fun downloadSingle(
        url: String,
        dest: File,
        totalBytes: Long,
        onProgress: ((Long, Long) -> Unit)?
    ) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept-Encoding", "identity")
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 30000
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                throw RuntimeException("HTTP $code")
            }

            val total = if (totalBytes > 0L) totalBytes else conn.getHeaderFieldLong("Content-Length", -1L)
            val downloaded = AtomicLong(0L)

            dest.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        val now = downloaded.addAndGet(read.toLong())
                        onProgress?.invoke(now, total)
                    }
                    output.flush()
                }
            }
        } finally {
            conn?.disconnect()
        }
    }

    private fun downloadMulti(
        url: String,
        dest: File,
        totalBytes: Long,
        threadCount: Int,
        onProgress: ((Long, Long) -> Unit)?
    ) {
        dest.parentFile?.mkdirs()

        // Pre-allocate file
        RandomAccessFile(dest, "rw").use { raf ->
            raf.setLength(totalBytes)
        }

        val downloaded = AtomicLong(0L)
        val firstError = AtomicReference<Throwable?>(null)
        val pool = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        val partSize = (totalBytes + threadCount - 1) / threadCount

        for (part in 0 until threadCount) {
            val start = part * partSize
            val end = minOf(totalBytes - 1, (part + 1) * partSize - 1)
            if (start > end) {
                latch.countDown()
                continue
            }

            pool.execute {
                var conn: HttpURLConnection? = null
                try {
                    conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("Accept-Encoding", "identity")
                        instanceFollowRedirects = true
                        connectTimeout = 15000
                        readTimeout = 30000
                        setRequestProperty("Range", "bytes=$start-$end")
                    }

                    val code = conn.responseCode
                    if (code != HttpURLConnection.HTTP_PARTIAL && code != HttpURLConnection.HTTP_OK) {
                        throw RuntimeException("HTTP $code")
                    }

                    conn.inputStream.use { input ->
                        RandomAccessFile(dest, "rw").use { raf ->
                            raf.seek(start)
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                raf.write(buffer, 0, read)
                                val now = downloaded.addAndGet(read.toLong())
                                onProgress?.invoke(now, totalBytes)
                            }
                        }
                    }
                } catch (t: Throwable) {
                    firstError.compareAndSet(null, t)
                } finally {
                    conn?.disconnect()
                    latch.countDown()
                }
            }
        }

        try {
            latch.await()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            firstError.compareAndSet(null, e)
            throw e
        } finally {
            val err = firstError.get()
            if (err != null) {
                pool.shutdownNow()
            } else {
                pool.shutdown()
            }
        }

        val err = firstError.get()
        if (err != null) {
            try {
                dest.delete()
            } catch (_: Exception) {
            }
            throw RuntimeException("Multi-part download failed", err)
        }

        onProgress?.invoke(totalBytes, totalBytes)
    }
}
