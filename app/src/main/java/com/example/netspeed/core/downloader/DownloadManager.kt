package com.example.netspeed.core.downloader

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.netspeed.core.common.Format
import com.example.netspeed.core.common.SettingsRepository
import com.example.netspeed.core.database.DownloadDao
import com.example.netspeed.core.database.DownloadEntity
import com.example.netspeed.core.database.SegmentDao
import com.example.netspeed.core.network.NetKind
import com.example.netspeed.core.network.NetworkBindingManager
import com.example.netspeed.core.network.NetworkManager
import com.example.netspeed.core.storage.StorageHelper
import com.example.netspeed.service.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** [message] is an error code (see [Err]) when ok == false. Byte split is filled when ok == true. */
data class DownloadEvent(
    val id: Long, val name: String, val ok: Boolean, val message: String?,
    val wifiBytes: Long = 0, val mobileBytes: Long = 0
)

/** Queue, priorities, concurrency limit, pause/resume/cancel, retry, scheduling, restore. */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val net: NetworkManager,
    private val binding: NetworkBindingManager,
    private val dao: DownloadDao,
    private val segDao: SegmentDao,
    private val storage: StorageHelper,
    private val settings: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val stopReason = ConcurrentHashMap<Long, DownloadStatus>()
    private val pumpLock = Mutex()
    private val limiter = RateLimiter(0)
    private val mobileUsed = AtomicLong(0)
    private val names = ConcurrentHashMap<Long, String>()
    fun nameOf(id: Long): String? = names[id]

    private val _live = MutableStateFlow<Map<Long, LiveProgress>>(emptyMap())
    val live: StateFlow<Map<Long, LiveProgress>> = _live.asStateFlow()

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    val all get() = dao.observeAll()

    /** Last 60 seconds of real per-network speed (wifi, mobile) in bytes/s, newest last. */
    private val _history = MutableStateFlow<List<Pair<Long, Long>>>(List(60) { 0L to 0L })
    val speedHistory: StateFlow<List<Pair<Long, Long>>> = _history.asStateFlow()

    init {
        net.startMonitoring()
        scope.launch { settings.flow.collect { limiter.bytesPerSec = it.speedLimitBps } }
        scope.launch {
            while (true) {
                delay(1000)
                val r = _live.value.values.filter { it.status == DownloadStatus.RUNNING }
                val point = r.sumOf { it.wifi.speedBps } to r.sumOf { it.mobile.speedBps }
                _history.update { (it + point).takeLast(60) }
            }
        }
        scope.launch {
            while (true) {                       // scheduler + mobile-usage refresh
                refreshMobileUsed()
                pump()
                delay(15_000)
            }
        }
    }

    private fun monthStart(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    suspend fun refreshMobileUsed() { mobileUsed.set(dao.mobileBytesSince(monthStart())) }
    fun mobileUsedThisMonth(): Long = mobileUsed.get()

    // ---------------------------------------------------------------- inspection / add

    /** Probe a URL on a real network so the user can confirm what will be downloaded. */
    suspend fun inspect(url: String, o: RequestOptions): ProbeResult = withContext(Dispatchers.IO) {
        val s = net.state.value
        val n = (s.wifi.takeIf { it.usable } ?: s.mobile.takeIf { it.usable })?.network
        val client: OkHttpClient = if (n != null) binding.clientFor(n) else OkHttpClient()
        RangeProbe.probe(client, url, o)
    }

    suspend fun add(
        url: String, probe: ProbeResult, o: RequestOptions,
        nameOverride: String? = null, scheduledAt: Long = 0, priority: Int = 0
    ): Long {
        // A link that cannot do HTTP Range cannot be downloaded over two networks: refuse it.
        if (!probe.canMultiPart) return -1
        val s = settings.flow.first()
        val name = Format.safeName(nameOverride ?: probe.fileName ?: "download.bin")
        val id = dao.insert(
            DownloadEntity(
                url = url, finalUrl = probe.finalUrl, fileName = name, mimeType = probe.mimeType,
                category = Format.category(name, probe.mimeType), totalBytes = probe.totalBytes,
                mode = NetworkMode.DUAL.name, rangeVerified = true, etag = probe.etag,
                lastModified = probe.lastModified, destUri = s.saveTree,
                userAgent = o.userAgent, referer = o.referer, cookies = o.cookies,
                username = o.username, password = o.password,
                scheduledAt = scheduledAt, priority = priority
            )
        )
        dao.update(dao.get(id)!!.copy(tempPath = storage.tempFile(id).absolutePath))
        pump()
        return id
    }

    // ---------------------------------------------------------------- control

    fun pause(id: Long) { stopReason[id] = DownloadStatus.PAUSED; jobs[id]?.cancel() ?: scope.launch { dao.setStatus(id, "PAUSED") } }
    fun cancel(id: Long) { stopReason[id] = DownloadStatus.CANCELED; jobs[id]?.cancel() ?: scope.launch { cleanup(id, "CANCELED") } }
    fun resume(id: Long) = scope.launch { dao.setStatus(id, "QUEUED"); pump() }
    fun retry(id: Long) = scope.launch {
        dao.get(id)?.let { dao.update(it.copy(status = "QUEUED", retryCount = 0, error = null)) }; pump()
    }

    fun pauseAll() = jobs.keys.forEach { pause(it) }
    fun resumeAll() = scope.launch {
        dao.observeAll().first().filter { it.status == "PAUSED" }.forEach { dao.setStatus(it.id, "QUEUED") }
        pump()
    }

    fun remove(id: Long, deleteFile: Boolean) = scope.launch {
        jobs[id]?.let { stopReason[id] = DownloadStatus.CANCELED; it.cancel() }
        val e = dao.get(id)
        if (e != null) {
            if (deleteFile) storage.delete(e.resultUri)
            java.io.File(e.tempPath).delete()
            dao.delete(id)
        }
    }

    fun clearFinished() = scope.launch { dao.clearFinished() }

    /** Resume after the app/process restarted. */
    fun restore() = scope.launch {
        dao.unfinished().forEach { dao.setStatus(it.id, "QUEUED") }
        pump()
    }

    private suspend fun cleanup(id: Long, status: String) {
        dao.get(id)?.let { java.io.File(it.tempPath).delete() }
        segDao.deleteFor(id)
        dao.setStatus(id, status)
    }

    // ---------------------------------------------------------------- scheduling

    suspend fun pump() = pumpLock.withLock {
        val s = settings.flow.first()
        var slots = s.maxConcurrent - jobs.size
        if (slots <= 0) return
        val now = System.currentTimeMillis()
        for (q in dao.queued()) {
            if (slots <= 0) break
            if (q.scheduledAt > now || jobs.containsKey(q.id)) continue
            start(q, s.connectionsPerNetwork, s.mobileLimitBytes, s.autoRetry)
            slots--
        }
    }

    private fun start(e: DownloadEntity, conns: Int, mobileLimit: Long, autoRetry: Boolean) {
        names[e.id] = e.fileName
        net.acquire()
        ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
        val flow = MutableStateFlow(
            LiveProgress(e.id, DownloadStatus.QUEUED, e.totalBytes, e.doneBytes,
                NetStats(NetKind.WIFI), NetStats(NetKind.MOBILE))
        )
        val job = scope.launch {
            val collector = launch { flow.collect { p -> _live.update { it + (e.id to p) } } }
            try {
                dao.setStatus(e.id, "RUNNING")
                val cfg = JobConfig(
                    connectionsPerNetwork = conns, limiter = limiter,
                    mobileAllowed = { mobileLimit <= 0 || mobileUsed.get() < mobileLimit }
                )
                val outcome = try {
                    DownloadJob(e, net, binding, dao, segDao, cfg, flow).run()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    JobOutcome.Failed(Err.UNKNOWN, t.message ?: t.javaClass.simpleName)
                }
                handleOutcome(e, outcome, autoRetry)
            } catch (ce: CancellationException) {
                withContext(NonCancellable) {
                    when (stopReason.remove(e.id)) {
                        DownloadStatus.CANCELED -> cleanup(e.id, "CANCELED")
                        else -> dao.setStatus(e.id, "PAUSED")
                    }
                }
                throw ce
            } finally {
                collector.cancel()
            }
        }
        jobs[e.id] = job
        job.invokeOnCompletion {
            jobs.remove(e.id)
            _live.update { m -> m - e.id }
            net.release()
            scope.launch { pump() }
        }
    }

    private suspend fun handleOutcome(e: DownloadEntity, outcome: JobOutcome, autoRetry: Boolean) {
        val fresh = dao.get(e.id) ?: return
        when (outcome) {
            is JobOutcome.Done -> {
                dao.setStatus(e.id, "VERIFYING")
                val s = settings.flow.first()
                try {
                    val result = withContext(Dispatchers.IO) {
                        storage.finalize(outcome.file, fresh.fileName, fresh.destUri, fresh.mimeType, s.overwrite)
                    }
                    segDao.deleteFor(e.id)
                    dao.update(dao.get(e.id)!!.copy(
                        status = "COMPLETED", resultUri = result, doneBytes = if (fresh.totalBytes > 0) fresh.totalBytes else outcome.wifiBytes + outcome.mobileBytes,
                        wifiBytes = outcome.wifiBytes, mobileBytes = outcome.mobileBytes,
                        completedAt = System.currentTimeMillis(), error = null
                    ))
                    refreshMobileUsed()
                    _events.tryEmit(DownloadEvent(e.id, fresh.fileName, true, null, outcome.wifiBytes, outcome.mobileBytes))
                } catch (t: Throwable) {
                    dao.setStatus(e.id, "FAILED", Err.SAVE_FAILED)
                    _events.tryEmit(DownloadEvent(e.id, fresh.fileName, false, Err.SAVE_FAILED))
                }
            }
            is JobOutcome.Failed -> {
                val retryable = autoRetry && fresh.retryCount < 3 && outcome.code !in Err.permanent
                if (retryable) {
                    dao.update(fresh.copy(status = "QUEUED", retryCount = fresh.retryCount + 1, error = outcome.code))
                    delay(3000L * (fresh.retryCount + 1))
                } else {
                    dao.setStatus(e.id, "FAILED", outcome.code)
                    _events.tryEmit(DownloadEvent(e.id, fresh.fileName, false, outcome.code))
                }
            }
        }
    }
}
