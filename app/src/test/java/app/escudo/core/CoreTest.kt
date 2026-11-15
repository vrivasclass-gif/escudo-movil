package app.escudo.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMatcherTest {
    private val m = DomainMatcher.Builder()
        .block("tracker.com").block("ads.example.org").block("Telemetry.Vendor.NET.")
        .allow("good.tracker.com")
        .build()

    @Test fun exactAndSubdomains() {
        assertTrue(m.isBlocked("tracker.com"))
        assertTrue(m.isBlocked("a.b.tracker.com"))
        assertTrue(m.isBlocked("ads.example.org"))
        assertTrue(m.isBlocked("x.ads.example.org"))
        assertTrue(m.isBlocked("telemetry.vendor.net"))
    }

    @Test fun notBlocked() {
        assertFalse(m.isBlocked("example.org"))
        assertFalse(m.isBlocked("nottracker.com"))
        assertFalse(m.isBlocked("tracker.com.evil.io"))
        assertFalse(m.isBlocked("com"))
        assertFalse(m.isBlocked(""))
    }

    @Test fun allowlistWins() {
        assertFalse(m.isBlocked("good.tracker.com"))
        assertFalse(m.isBlocked("cdn.good.tracker.com"))
        assertTrue(m.isBlocked("bad.tracker.com"))
    }

    @Test fun dedupAndSize() {
        val b = DomainMatcher.Builder()
        repeat(5000) { b.block("d$it.com"); b.block("d$it.com") }
        val mm = b.build()
        assertEquals(5000, mm.size)
        assertTrue(mm.isBlocked("x.d4999.com"))
    }
}

class ListParserTest {
    @Test fun formats() {
        assertEquals("tracker.com", ListParser.parseLine("0.0.0.0 tracker.com"))
        assertEquals("tracker.com", ListParser.parseLine("127.0.0.1\ttracker.com  # comentario"))
        assertEquals("tracker.com", ListParser.parseLine("||tracker.com^"))
        assertEquals("tracker.com", ListParser.parseLine("*.tracker.com"))
        assertEquals("tracker.com", ListParser.parseLine("TRACKER.com"))
        assertNull(ListParser.parseLine("# comentario"))
        assertNull(ListParser.parseLine("! adblock"))
        assertNull(ListParser.parseLine("[Adblock Plus]"))
        assertNull(ListParser.parseLine("@@||ok.com^"))
        assertNull(ListParser.parseLine("||tracker.com^\$third-party"))
        assertNull(ListParser.parseLine("127.0.0.1 localhost"))
        assertNull(ListParser.parseLine("0.0.0.0 0.0.0.0"))
        assertNull(ListParser.parseLine(""))
    }
}

