package com.walsoup.ditto

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.walsoup.ditto.core.audio.AudioFormatType
import com.walsoup.ditto.core.audio.HistoryItem
import com.walsoup.ditto.core.audio.VoiceFilter
import com.walsoup.ditto.data.HistoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Regression tests for the v1.1 review findings around history commits:
 *
 *  - WAV fallback after a failed AAC export must be recorded as WAV, never renamed or
 *    labelled M4A (the stored format is derived from the actual file).
 *  - Two commits in the same minute must not overwrite each other: the unique-name check
 *    and the move are serialised across threads.
 *  - A commit that lands while the initial load is still reading the history file must
 *    survive that load (merge, not overwrite).
 *  - clearHistory's completion callback fires only after the files are gone.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryManagerTest {

    private lateinit var context: Context

    @Before
    fun resetSharedState() {
        context = ApplicationProvider.getApplicationContext()
        // HistoryManager keeps process-wide shared flows in its companion; reset them so
        // every test starts from a clean slate.
        val companion = HistoryManager::class.java.getDeclaredField("Companion").apply {
            isAccessible = true
        }.get(null)
        companion.javaClass.declaredFields
            .filter { MutableStateFlow::class.java.isAssignableFrom(it.type) }
            .forEach {
                it.isAccessible = true
                it.set(companion, null)
            }
    }

    private fun newTake(name: String, size: Int, fill: Int): File =
        File(context.cacheDir, name).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(size) { fill.toByte() })
        }

    private fun awaitCondition(
        manager: HistoryManager,
        timeoutMs: Long = 10_000,
        condition: (List<HistoryItem>) -> Boolean
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition(manager.history.value)) return true
            Thread.sleep(50)
        }
        return false
    }

    @Test
    fun `wav fallback file is committed as WAV`() {
        val manager = HistoryManager(context)
        // A failed AAC export hands back the WAV; it must not be recorded or renamed as M4A.
        val src = newTake("take_raw.wav", 128, 1)
        val item = manager.commitRecording(
            sourceFile = src,
            durationMs = 1_000L,
            filter = VoiceFilter.RAW
        )
        assertNotNull(item)
        assertEquals(AudioFormatType.WAV, item!!.format)
        assertTrue(item.fileName.endsWith(".wav"))
        assertTrue(File(item.filePath).exists())
        assertFalse("source is moved, not copied", src.exists())
        manager.close()
    }

    @Test
    fun `m4a file is committed as M4A`() {
        val manager = HistoryManager(context)
        val src = newTake("take_raw.m4a", 96, 2)
        val item = manager.commitRecording(
            sourceFile = src,
            durationMs = 1_000L,
            filter = VoiceFilter.RAW
        )
        assertNotNull(item)
        assertEquals(AudioFormatType.M4A, item!!.format)
        assertTrue(item.fileName.endsWith(".m4a"))
        manager.close()
    }

    @Test
    fun `sequential commits in the same minute never overwrite`() {
        val manager = HistoryManager(context)
        val first = manager.commitRecording(newTake("a.wav", 64, 3), 1_000L, VoiceFilter.RAW)
        val second = manager.commitRecording(newTake("b.wav", 128, 4), 1_000L, VoiceFilter.RAW)
        assertNotNull(first)
        assertNotNull(second)
        assertTrue("same-minute commits must pick distinct names", first!!.filePath != second!!.filePath)
        assertEquals(64L, File(first.filePath).length())
        assertEquals(128L, File(second.filePath).length())
        manager.close()
    }

    @Test
    fun `concurrent commits never overwrite each other`() {
        val manager = HistoryManager(context)
        val threadCount = 8
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val results = java.util.Collections.synchronizedList(mutableListOf<HistoryItem>())
        repeat(threadCount) { index ->
            Thread {
                start.await()
                val item = manager.commitRecording(
                    sourceFile = newTake("concurrent_$index.wav", 32 + index, index),
                    durationMs = 1_000L,
                    filter = VoiceFilter.RAW
                )
                if (item != null) results.add(item)
                done.countDown()
            }.start()
        }
        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS))
        assertEquals(threadCount, results.size)
        val paths = results.map { it.filePath }.toSet()
        assertEquals("every commit gets its own destination", threadCount, paths.size)
        results.forEach { item ->
            assertTrue("committed file exists: ${item.filePath}", File(item.filePath).exists())
        }
        manager.close()
    }

    @Test
    fun `commit made during initial load survives the load`() {
        // Seed a non-trivial history file so the async load is still running when the
        // commit below lands.
        val recordings = File(context.filesDir, "recordings").apply { mkdirs() }
        val array = JSONArray()
        val seedCount = 200
        repeat(seedCount) { i ->
            val audio = File(recordings, "seed_$i.wav").apply { writeBytes(ByteArray(8) { 7 }) }
            array.put(
                JSONObject()
                    .put("id", "seed-$i")
                    .put("fileName", audio.name)
                    .put("filePath", audio.absolutePath)
                    .put("durationMs", 100L)
                    .put("format", AudioFormatType.WAV.name)
                    .put("filterName", VoiceFilter.RAW.name)
                    .put("createdAt", 1_000_000L + i)
            )
        }
        File(context.filesDir, "recordings_history.json").writeText(array.toString())

        val manager = HistoryManager(context) // kicks off the async load
        val fresh = manager.commitRecording(newTake("fresh.wav", 48, 9), 2_000L, VoiceFilter.RAW)
        assertNotNull(fresh)

        val freshId = fresh!!.id
        val ok = awaitCondition(manager) { items ->
            items.any { it.id == "seed-0" } && items.any { it.id == freshId }
        }
        assertTrue("a commit made during the initial load must not be wiped by it", ok)
        manager.close()
    }

    @Test
    fun `clearHistory callback fires after files are gone`() {
        val manager = HistoryManager(context)
        val item = manager.commitRecording(newTake("clear_me.wav", 32, 5), 1_000L, VoiceFilter.RAW)
        assertNotNull(item)
        assertTrue(manager.storageUsedBytes() > 0L)

        val cleared = CountDownLatch(1)
        manager.clearHistory { cleared.countDown() }
        // The callback is posted to the main looper; pump it until the writer thread has
        // finished deleting and posted it (Robolectric's main looper is paused in tests).
        val deadline = System.currentTimeMillis() + 10_000
        while (cleared.count > 0 && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        assertTrue("onCleared must fire", cleared.await(1, TimeUnit.SECONDS))
        assertFalse(File(item!!.filePath).exists())
        assertEquals(0L, manager.storageUsedBytes())
        manager.close()
    }
}
