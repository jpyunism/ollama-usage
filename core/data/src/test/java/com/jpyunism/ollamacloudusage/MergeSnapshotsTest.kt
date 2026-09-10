package com.jpyunism.ollamacloudusage

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeSnapshotsTest {

    private fun prefs(): SharedPreferences = mockk(relaxed = true)

    private fun store(now: () -> Long = { 1_000L }, prefsOverride: SharedPreferences? = null) =
        UsageHistoryStore(prefsOverride ?: prefs(), now)

    private fun snap(t: Long, s: Double = 10.0, w: Double = 20.0) =
        UsageSnapshot(timestampMillis = t, sessionPercent = s, weeklyPercent = w)

    @Test
    fun `merge agrega snapshots nuevos y ordena por timestamp`() {
        val existing = listOf(snap(2_000), snap(8_000))
        val imported = listOf(snap(1_000), snap(5_000))

        val result = store().mergeSnapshots(imported) { existing }

        assertEquals(listOf(1_000L, 2_000L, 5_000L, 8_000L), result.map { it.timestampMillis })
    }

    @Test
    fun `merge deduplica por timestamp exacto y el existente gana`() {
        val existing = listOf(snap(2_000, s = 11.0))
        val imported = listOf(snap(2_000, s = 99.0), snap(3_000))

        val result = store().mergeSnapshots(imported) { existing }

        assertEquals(2, result.size)
        assertEquals(11.0, result.first { it.timestampMillis == 2_000L }.sessionPercent, 1e-9)
    }

    @Test
    fun `merge con importado vacio devuelve el historial actual`() {
        val existing = listOf(snap(2_000), snap(3_000))

        val result = store().mergeSnapshots(emptyList()) { existing }

        assertEquals(existing, result)
    }

    @Test
    fun `merge con historial vacio importa todo ordenado`() {
        val imported = listOf(snap(9_000), snap(1_000), snap(5_000))

        val result = store().mergeSnapshots(imported) { emptyList() }

        assertEquals(listOf(1_000L, 5_000L, 9_000L), result.map { it.timestampMillis })
    }

    @Test
    fun `merge respeta el cap FIFO de 600`() {
        val existing = (0 until 400).map { snap(it.toLong()) }
        val imported = (400 until 1_100).map { snap(it.toLong()) }

        val result = store().mergeSnapshots(imported) { existing }

        assertEquals(UsageHistoryStore.MAX_SNAPSHOTS, result.size)
        // FIFO: quedan los 600 más recientes.
        assertEquals(
            ((1_100L - UsageHistoryStore.MAX_SNAPSHOTS) until 1_100L).map { it },
            result.map { it.timestampMillis },
        )
    }

    @Test
    fun `merge persiste el resultado`() {
        val historyKey = UsageHistoryStore.KEY_HISTORY
        val saved = mutableListOf<String>()
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { prefs.edit() } returns editor
        every { editor.putString(historyKey, any()) } answers {
            saved.add(secondArg()); editor
        }

        store(prefsOverride = prefs).mergeSnapshots(listOf(snap(5_000))) { emptyList() }

        assertEquals(1, saved.size)
        val parsed = UsageHistoryStore.parseSnapshots(saved[0])
        assertEquals(1, parsed.size)
        assertEquals(5_000L, parsed[0].timestampMillis)
    }
}