class DnsTest {
    /** Consulta DNS real: id 0x1234, RD, pregunta A de "ads.tracker.com" */
    private fun query(name: String, type: Int = Dns.TYPE_A): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0))
        for (label in name.split('.')) { out.write(label.length); out.write(label.toByteArray()) }
        out.write(0); out.write(type ushr 8); out.write(type and 0xFF); out.write(0); out.write(1)
        return out.toByteArray()
    }

    private fun ipUdp(payload: ByteArray): ByteArray {
        val req = UdpPacket(byteArrayOf(10, 111, 0, 2), byteArrayOf(10, 111, 0, 1), 40000, 53, payload)
        // construimos el paquete "de ida" reutilizando buildUdpReply con origen/destino invertidos
        val reversed = UdpPacket(req.dstIp, req.srcIp, req.dstPort, req.srcPort, payload)
        return IpPacket.buildUdpReply(reversed, payload)
    }

    @Test fun parseIpUdpDns() {
        val pkt = ipUdp(query("Ads.Tracker.com"))
        val udp = IpPacket.parseUdp(pkt, pkt.size)!!
        assertEquals(53, udp.dstPort)
        assertEquals(40000, udp.srcPort)
        assertArrayEquals(byteArrayOf(10, 111, 0, 1), udp.dstIp)
        val q = Dns.parseQuestion(udp.payload)!!
        assertEquals("ads.tracker.com", q.name)
        assertEquals(Dns.TYPE_A, q.type)
        assertEquals(0x1234, q.id)
        assertEquals(0, IpPacket.checksum(pkt, 0, 20)) // checksum IP válido
    }

    @Test fun blockedResponseA() {
        val qb = query("ads.tracker.com")
        val q = Dns.parseQuestion(qb)!!
        val r = Dns.blockedResponse(qb, q)
        assertEquals(0x1234, IpPacket.u16(r, 0))
        assertTrue(IpPacket.u16(r, 2) and 0x8000 != 0)
        assertEquals(0, IpPacket.u16(r, 2) and 0xF)
        assertEquals(1, IpPacket.u16(r, 6))
        assertEquals(60, Dns.minTtl(r))
        assertNull(Dns.parseQuestion(r)) // es una respuesta
    }

    @Test fun blockedResponseOther() {
        val qb = query("ads.tracker.com", Dns.TYPE_HTTPS)
        val r = Dns.blockedResponse(qb, Dns.parseQuestion(qb)!!)
        assertEquals(Dns.RCODE_NXDOMAIN, IpPacket.u16(r, 2) and 0xF)
        assertEquals(0, IpPacket.u16(r, 6))
    }

    @Test fun cacheRewritesIdAndExpires() {
        var now = 0L
        val c = DnsCache(clock = { now })
        val qb = query("example.com")
        val resp = Dns.blockedResponse(qb, Dns.parseQuestion(qb)!!) // TTL 60
        c.put("example.com", 1, resp)
        val hit = c.get("example.com", 1, 0x9999)
        assertNotNull(hit)
        assertEquals(0x9999, IpPacket.u16(hit!!, 0))
        now = 61_000
        assertNull(c.get("example.com", 1, 1))
    }

    @Test fun rejectsGarbage() {
        assertNull(IpPacket.parseUdp(ByteArray(10), 10))
        assertNull(Dns.parseQuestion(ByteArray(5)))
        val bad = query("a.com").copyOf(14)
        assertNull(Dns.parseQuestion(bad))
    }
}

class FeedRulesTest {
    @Test fun parsesDefaults() {
        val rules = FeedRules.parse(FeedRules.DEFAULT_TEXT)
        assertTrue(rules.any { it.pkg == "com.instagram.android" && it.viewId == "clips_viewer" })
        assertFalse(rules.any { it.pkg == "com.zhiliaoapp.musically" }) // comentada
        assertFalse(rules.any { it.viewId == "clips_tab" }) // botón siempre visible: causaba cierres
        assertTrue(rules.all { it.action == FeedAction.NOTICE }) // por defecto nunca se cierra nada
        val r = FeedRules.parse("com.zhiliaoapp.musically | * | inicio | TikTok").single()
        assertTrue(r.wholeApp); assertEquals(FeedAction.HOME, r.action); assertEquals("TikTok", r.label)
        assertEquals(FeedAction.BACK, FeedRules.parse("a.b | x | atras").single().action)
        assertEquals(FeedAction.NOTICE, FeedRules.parse("a.b | x").single().action)
    }
}

class DomainInfoTest {
    @Test fun explains() {
        assertEquals("Google (Firebase Analytics)", DomainInfo.explain("app-measurement.com").owner)
        assertTrue(DomainInfo.explain("graph.facebook.com").purpose.contains("Instagram"))
        assertTrue(DomainInfo.explain("ads.example.com").purpose.startsWith("Publicidad"))
        assertTrue(DomainInfo.explain("telemetry.vendor.net").purpose.startsWith("Analítica"))
        assertTrue(DomainInfo.explain("roads.example.com").purpose.startsWith("Aparece"))
    }
}

class TrackerTest {
    @Test fun detects() {
        val found = TrackerSignatures.detect(listOf(
            "com.appsflyer.SingleInstallBroadcastReceiver",
            "com.google.android.gms.ads.AdActivity",
            "com.myapp.MainActivity",
        )).map { it.name }
        assertEquals(listOf("Google AdMob", "AppsFlyer"), found)
    }
}
