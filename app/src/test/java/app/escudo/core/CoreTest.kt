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
        assertTrue(rules.any { it.pkg == "com.instagram.android" && "clips_viewer" in it.fragments })
        val yt = rules.first { it.pkg == "com.google.android.youtube" }
        assertTrue("reel_player" in yt.fragments)
        assertEquals(listOf("Shorts"), yt.selectedTabs)
        assertTrue(rules.any { it.pkg == "com.zhiliaoapp.musically" && it.wholeApp })
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

class CategoriesTest {
    @Test fun categorize() {
        assertEquals(AppCategory.SHORT_VIDEO, Categorizer.categorize("com.zhiliaoapp.musically", -1))
        assertEquals(AppCategory.SOCIAL, Categorizer.categorize("com.instagram.android", -1))
        assertEquals(AppCategory.GAMES, Categorizer.categorize("com.some.game", Categorizer.CAT_GAME))
        assertEquals(AppCategory.OTHER, Categorizer.categorize("com.some.notes", 7))
        // la elección del usuario manda
        assertEquals(AppCategory.SHORT_VIDEO, Categorizer.categorize("com.instagram.android", -1, mapOf("com.instagram.android" to "short")))
    }
}

class GameTest {
    @Test fun levels() {
        assertEquals(1, Game.levelFor(0).number)
        assertEquals(1, Game.levelFor(99).number)
        assertEquals(2, Game.levelFor(100).number)
        assertEquals(3, Game.levelFor(300).number)
        assertEquals(0.5f, Game.levelFor(200).progress(200), 0.001f)
        assertEquals("Inmune al scroll", Game.levelFor(1_000_000).title) // el título máximo se mantiene
        assertTrue(Game.avatarsFor(1).size == 2)
    }

    @Test fun streakWithFreezes() {
        var s = Game.Streak(0, 0, 0)
        repeat(7) { s = Game.updateStreak(s, true) }
        assertEquals(7, s.days); assertEquals(1, s.freezes)
        s = Game.updateStreak(s, false)           // usa comodín
        assertEquals(7, s.days); assertEquals(0, s.freezes); assertTrue(s.usedFreeze)
        s = Game.updateStreak(s, false)           // sin comodines: se reinicia
        assertEquals(0, s.days); assertEquals(7, s.best)
    }

    @Test fun dayOkAndChallenges() {
        val budgets = mapOf(AppCategory.SHORT_VIDEO to 30, AppCategory.SOCIAL to 60, AppCategory.GAMES to 0)
        val good = Game.DayStats(mapOf(AppCategory.SHORT_VIDEO to 25, AppCategory.GAMES to 300))
        val bad = Game.DayStats(mapOf(AppCategory.SHORT_VIDEO to 31))
        assertTrue(Game.dayOk(good, budgets))   // juegos sin presupuesto no cuenta
        assertFalse(Game.dayOk(bad, budgets))

        val under = Game.challengeById("short20")!!
        val s = Game.DayStats(mapOf(AppCategory.SHORT_VIDEO to 10))
        assertFalse(Game.isDone(under, s, dayClosed = false)) // aún no se da por bueno
        assertTrue(Game.isDone(under, s, dayClosed = true))
        assertTrue(Game.isDone(Game.challengeById("exit3")!!, Game.DayStats(reelExits = 3), false))
        assertFalse(Game.isDone(Game.challengeById("late12")!!, Game.DayStats(firstTrapMinute = 9 * 60), true))
        assertTrue(Game.isDone(Game.challengeById("late12")!!, Game.DayStats(firstTrapMinute = -1), true))

        val c1 = Game.pickChallenge("2026-11-16", setOf(AppCategory.SOCIAL), hasRules = true)
        val c2 = Game.pickChallenge("2026-11-16", setOf(AppCategory.SOCIAL), hasRules = true)
        assertEquals(c1, c2) // estable en el mismo día
        assertTrue(c1.cat == null || c1.cat == AppCategory.SOCIAL)
    }

    @Test fun achievementsAndTexts() {
        val got = Game.newAchievements(Game.Totals(reelExits = 1, bestStreak = 3), setOf("streak3"))
        assertEquals(listOf("exit1"), got.map { it.id })
        val t = Game.checkInText(0, 12, "Instagram", "")
        assertTrue(t.contains("12 min") && t.contains("Instagram"))
        assertTrue(Game.checkInText(3, 5, "TikTok", "").contains("tus cosas"))
        assertTrue(Game.equivalent(130).contains("peli"))
    }
}

class ShellErrorsTest {
    @org.junit.Test
    fun xiaomiSecuritySetting() {
        val out = "Exception occurred while executing 'disable-user':\njava.lang.SecurityException: Shell cannot change component state for com.miui.analytics/null to 3"
        org.junit.Assert.assertTrue(ShellErrors.explain(out, xiaomi = true).contains("Depuración USB (ajustes de seguridad)"))
        org.junit.Assert.assertFalse(ShellErrors.explain(out, xiaomi = false).contains("Xiaomi"))
    }

    @org.junit.Test
    fun otherErrors() {
        org.junit.Assert.assertTrue(ShellErrors.explain("Failure [DELETE_FAILED_USER_RESTRICTED]", true).contains("Xiaomi"))
        org.junit.Assert.assertTrue(ShellErrors.explain("Unknown package: x.y", false).contains("ya no está"))
        org.junit.Assert.assertTrue(ShellErrors.explain("", false).contains("Reinicia Shizuku"))
    }
}
