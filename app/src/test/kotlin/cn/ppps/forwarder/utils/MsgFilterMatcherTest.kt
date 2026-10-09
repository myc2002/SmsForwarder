package cn.ppps.forwarder.utils

import cn.ppps.forwarder.entity.MsgFilterConfig
import cn.ppps.forwarder.entity.MsgFilterEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MsgFilterMatcherTest {

    private fun cfg(mode: Int, vararg entries: MsgFilterEntry) =
        MsgFilterConfig(mode, mutableListOf("sms"), entries.toMutableList())

    private val blackA = MsgFilterEntry(1, MsgFilterEntry.LIST_BLACK, true, MsgFilterEntry.MODE_EQUALS, "10086")
    private val whiteAKey = MsgFilterEntry(2, MsgFilterEntry.LIST_WHITE, true, MsgFilterEntry.MODE_EQUALS, "10086", MsgFilterEntry.MODE_CONTAINS, "验证码\n账单")

    @Test
    fun blockNumberButAllowKeyword() {
        val c = cfg(MsgFilterConfig.MODE_BLACK_WHITE, blackA, whiteAKey)
        // A 的普通短信 → 拦截
        val r1 = MsgFilterMatcher.check(c, "sms", "10086", "流量包优惠活动")
        assertTrue(r1.blocked); assertEquals("black", r1.reason)
        // A 的含关键字短信 → 放行
        val r2 = MsgFilterMatcher.check(c, "sms", "10086", "您的验证码是 1234")
        assertFalse(r2.blocked); assertEquals("white", r2.reason)
        // 多关键字第二行
        assertFalse(MsgFilterMatcher.check(c, "sms", "10086", "本月账单已出").blocked)
        // 其它号码 → 默认放行
        val r3 = MsgFilterMatcher.check(c, "sms", "13800138000", "hello")
        assertFalse(r3.blocked); assertEquals("default", r3.reason)
        // 其它号码即使含关键字也不受白名单影响（白名单要求号码同时满足）
        assertEquals("default", MsgFilterMatcher.check(c, "sms", "95588", "验证码").reason)
    }

    @Test
    fun whiteHasPriorityRegardlessOfOrder() {
        val c = cfg(MsgFilterConfig.MODE_BLACK_WHITE, whiteAKey, blackA)
        assertFalse(MsgFilterMatcher.check(c, "sms", "10086", "验证码 9999").blocked)
        assertTrue(MsgFilterMatcher.check(c, "sms", "10086", "广告").blocked)
    }

    @Test
    fun phoneNormalization() {
        val black = MsgFilterEntry(1, MsgFilterEntry.LIST_BLACK, true, MsgFilterEntry.MODE_EQUALS, "138 0013 8000")
        val c = cfg(MsgFilterConfig.MODE_BLACK_WHITE, black)
        assertTrue(MsgFilterMatcher.check(c, "sms", "+8613800138000", "x").blocked)
        assertTrue(MsgFilterMatcher.check(c, "sms", "008613800138000", "x").blocked)
        assertTrue(MsgFilterMatcher.check(c, "sms", "8613800138000", "x").blocked)
        assertTrue(MsgFilterMatcher.check(c, "sms", "138-0013-8000", "x").blocked)
        assertFalse(MsgFilterMatcher.check(c, "sms", "13800138001", "x").blocked)
    }

    @Test
    fun keywordOnlyBlacklistAndCaseInsensitive() {
        val black = MsgFilterEntry(1, MsgFilterEntry.LIST_BLACK, true, MsgFilterEntry.MODE_ANY, "", MsgFilterEntry.MODE_CONTAINS, "退订")
        val black2 = MsgFilterEntry(2, MsgFilterEntry.LIST_BLACK, true, MsgFilterEntry.MODE_ANY, "", MsgFilterEntry.MODE_CONTAINS, "SALE")
        val c = cfg(MsgFilterConfig.MODE_BLACK_WHITE, black, black2)
        assertTrue(MsgFilterMatcher.check(c, "sms", "1069xxxx", "双11大促，回T退订").blocked)
        assertTrue(MsgFilterMatcher.check(c, "sms", "1069xxxx", "big sale now").blocked)
        assertFalse(MsgFilterMatcher.check(c, "sms", "1069xxxx", "正常通知").blocked)
    }

    @Test
    fun startEndRegex() {
        val start = MsgFilterEntry(1, MsgFilterEntry.LIST_BLACK, true, MsgFilterEntry.MODE_START, "106")
        val whiteRegex = MsgFilterEntry(2, MsgFilterEntry.LIST_WHITE, true, MsgFilterEntry.MODE_ANY, "", MsgFilterEntry.MODE_REGEX, "验证码[：:]?\\s*\\d{4,6}")
        val c = cfg(MsgFilterConfig.MODE_BLACK_WHITE, start, whiteRegex)
        assertTrue(MsgFilterMatcher.check(c, "sms", "10690000", "广告").blocked)
        assertFalse(MsgFilterMatcher.check(c, "sms", "10690000", "【某银行】验证码：123456").blocked)
        val end = MsgFilterEntry(3, MsgFilterEntry.LIST_BLACK, true, MsgFilterEntry.MODE_END, "8000")
        assertTrue(MsgFilterMatcher.check(cfg(1, end), "sms", "13800138000", "x").blocked)
        val senderRegex = MsgFilterEntry(4, MsgFilterEntry.LIST_BLACK, true, MsgFilterEntry.MODE_REGEX, "^95\\d{3}$")
        assertTrue(MsgFilterMatcher.check(cfg(1, senderRegex), "sms", "95588", "x").blocked)
        assertFalse(MsgFilterMatcher.check(cfg(1, senderRegex), "sms", "955880", "x").blocked)
    }

    @Test
    fun invalidRegexDoesNotCrash() {
        val bad = MsgFilterEntry(1, MsgFilterEntry.LIST_BLACK, true, MsgFilterEntry.MODE_ANY, "", MsgFilterEntry.MODE_REGEX, "([abc")
        assertFalse(MsgFilterMatcher.check(cfg(1, bad), "sms", "1", "abc").blocked)
        assertEquals("([abc", MsgFilterMatcher.regexError("ok\n([abc"))
    }

    @Test
    fun whiteOnlyMode() {
        val c = cfg(MsgFilterConfig.MODE_WHITE_ONLY, blackA, whiteAKey)
        assertFalse(MsgFilterMatcher.check(c, "sms", "10086", "验证码").blocked)
        assertTrue(MsgFilterMatcher.check(c, "sms", "10086", "广告").blocked)
        assertTrue(MsgFilterMatcher.check(c, "sms", "13800138000", "hello").blocked)
    }

    @Test
    fun offModeTypesAndDisabled() {
        assertFalse(MsgFilterMatcher.check(cfg(MsgFilterConfig.MODE_OFF, blackA), "sms", "10086", "x").blocked)
        // 只作用于 sms，通话不受影响
        assertEquals("none", MsgFilterMatcher.check(cfg(1, blackA), "call", "10086", "x").reason)
        val disabled = blackA.copy(enabled = false)
        assertFalse(MsgFilterMatcher.check(cfg(1, disabled), "sms", "10086", "x").blocked)
    }

    @Test
    fun emptyValueMeansAny() {
        // 号码=10086 内容模式“包含”但值为空 → 视为不限
        val e = MsgFilterEntry(1, MsgFilterEntry.LIST_BLACK, true, MsgFilterEntry.MODE_EQUALS, "10086", MsgFilterEntry.MODE_CONTAINS, "")
        assertTrue(MsgFilterMatcher.check(cfg(1, e), "sms", "10086", "任何内容").blocked)
    }
}
