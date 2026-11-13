package com.example.netspeed.core.downloader

import com.example.netspeed.core.database.DownloadDao
import com.example.netspeed.core.database.DownloadEntity
import com.example.netspeed.core.database.SegmentDao
import com.example.netspeed.core.network.NetKind
import com.example.netspeed.core.network.NetworkBindingManager
import com.example.netspeed.core.network.NetworkManager
import kotlinx.coroutines.CancellationException
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
import okhttp3.Call
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
    /** Extra policy gate for mobile data (monthly limit). Evaluated continuously. */
    val mobileAllowed: () -> Boolean = { true },
    /** User allowed downloading with only one network (Settings). */
    val allowSingle: () -> Boolean = { false },
    /** Pro: Wi-Fi and mobile data work on the same file together. Free: only one network is used at a time. */
    val dual: () -> Boolean = { true }
)

/** Error codes. The UI turns them into precise Persian/English text. */
object Err {
    const val NO_WIFI = "NO_WIFI"
    const val NO_MOBILE = "NO_MOBILE"
    const val RANGE_UNSUPPORTED = "RANGE_UNSUPPORTED"
    const val RANGE_INVALID = "RANGE_INVALID"
    const val AUTH = "AUTH"
    const val UNREACHABLE = "UNREACHABLE"
    const val FILE_CHANGED = "FILE_CHANGED"
    const val VERIFY_FAILED = "VERIFY_FAILED"
    const val SAVE_FAILED = "SAVE_FAILED"
    const val SEGMENT_FAILED = "SEGMENT_FAILED"
    const val MOBILE_LIMIT = "MOBILE_LIMIT"
    const val UNKNOWN = "UNKNOWN"

    /** Errors where an automatic retry cannot help. */
    val permanent = setOf(AUTH, RANGE_UNSUPPORTED, RANGE_INVALID, FILE_CHANGED, VERIFY_FAILED, MOBILE_LIMIT)
}

sealed class JobOutcome {
    data class Done(val file: File, val wifiBytes: Long, val mobileBytes: Long) : JobOutcome()
    /** [code] is one of [Err] (or HTTP_<n>), [detail] is an English technical note for logs. */
    data class Failed(val code: String, val detail: String? = null) : JobOutcome()
}

private class FatalDownloadException(val code: String, msg: String) : IOException(msg)

