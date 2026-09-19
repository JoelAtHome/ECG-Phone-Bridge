package com.example.polarh10bridge

import com.example.polarh10bridge.feather.FeatherBleContract
import com.example.polarh10bridge.feather.FeatherLeadOffParser
import com.example.polarh10bridge.feather.FeatherLeadOffSnapshot
import com.example.polarh10bridge.feather.FeatherLeadOffTracker
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatherLeadOffTest {
    @Test
    fun parse_bareStatus() {
        val snap =
            FeatherLeadOffParser.parseStatusJson(
                """{"version":1,"streaming":true,"use_leads_off":true,"leads_off":true}""",
            )
        assertEquals(FeatherLeadOffSnapshot(useLeadsOff = true, leadsOff = true), snap)
        assertTrue(snap!!.hostsShouldWarn())
    }

    @Test
    fun parse_nestedQc() {
        val snap =
            FeatherLeadOffParser.parseStatusJson(
                """{"type":"qc","qc":{"use_leads_off":false,"leads_off":true,"bpm":0}}""",
            )
        assertEquals(FeatherLeadOffSnapshot(useLeadsOff = false, leadsOff = true), snap)
        assertFalse(snap!!.hostsShouldWarn())
    }

    @Test
    fun parse_ignoresCoeffsHeartbeat() {
        assertNull(
            FeatherLeadOffParser.parseStatusJson(
                """{"type":"coeffs","coeffs":{"refractory_ms":400}}""",
            ),
        )
        assertNull(FeatherLeadOffParser.parseStatusJson("""{"version":1,"streaming":true}"""))
    }

    @Test
    fun parse_leadsOffOnly_defaultsUseTrue() {
        val snap =
            FeatherLeadOffParser.parseStatusJson("""{"leads_off":false}""")
        assertEquals(FeatherLeadOffSnapshot(useLeadsOff = true, leadsOff = false), snap)
    }

    @Test
    fun tracker_emitsOnEdgeOnly() {
        val t = FeatherLeadOffTracker()
        val first =
            t.observe("""{"use_leads_off":true,"leads_off":false}""")
        assertNotNull(first)
        assertNull(t.observe("""{"use_leads_off":true,"leads_off":false}"""))
        val off = t.observe("""{"use_leads_off":true,"leads_off":true}""")
        assertEquals(true, off?.leadsOff)
        assertNull(t.observe("""{"type":"coeffs","coeffs":{}}"""))
        t.reset()
        assertNull(t.last())
        assertNotNull(t.observe("""{"use_leads_off":true,"leads_off":true}"""))
    }

    @Test
    fun statusJson_includesContractFields() {
        val json =
            FeatherLeadOffSnapshot(useLeadsOff = true, leadsOff = true)
                .toStatusJson(connected = true)
        val obj = JSONObject(json)
        assertEquals("status", obj.getString("type"))
        assertEquals("Feather lead-off", obj.getString("message"))
        assertTrue(obj.getBoolean("connected"))
        assertTrue(obj.getBoolean("use_leads_off"))
        assertTrue(obj.getBoolean("leads_off"))
        assertEquals(FeatherBleContract.SOURCE_DEVICE_WIRE, obj.getString("source_device"))
    }
}
