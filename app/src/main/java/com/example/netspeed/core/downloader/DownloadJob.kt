package com.example.netspeed.core.downloader

import com.example.netspeed.core.database.DownloadDao
import com.example.netspeed.core.database.DownloadEntity
import com.example.netspeed.core.database.SegmentDao
import com.example.netspeed.core.network.NetKind
import com.example.netspeed.core.network.NetworkBindingManager
import com.example.netspeed.core.network.NetworkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

class JobConfig(
    val connectionsPerNetwork: Int = 3,
    val maxSegmentRetries: Int = 8,
    val limiter: RateLimiter = RateLimiter(0),
    /** Extra policy gate for mobile data (monthly limit etc). Evaluated continuously. */
    val mobileAllowed: () -> Boolean = { true }
)

sealed class JobOutcome {
    data class Done(val file: File, val wifiBytes: Long, val mobileBytes: Long) : JobOutcome()
    data class Failed(val message: String) : JobOutcome()
}

private class FatalDownloadException(msg: String) : IOException(msg)

/**
 * Runs ONE download to a verified temp file. Cancelling the coroutine pauses/cancels it;
 * segment progress is persisted in `finally`, so resume continues where it stopped.
 */
class DownloadJob(
    private val d: DownloadEntity,
    private val net: NetworkManager,
    private val binding: NetworkBindingManager,
    private val dao: DownloadDao,
    private val segDao: SegmentDao,
    private val cfg: JobConfig,
    private val progress: MutableStateFlow<LiveProgress>
) {
    private val opts = RequestOptions(
        userAgent = d.userAgent ?: RequestOptions.DEFAULT_UA,
        referer = d.referer, cookies = d.cookies,
        username = d.username, password = d.password
    )
    private val meters = mapOf(NetKind.WIFI to SpeedMeter(), NetKind.MOBILE to SpeedMeter())
    private val conns = mapOf(NetKind.WIFI to AtomicInteger(), NetKind.MOBILE to AtomicInteger())
    private val assigned = mapOf(
        NetKind.WIFI to ConcurrentHashMap.newKeySet<Int>(),
        NetKind.MOBILE to ConcurrentHashMap.newKeySet<Int>()
    )
    @Volatile private var fatal: String? = null
    private val mode = NetworkMode.valueOf(d.mode)
    private val startedAt = System.currentTimeMillis()

    /** Whether new work may be routed over [kind] right now. */
    private fun allowed(kind: NetKind): Boolean {
        val s = net.state.value
        return when (mode) {
            NetworkMode.WIFI_ONLY -> kind == NetKind.WIFI
            NetworkMode.MOBILE_ONLY -> kind == NetKind.MOBILE && cfg.mobileAllowed()
            NetworkMode.DUAL -> kind == NetKind.WIFI || cfg.mobileAllowed()
            // AUTO: Wi-Fi first, mobile only as a fallback when Wi-Fi is not usable
            NetworkMode.AUTO -> kind == NetKind.WIFI || (!s.wifi.usable && cfg.mobileAllowed())
        }
    }

    private fun publish(status: DownloadStatus, total: Long, done: Long, msg: String? = null) {
        fun stats(k: NetKind) = NetStats(
            k, meters.getValue(k).sample(), meters.getValue(k).bytes,
            conns.getValue(k).get(), assigned.getValue(k).toList().sorted()
        )
        progress.value = LiveProgress(d.id, status, total, done, stats(NetKind.WIFI), stats(NetKind.MOBILE), startedAt, msg)
    }

    suspend fun run(): JobOutcome {
        // ---- wait for any allowed, usable network (survives disconnects) ----
        var client: okhttp3.OkHttpClient? = null
        var probeNet: NetKind? = null
        while (client == null) {
            currentCoroutineContext().job.ensureActive()
            val s = net.state.value
            val k = NetKind.values().firstOrNull { allowed(it) && s[it].usable }
            if (k != null) { client = binding.clientFor(s[k].network!!); probeNet = k }
            else { publish(DownloadStatus.RUNNING, d.totalBytes, d.doneBytes, "Waiting for network…"); delay(1000) }
        }
        publish(DownloadStatus.PROBING, d.totalBytes, d.doneBytes, "Checking server ($probeNet)…")
        val probe = withContext(Dispatchers.IO) { RangeProbe.probe(client, d.url, opts) }
        if (probe.authRequired) return JobOutcome.Failed(probe.message)
        if (probe.totalBytes <= 0 && !probe.rangeVerified && probe.message.startsWith("Cannot reach")) {
            return JobOutcome.Failed(probe.message)
        }
        dao.update(d.copy(
            finalUrl = probe.finalUrl, totalBytes = probe.totalBytes, rangeVerified = probe.rangeVerified,
            etag = probe.etag, lastModified = probe.lastModified, mimeType = probe.mimeType ?: d.mimeType
        ))
        return if (probe.canMultiPart) runSegmented(probe) else runSingle(probe)
    }

    // ------------------------------------------------------------------------------------
    //  Multi-part, multi-network
    // ------------------------------------------------------------------------------------
    private suspend fun runSegmented(probe: ProbeResult): JobOutcome {
        val total = probe.totalBytes
        val temp = File(d.tempPath)
        temp.parentFile?.mkdirs()

        // ---- resume or fresh plan ----
        var rows = segDao.forDownload(d.id)
        val sameFile = d.etag == null || d.etag == probe.etag
        if (rows.isEmpty() || !temp.exists() || temp.length() != total || !sameFile) {
            segDao.deleteFor(d.id)
            temp.delete()
            rows = SegmentPlanner.plan(d.id, total)
            segDao.insertAll(rows)
            rows = segDao.forDownload(d.id)
        }
        val raf = RandomAccessFile(temp, "rw")
        if (raf.length() != total) raf.setLength(total)
        val channel = raf.channel
        val queue = SegmentQueue(rows.map { Seg(it) })
        meters.getValue(NetKind.WIFI).reset(d.wifiBytes)
        meters.getValue(NetKind.MOBILE).reset(d.mobileBytes)

        try {
            coroutineScope {
                val ticker = launch(Dispatchers.IO) {
                    while (isActive) {
                        publish(DownloadStatus.RUNNING, total, queue.doneBytes, fatal)
                        persist(queue, total, channel)
                        delay(1000)
                    }
                }
                val workers = NetKind.values().flatMap { kind ->
                    (0 until cfg.connectionsPerNetwork).map {
                        launch(Dispatchers.IO) { worker(kind, probe.finalUrl, queue, channel, total) }
                    }
                }
                // finish when all segments are done, or a fatal error appears
                while (isActive && !queue.allDone() && fatal == null) delay(200)
                workers.forEach { it.cancel() }
                ticker.cancel()
            }
        } finally {
            withContext(NonCancellable) {
                runCatching { channel.force(true) }
                persist(queue, total, null)
                runCatching { raf.close() }
            }
        }
        fatal?.let { return JobOutcome.Failed(it) }

        // ---- verification: never mark complete before this passes ----
        publish(DownloadStatus.VERIFYING, total, total)
        val rowsNow = queue.all.map { it.toEntity() }
        SegmentPlanner.verify(rowsNow, total)?.let { return JobOutcome.Failed("Verification failed: $it") }
        if (temp.length() != total) return JobOutcome.Failed("File size ${temp.length()} != expected $total")
        return JobOutcome.Done(temp, meters.getValue(NetKind.WIFI).bytes, meters.getValue(NetKind.MOBILE).bytes)
    }

    private suspend fun persist(queue: SegmentQueue, total: Long, channel: java.nio.channels.FileChannel?) {
        runCatching { channel?.force(false) }
        queue.all.forEach { segDao.update(it.toEntity()) }
        dao.setProgress(d.id, queue.doneBytes, meters.getValue(NetKind.WIFI).bytes, meters.getValue(NetKind.MOBILE).bytes)
    }

    private suspend fun worker(
        kind: NetKind, url: String, queue: SegmentQueue,
        channel: java.nio.channels.FileChannel, total: Long
    ) {
        var failures = 0
        while (currentCoroutineContext().isActive && !queue.allDone() && fatal == null) {
            val st = net.state.value[kind]
            // Stop assigning new segments to a disconnected / disallowed network.
            if (!st.usable || !allowed(kind)) { delay(1000); continue }
            val seg = queue.take()
            if (seg == null) { delay(200); continue }
            assigned.getValue(kind).add(seg.e.index)
            conns.getValue(kind).incrementAndGet()
            try {
                fetch(kind, st.network!!, url, seg, channel, total)
                queue.complete(seg, kind.name)
                failures = 0
            } catch (e: CancellationException) {
                queue.giveBack(seg); throw e
            } catch (e: FatalDownloadException) {
                fatal = e.message
            } catch (e: Exception) {
                seg.retries++
                queue.giveBack(seg)          // the other network may pick it up
                failures++
                if (seg.retries > cfg.maxSegmentRetries) {
                    fatal = "Segment ${seg.e.index} failed ${seg.retries} times: ${e.message}"
                }
                delay(min(8000L, 400L shl min(failures, 5)))   // back off an unstable network
            } finally {
                conns.getValue(kind).decrementAndGet()
                assigned.getValue(kind).remove(seg.e.index)
            }
        }
    }

    private suspend fun fetch(
        kind: NetKind, network: android.net.Network, url: String, seg: Seg,
        channel: java.nio.channels.FileChannel, total: Long
    ) {
        val from = seg.nextByte
        val to = seg.e.endByte
        if (from > to) return
        val client = binding.clientFor(network)
        val b = RangeProbe.applyOptions(Request.Builder().url(url), opts)
            .header("Range", "bytes=$from-$to").header("Accept-Encoding", "identity")
        d.etag?.let { b.header("If-Range", it) }
        val call = client.newCall(b.build())
        val h = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { r ->
                if (r.code == 200) throw FatalDownloadException("The file changed on the server during download.")
                if (r.code != 206) throw IOException("HTTP ${r.code}")
                val cr = Regex("""bytes (\d+)-(\d+)/(\d+)""").matchEntire(r.header("Content-Range")?.trim() ?: "")
                    ?: throw IOException("Bad Content-Range")
                if (cr.groupValues[1].toLong() != from || cr.groupValues[2].toLong() != to ||
                    cr.groupValues[3].toLong() != total) throw IOException("Content-Range mismatch")
                val buf = ByteArray(32 * 1024)
                var pos = from
                r.body!!.byteStream().use { s ->
                    while (true) {
                        val want = min(buf.size.toLong(), to - pos + 1).toInt()
                        if (want <= 0) break
                        val n = s.read(buf, 0, want)
                        if (n < 0) break
                        cfg.limiter.acquire(n)
                        val bb = ByteBuffer.wrap(buf, 0, n)
                        var p = pos
                        while (bb.hasRemaining()) p += channel.write(bb, p)   // positional, non-overlapping
                        pos += n
                        seg.downloaded += n
                        meters.getValue(kind).add(n.toLong())
                    }
                }
                if (pos != to + 1) throw IOException("Short segment: got ${pos - from} of ${to - from + 1}")
            }
        } finally {
            h.dispose()
        }
    }

    // ------------------------------------------------------------------------------------
    //  Fallback: one plain connection (server has no verified Range support)
    // ------------------------------------------------------------------------------------
    private suspend fun runSingle(probe: ProbeResult): JobOutcome {
        val kind = NetKind.values().firstOrNull { allowed(it) && net.state.value[it].usable }
            ?: return JobOutcome.Failed("No allowed network is available.")
        val network = net.state.value[kind].network!!
        val temp = File(d.tempPath)
        temp.parentFile?.mkdirs()
        temp.delete()                               // no Range => cannot resume, restart cleanly
        meters.getValue(kind).reset(0)
        val total = probe.totalBytes
        val call = binding.clientFor(network).newCall(
            RangeProbe.applyOptions(Request.Builder().url(probe.finalUrl), opts).build()
        )
        val h = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        conns.getValue(kind).set(1)
        try {
            coroutineScope {
                val ticker = launch(Dispatchers.IO) {
                    while (isActive) {
                        publish(DownloadStatus.RUNNING, total, meters.getValue(kind).bytes, probe.message)
                        dao.setProgress(d.id, meters.getValue(kind).bytes,
                            meters.getValue(NetKind.WIFI).bytes, meters.getValue(NetKind.MOBILE).bytes)
                        delay(1000)
                    }
                }
                withContext(Dispatchers.IO) {
                    call.execute().use { r ->
                        if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                        RandomAccessFile(temp, "rw").use { f ->
                            val buf = ByteArray(32 * 1024)
                            r.body!!.byteStream().use { s ->
                                while (true) {
                                    val n = s.read(buf)
                                    if (n < 0) break
                                    cfg.limiter.acquire(n)
                                    f.write(buf, 0, n)
                                    meters.getValue(kind).add(n.toLong())
                                }
                            }
                            f.fd.sync()
                        }
                    }
                }
                ticker.cancel()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return JobOutcome.Failed("Download failed: ${e.message}")
        } finally {
            conns.getValue(kind).set(0)
            h.dispose()
        }
        val got = meters.getValue(kind).bytes
        if (total > 0 && got != total) return JobOutcome.Failed("Size mismatch: got $got, expected $total")
        return JobOutcome.Done(temp, meters.getValue(NetKind.WIFI).bytes, meters.getValue(NetKind.MOBILE).bytes)
    }
}
