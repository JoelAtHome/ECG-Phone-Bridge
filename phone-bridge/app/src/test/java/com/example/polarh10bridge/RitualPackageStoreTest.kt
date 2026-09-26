package com.example.polarh10bridge

import com.example.polarh10bridge.ritual.RitualPackage
import com.example.polarh10bridge.ritual.RitualPackageStore
import com.example.polarh10bridge.ritual.RitualTransferReason
import com.example.polarh10bridge.ritual.RitualWireCodec
import com.example.polarh10bridge.ritual.toSummary
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RitualPackageStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun samplePackage(
        id: String,
        acked: Boolean = false,
        ecgSamples: Int = 100,
    ): RitualPackage {
        val mv = List(ecgSamples) { i -> (i % 10) * 0.001f }
        return RitualPackage(
            sessionId = id,
            mode = "record",
            kind = "ritual",
            sourceDevice = "FEATHER",
            emittedAt = "2026-09-15T12:00:00Z",
            durationS = 120.0,
            ibiMs = listOf(800, 810, 790, 805),
            ecgSampleHz = 250,
            ecgUv = RitualPackage.mvToUvShorts(mv),
            ecgScaleUvPerLsb = 1.0,
            ecgTruncated = false,
            rmssdJson =
                JSONObject()
                    .put("type", "rmssd")
                    .put("session_id", id)
                    .put("rmssd_ms", 42.0)
                    .put("rmssd_source", "bridge")
                    .toString(),
            sessionStateJson =
                JSONObject()
                    .put("type", "session_state")
                    .put("session_id", id)
                    .put("state", "completed")
                    .toString(),
            acked = acked,
        )
    }

    @Test
    fun roundTrip_save_get_preservesEcgAndIbi() {
        val store = RitualPackageStore(tmp.newFolder("rituals"))
        val original = samplePackage("sess-a", ecgSamples = 250)
        store.save(original)
        val loaded = store.get("sess-a")!!
        assertEquals(original.sessionId, loaded.sessionId)
        assertEquals(original.ibiMs, loaded.ibiMs)
        assertTrue(original.ecgUv.contentEquals(loaded.ecgUv))
        assertEquals(42.0, loaded.rmssdMs!!, 0.001)
        assertFalse(loaded.acked)
    }

    @Test
    fun profile_roundTrip_and_absentOnOlderFiles() {
        val store = RitualPackageStore(tmp.newFolder("rituals"))
        val named =
            samplePackage("named", ecgSamples = 4).copy(
                profileId = "patient-2",
                profileDisplayName = "Joel",
            )
        store.save(named)
        val loaded = store.get("named")!!
        assertEquals("patient-2", loaded.profileId)
        assertEquals("Joel", loaded.profileDisplayName)
        val legacy = samplePackage("legacy", ecgSamples = 4)
        assertFalse(legacy.toFileJson().has("profile_id"))
        assertNull(RitualPackage.fromFileJson(legacy.toFileJson()).profileDisplayName)
    }

    @Test
    fun delete_removesPackageAndKeepsTheRest() {
        val store = RitualPackageStore(tmp.newFolder("rituals"))
        store.save(samplePackage("keep", ecgSamples = 4))
        store.save(samplePackage("drop", ecgSamples = 4))
        assertTrue(store.delete("drop"))
        assertNull(store.get("drop"))
        assertEquals(listOf("keep"), store.summaries().map { it.sessionId })
    }

    @Test
    fun ritualList_isNewestFirstWithProfile() {
        val older =
            samplePackage("older", ecgSamples = 4).copy(
                profileId = "patient-1",
                profileDisplayName = "Patient 1",
            )
        val newer =
            samplePackage("newer", ecgSamples = 4).copy(
                profileDisplayName = "Joel",
                profileId = "joel",
            )
        val list = RitualWireCodec.ritualList(listOf(newer, older))
        assertEquals("ritual_list", list.getString("type"))
        val recordings = list.getJSONArray("recordings")
        assertEquals(2, recordings.length())
        assertEquals("newer", recordings.getJSONObject(0).getString("session_id"))
        assertEquals("Joel", recordings.getJSONObject(0).getString("profile_display_name"))
        assertEquals("patient-1", recordings.getJSONObject(1).getString("profile_id"))
        val summary = RitualWireCodec.sessionSummary(newer, RitualTransferReason.ManualSend)
        assertEquals("Joel", summary.getString("profile_display_name"))
        assertEquals("Joel", newer.toSummary().profileDisplayName)
    }

    @Test
    fun ritualUnavailable_namesTheMissingSession() {
        val msg = RitualWireCodec.ritualUnavailable("gone-1")
        assertEquals("ritual_unavailable", msg.getString("type"))
        assertEquals("gone-1", msg.getString("session_id"))
        assertEquals("not_found", msg.getString("reason"))
    }

    @Test
    fun count_matchesSavedPackages() {
        val store = RitualPackageStore(tmp.newFolder("rituals"), maxPackages = 5)
        assertEquals(0, store.count())
        store.save(samplePackage("sess-1", ecgSamples = 8))
        store.save(samplePackage("sess-2", ecgSamples = 8))
        assertEquals(2, store.count())
    }

    @Test
    fun prune_keepsLastFive() {
        val store = RitualPackageStore(tmp.newFolder("rituals"), maxPackages = 5)
        for (i in 1..7) {
            store.save(samplePackage("sess-$i", ecgSamples = 10))
        }
        val ids = store.list().map { it.sessionId }
        assertEquals(5, ids.size)
        assertEquals("sess-7", ids.first())
        assertNull(store.get("sess-1"))
        assertNull(store.get("sess-2"))
        assertNotNull(store.get("sess-3"))
    }

    @Test
    fun markAcked_and_latestUnacked() {
        val store = RitualPackageStore(tmp.newFolder("rituals"))
        store.save(samplePackage("old", acked = true, ecgSamples = 8))
        store.save(samplePackage("new", acked = false, ecgSamples = 8))
        assertEquals("new", store.latestUnacked()!!.sessionId)
        store.markAcked("new")
        assertNull(store.latestUnacked())
        assertTrue(store.get("new")!!.acked)
    }

    @Test
    fun wireCodec_buildsSummaryRmssdAndChunks() {
        val pkg = samplePackage("wire-1", ecgSamples = 500)
        val lines = RitualWireCodec.buildTransferLines(pkg, RitualTransferReason.DelayedPush)
        assertTrue(lines.size >= 4)
        val summary = JSONObject(lines[0])
        assertEquals("session_summary", summary.getString("type"))
        assertEquals("delayed_push", summary.getString("transfer_reason"))
        assertEquals("wire-1", summary.getString("session_id"))
        assertEquals("rmssd", JSONObject(lines[1]).getString("type"))
        assertEquals("session_state", JSONObject(lines[2]).getString("type"))
        val ibi = JSONObject(lines[3])
        assertEquals("ritual_chunk", ibi.getString("type"))
        assertEquals("ibi", ibi.getString("content"))
        val ecgLine = lines.first { JSONObject(it).optString("content") == "ecg" }
        val ecg = JSONObject(ecgLine)
        assertEquals("int16_uv_b64", ecg.getString("encoding"))
        assertTrue(ecg.getString("data").isNotBlank())
    }

    @Test
    fun wireCodec_autoPushPolicy() {
        assertTrue(RitualWireCodec.shouldAutoPush(null))
        assertTrue(RitualWireCodec.shouldAutoPush("flaretracker"))
        assertTrue(RitualWireCodec.shouldAutoPush("hertz_and_hearts"))
        assertFalse(RitualWireCodec.shouldAutoPush("vns_ta"))
        assertFalse(RitualWireCodec.shouldAutoPush("ecg_box_tuner"))
    }

    @Test
    fun sizeSanity_oneMinuteFeatherCompactUnder200kb() {
        // ~250 Hz * 60 s = 15000 samples * 2 bytes ≈ 30 KB raw (+ JSON overhead)
        val pkg = samplePackage("size-1", ecgSamples = 15_000)
        val json = pkg.toFileJson().toString()
        assertTrue("package too large: ${json.length}", json.length < 200_000)
    }

    @Test
    fun recordStop_buildsRitualPackageWithEcg() {
        val ctl = BridgeSessionController()
        ctl.start(
            mode = BridgeSessionMode.Record,
            kind = BridgeSessionKind.Ritual,
            nowElapsedMs = 1_000L,
            sourceDevice = "POLAR_H10",
        )
        ctl.onRrMs(800, 1_500L)
        ctl.onRrMs(810, 2_310L)
        ctl.onEcgMv(130, listOf(0.1f, -0.05f, 0.2f))
        val stop = ctl.stop("POLAR_H10", nowElapsedMs = 60_000L)
        assertNotNull(stop.ritualPackage)
        assertEquals(3, stop.ritualPackage!!.ecgUv.size)
        assertEquals(2, stop.ritualPackage!!.ibiMs.size)
        assertNull(stop.ritualPackage!!.profileDisplayName)
    }

    @Test
    fun recordStop_storesPatientProfileSnapshot() {
        val ctl = BridgeSessionController()
        ctl.start(
            mode = BridgeSessionMode.Record,
            kind = BridgeSessionKind.Ritual,
            nowElapsedMs = 1_000L,
            sourceDevice = "FEATHER",
        )
        ctl.onRrMs(800, 1_500L)
        ctl.onRrMs(820, 2_320L)
        val stop =
            ctl.stop(
                "FEATHER",
                nowElapsedMs = 181_000L,
                profileId = "patient-2",
                profileDisplayName = " Joel ",
            )
        assertEquals("patient-2", stop.ritualPackage!!.profileId)
        assertEquals("Joel", stop.ritualPackage!!.profileDisplayName)
        assertEquals(180.0, stop.ritualPackage!!.durationS, 0.001)
    }

    @Test
    fun streamStop_doesNotBuildRitualPackage() {
        val ctl = BridgeSessionController()
        ctl.start(
            mode = BridgeSessionMode.Stream,
            kind = BridgeSessionKind.Session,
            nowElapsedMs = 1_000L,
            sourceDevice = "POLAR_H10",
        )
        ctl.onRrMs(800, 1_500L)
        ctl.onEcgMv(130, listOf(0.1f))
        val stop = ctl.stop("POLAR_H10", nowElapsedMs = 30_000L)
        assertNull(stop.ritualPackage)
    }
}
