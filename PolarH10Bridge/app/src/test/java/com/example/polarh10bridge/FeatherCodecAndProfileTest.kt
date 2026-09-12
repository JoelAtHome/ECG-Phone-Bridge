package com.example.polarh10bridge

import com.example.polarh10bridge.feather.FeatherBleContract
import com.example.polarh10bridge.feather.FeatherEcgPacket
import com.example.polarh10bridge.feather.FeatherIbiPacket
import com.example.polarh10bridge.feather.FeatherPacketCodec
import com.example.polarh10bridge.feather.FeatherPatientProfile
import com.example.polarh10bridge.feather.FeatherProfileStore
import com.example.polarh10bridge.feather.FeatherSimIbi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.abs

class FeatherPacketCodecTest {
    @Test
    fun ibi_roundTrip() {
        val original =
            FeatherIbiPacket(
                version = FeatherBleContract.PACKET_VERSION,
                timestampMs = 12_345L,
                ibiMs = listOf(800, 812, 790),
            )
        val decoded = FeatherPacketCodec.decodeIbi(FeatherPacketCodec.encodeIbi(original))
        assertEquals(original, decoded)
    }

    @Test
    fun ibi_rejectsWrongVersion() {
        val bytes =
            FeatherPacketCodec.encodeIbi(
                FeatherIbiPacket(version = 1, timestampMs = 1L, ibiMs = listOf(700)),
            )
        bytes[0] = 2
        assertNull(FeatherPacketCodec.decodeIbi(bytes))
    }

    @Test
    fun ecg_roundTrip_andUvToMv() {
        val original =
            FeatherEcgPacket(
                version = FeatherBleContract.PACKET_VERSION,
                timestampMs = 99L,
                sampleHz = 250,
                samplesUv = listOf(-1500, 0, 2200),
            )
        val decoded = FeatherPacketCodec.decodeEcg(FeatherPacketCodec.encodeEcg(original))
        assertEquals(original, decoded)
        assertEquals(listOf(-1.5, 0.0, 2.2), FeatherPacketCodec.samplesUvToMv(original.samplesUv))
    }

    @Test
    fun coeffs_and_control_json() {
        val encoded =
            FeatherPacketCodec.encodeCoeffsJson(
                mapOf("refractory_ms" to 400, "peak_end_frac" to 0.55, "gain" to null),
            )
        val decoded = FeatherPacketCodec.decodeCoeffsJson(encoded)!!
        assertEquals(1, (decoded["schema_version"] as Number).toInt())
        assertEquals(400, (decoded["refractory_ms"] as Number).toInt())
        assertEquals(0.55, (decoded["peak_end_frac"] as Number).toDouble(), 1e-9)
        assertNull(decoded["gain"])

        assertEquals(
            "start_stream",
            FeatherPacketCodec.decodeControl(FeatherPacketCodec.encodeControl("start_stream")),
        )
    }

    @Test
    fun advertised_name_filter() {
        assertTrue(FeatherBleContract.matchesAdvertisedName("HnH-Feather"))
        assertTrue(FeatherBleContract.matchesAdvertisedName("ECG-Box"))
        assertTrue(FeatherBleContract.matchesAdvertisedName("HnH-Feather-2"))
        assertFalse(FeatherBleContract.matchesAdvertisedName("Polar H10"))
        assertFalse(FeatherBleContract.matchesAdvertisedName(null))
    }
}

class FeatherProfileStoreTest {
    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    @Test
    fun save_load_list_and_demo() {
        val store = FeatherProfileStore(tmp.newFolder("feather_profiles"))
        val demo = store.ensureDemoProfile()
        assertEquals("demo", demo.profileId)
        assertEquals(400, (demo.coeffs["refractory_ms"] as Number).toInt())

        val again = store.ensureDemoProfile()
        assertEquals(demo.createdAt, again.createdAt)

        val payton =
            store.save(
                FeatherPatientProfile.defaultDemo(profileId = "payton", displayName = "Payton")
                    .copy(coeffs = demo.coeffs + ("fiducial_delay_ms" to 90)),
            )
        assertEquals(90, (store.load("payton")!!.coeffs["fiducial_delay_ms"] as Number).toInt())
        assertEquals(2, store.listProfiles().size)
        assertNotNull(payton.updatedAt)

        val ble = payton.coeffsForBleWrite()
        assertEquals(1, ble["schema_version"])
        assertTrue(ble.containsKey("refractory_ms"))
    }

    @Test
    fun profile_json_roundTrip() {
        val p = FeatherPatientProfile.defaultDemo()
        val back = FeatherPatientProfile.fromJsonObject(p.toJsonObject())
        assertEquals(p.profileId, back.profileId)
        assertEquals(p.coeffs["ibi_max_ms"], back.coeffs["ibi_max_ms"])
    }
}

class FeatherSimIbiTest {
    @Test
    fun rsa_swings_within_band_and_varies_over_breath() {
        val trough = FeatherSimIbi.nextIbiMs(elapsedSimMs = 1_250L, beatIndex = 1) // +RSA peak
        val crest = FeatherSimIbi.nextIbiMs(elapsedSimMs = 3_750L, beatIndex = 3) // −RSA trough
        assertTrue(trough in 560..1080)
        assertTrue(crest in 560..1080)
        assertTrue(abs(trough - crest) > 40)
    }

    @Test
    fun slow_wander_shifts_mean_across_windows() {
        val early = (0 until 20).map { FeatherSimIbi.nextIbiMs(it * 800L, it) }.average()
        val late = (0 until 20).map { FeatherSimIbi.nextIbiMs(21_000L + it * 800L, 100 + it) }.average()
        assertTrue(abs(early - late) > 5.0)
    }
}
