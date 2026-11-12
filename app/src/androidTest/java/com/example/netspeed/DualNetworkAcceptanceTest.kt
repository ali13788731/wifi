package com.example.netspeed

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.netspeed.core.database.AppDatabase
import com.example.netspeed.core.database.DownloadEntity
import com.example.netspeed.core.downloader.DownloadJob
import com.example.netspeed.core.downloader.DownloadStatus
import com.example.netspeed.core.downloader.JobConfig
import com.example.netspeed.core.downloader.JobOutcome
import com.example.netspeed.core.downloader.LiveProgress
import com.example.netspeed.core.downloader.NetStats
import com.example.netspeed.core.network.NetKind
import com.example.netspeed.core.network.NetworkBindingManager
import com.example.netspeed.core.network.NetworkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * REAL-DEVICE acceptance test (cannot run on an emulator without cellular). Run with:
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.url=https://your.server/100MB.bin
 * Wi-Fi connected + mobile data ON are required; otherwise the test is skipped (not passed).
 * The server must support HTTP Range and serve >= 100 MB.
 */
@RunWith(AndroidJUnit4::class)
class DualNetworkAcceptanceTest {

    @Test fun bothNetworksTransferBytesAndFileIsValid() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val url = args.getString("url")
        assumeTrue("pass -Pandroid.testInstrumentationRunnerArguments.url=...", url != null)
        val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext

        val net = NetworkManager(ctx)
        net.acquire()
        var waited = 0
        while (!net.state.value.dualReady && waited < 15) { delay(1000); waited++ }
        assumeTrue("Wi-Fi AND mobile data must both be usable", net.state.value.dualReady)

        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        val temp = File(ctx.cacheDir, "acceptance.part").also { it.delete() }
        val id = db.downloads().insert(DownloadEntity(url = url!!, fileName = "acc.bin", tempPath = temp.absolutePath))
        val entity = db.downloads().get(id)!!
        val progress = MutableStateFlow(
            LiveProgress(id, DownloadStatus.QUEUED, -1, 0, NetStats(NetKind.WIFI), NetStats(NetKind.MOBILE))
        )

        var sawBoth = false
        val watcher = launch(kotlinx.coroutines.Dispatchers.Default) {
            progress.collect { p -> if (p.wifi.speedBps > 0 && p.mobile.speedBps > 0) sawBoth = true }
        }
        val out = DownloadJob(entity, net, NetworkBindingManager(), db.downloads(), db.segments(), JobConfig(), progress).run()
        watcher.cancel()
        net.release()

        assertTrue("job failed: $out", out is JobOutcome.Done)
        out as JobOutcome.Done
        val expected = db.downloads().get(id)!!.totalBytes
        assertTrue("file must be >= 100 MB for this test", expected >= 100L * 1024 * 1024)
        assertTrue("Wi-Fi counter must increase", out.wifiBytes > 0)
        assertTrue("Mobile counter must increase", out.mobileBytes > 0)
        assertTrue("both networks must have been transferring at the same moment", sawBoth)
        assertEquals("final size must match", expected, out.file.length())
        assertEquals("counters must add up to the file size", expected, out.wifiBytes + out.mobileBytes)
    }
}