/**
 * Runs ONE dual-network download to a verified temp file. Cancelling the coroutine pauses or
 * cancels it; segment progress is persisted in `finally`, so resume continues where it stopped.
 *
 * Truthfulness rules:
 *  - speed numbers are real byte rates measured from the sockets (no smoothing);
 *  - the moment a network disappears its in-flight HTTP calls are cancelled and its speed shows 0;
 *  - a network that comes back is picked up again by its idle workers within a second.
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
    private val calls = mapOf(
        NetKind.WIFI to ConcurrentHashMap.newKeySet<Call>(),
        NetKind.MOBILE to ConcurrentHashMap.newKeySet<Call>()
    )
    @Volatile private var fatalCode: String? = null
    @Volatile private var fatalDetail: String? = null
    private val startedAt = System.currentTimeMillis()

    private fun usable(kind: NetKind) = net.state.value[kind].usable
    /** Both networks up, or (when the user allowed it) at least one. */
    private fun ready(): Boolean {
        val w = usable(NetKind.WIFI); val m = usable(NetKind.MOBILE)
        return (w && m) || (cfg.allowSingle() && (w || m))
    }
    private fun allowed(kind: NetKind) = kind == NetKind.WIFI || cfg.mobileAllowed()

    /**
     * Free version: exactly one network downloads at a time, Wi-Fi first. If Wi-Fi drops the mobile
     * workers take over automatically (segments go back to the queue); when Wi-Fi returns it takes over again.
     */
    private fun activeInFreeMode(kind: NetKind): Boolean {
        val wifiUp = usable(NetKind.WIFI)
        return if (kind == NetKind.WIFI) wifiUp else !wifiUp
    }

    /** Localizable hint about what is wrong right now (null = everything fine). */
    private fun notice(): String? {
        val w = usable(NetKind.WIFI); val m = usable(NetKind.MOBILE)
        return when {
            !w && !m -> "waiting_both"
            !w -> if (cfg.allowSingle()) null else "wifi_lost"
            !m -> if (cfg.allowSingle()) null else "mobile_lost"
            !cfg.mobileAllowed() -> "mobile_limit"
            else -> null
        }
    }

    private fun publish(status: DownloadStatus, total: Long, done: Long, notice: String?) {
        fun stats(k: NetKind): NetStats {
            val up = usable(k)
            return NetStats(
                k, if (up) meters.getValue(k).sample() else 0L.also { meters.getValue(k).sample() },
                meters.getValue(k).bytes, conns.getValue(k).get(), assigned.getValue(k).toList().sorted()
            )
        }
        progress.value = LiveProgress(d.id, status, total, done, stats(NetKind.WIFI), stats(NetKind.MOBILE), startedAt, notice)
    }

    private fun failFatal(code: String, detail: String) { fatalDetail = detail; fatalCode = code }

    suspend fun run(): JobOutcome {
        if (!cfg.mobileAllowed()) return JobOutcome.Failed(Err.MOBILE_LIMIT)

        // ---- dual mode needs BOTH networks: wait (and tell the user exactly which one is missing) ----
        while (!ready()) {
            currentCoroutineContext().job.ensureActive()
            val notice = when {
                !usable(NetKind.WIFI) && !usable(NetKind.MOBILE) -> "waiting_both"
                !usable(NetKind.WIFI) -> "waiting_wifi"
                else -> "waiting_mobile"
            }
            publish(DownloadStatus.RUNNING, d.totalBytes, d.doneBytes, notice)
            delay(1000)
        }
        publish(DownloadStatus.PROBING, d.totalBytes, d.doneBytes, "checking")
        val first = net.state.value.let { if (it.wifi.usable) it.wifi else it.mobile }
        val client = binding.clientFor(first.network!!)
        val probe = withContext(Dispatchers.IO) { RangeProbe.probe(client, d.url, opts) }
        if (probe.authRequired) return JobOutcome.Failed(Err.AUTH)
        if (!probe.canMultiPart) {
            // The whole point of the app is the dual-network download: no verified Range = no download.
            return JobOutcome.Failed(probe.code.takeIf { it != "OK" } ?: Err.RANGE_UNSUPPORTED, probe.message)
        }
        dao.update(d.copy(
            finalUrl = probe.finalUrl, totalBytes = probe.totalBytes, rangeVerified = true,
            etag = probe.etag, lastModified = probe.lastModified, mimeType = probe.mimeType ?: d.mimeType
        ))
        return runSegmented(probe)
    }

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
                        publish(DownloadStatus.RUNNING, total, queue.doneBytes, notice())
                        persist(queue, channel)
                        delay(1000)
                    }
                }
                // Cut the sockets of a network the instant Android reports it gone or replaced,
                // instead of waiting for a read timeout. This is what keeps the speed honest.
                val watcher = launch {
                    val last = HashMap<NetKind, android.net.Network?>()
                    net.state.collect { s ->
                        NetKind.values().forEach { k ->
                            val cur = s[k].network.takeIf { s[k].usable }
                            if (last.containsKey(k) && last[k] != cur) calls.getValue(k).toList().forEach { it.cancel() }
                            last[k] = cur
                        }
                    }
                }
                val workers = NetKind.values().flatMap { kind ->
                    (0 until cfg.connectionsPerNetwork).map {
                        launch(Dispatchers.IO) { worker(kind, probe.finalUrl, queue, channel, total) }
                    }
                }
                while (isActive && !queue.allDone() && fatalCode == null) delay(200)
                workers.forEach { it.cancel() }
                watcher.cancel()
                ticker.cancel()
            }
        } finally {
            withContext(NonCancellable) {
                runCatching { channel.force(true) }
                persist(queue, null)
                runCatching { raf.close() }
            }
        }
        fatalCode?.let { return JobOutcome.Failed(it, fatalDetail) }

        // ---- verification: never mark complete before this passes ----
        publish(DownloadStatus.VERIFYING, total, total, null)
        SegmentPlanner.verify(queue.all.map { it.toEntity() }, total)
            ?.let { return JobOutcome.Failed(Err.VERIFY_FAILED, it) }
        if (temp.length() != total) return JobOutcome.Failed(Err.VERIFY_FAILED, "File size ${temp.length()} != $total")
        return JobOutcome.Done(temp, meters.getValue(NetKind.WIFI).bytes, meters.getValue(NetKind.MOBILE).bytes)
    }

    private suspend fun persist(queue: SegmentQueue, channel: java.nio.channels.FileChannel?) {
        runCatching { channel?.force(false) }
        queue.all.forEach { segDao.update(it.toEntity()) }
        dao.setProgress(d.id, queue.doneBytes, meters.getValue(NetKind.WIFI).bytes, meters.getValue(NetKind.MOBILE).bytes)
    }

    private suspend fun worker(
        kind: NetKind, url: String, queue: SegmentQueue,
        channel: java.nio.channels.FileChannel, total: Long
    ) {
        var failures = 0
        while (currentCoroutineContext().isActive && !queue.allDone() && fatalCode == null) {
            val st = net.state.value[kind]
            // A disconnected / disallowed network gets no new segments; it resumes by itself on return.
            if (!st.usable || !allowed(kind)) { failures = 0; delay(500); continue }
            if (!cfg.dual() && !activeInFreeMode(kind)) { failures = 0; delay(500); continue }   // free: other network waits
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
                failFatal(e.code, e.message ?: "")
            } catch (e: Exception) {
                queue.giveBack(seg)                      // the other network may pick it up
                // Only count it against the segment when the network was fine; a lost network is not
                // the segment's fault and must not make the whole download fail.
                if (usable(kind)) {
                    seg.retries++
                    failures++
                    if (seg.retries > cfg.maxSegmentRetries) {
                        failFatal(Err.SEGMENT_FAILED, "Segment ${seg.e.index} failed ${seg.retries} times: ${e.message}")
                    }
                }
                delay(if (usable(kind)) min(6000L, 400L shl min(failures, 4)) else 500L)
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
        calls.getValue(kind).add(call)
        val h = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { r ->
                if (r.code == 200) throw FatalDownloadException(Err.FILE_CHANGED, "The file changed on the server.")
                if (r.code == 401 || r.code == 407) throw FatalDownloadException(Err.AUTH, "HTTP ${r.code}")
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
            calls.getValue(kind).remove(call)
            h.dispose()
        }
    }
}
