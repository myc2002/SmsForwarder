package cn.ppps.forwarder.utils

import cn.ppps.forwarder.entity.MsgFilterConfig
import cn.ppps.forwarder.entity.MsgFilterEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MsgFilterMatcherTest {

    private fun cfg(vararg entries: MsgFilterEntry, mode: Int = MsgFilterConfig.MODE_BLACK_WHITE) =
        MsgFilterConfig(mode, mutableListOf("sms"), entries.toMutableList())

    private fun b(k: String) = MsgFilterEntry(1, MsgFilterEntry.LIST_BLACK, k)
    private fun w(k: String) = MsgFilterEntry(2, MsgFilterEntry.LIST_WHITE, k)

    @Test
    fun blockNumberButAllowKeyword() {
        val c = cfg(b("10086"), w("验证码"))
        assertTrue(MsgFilterMatcher.check(c, "sms", "10086", "流量包优惠").blocked)
        assertFalse(MsgFilterMatcher.check(c, "sms", "10086", "您的验证码是 1234").blocked)
        assertEquals("white", MsgFilterMatcher.check(c, "sms", "10086", "验证码 1234").reason)
        assertEquals("default", MsgFilterMatcher.check(c, "sms", "13800138000", "hello").reason)
    }

    @Test
    fun whiteHasPriorityRegardlessOfOrder() {
        val c = cfg(w("验证码"), b("10086"))
        assertFalse(MsgFilterMatcher.check(c, "sms", "10086", "验证码 9999").blocked)
        assertTrue(MsgFilterMatcher.check(c, "sms", "10086", "广告").blocked)
    }

    @Test
    fun keywordMatchesNumberOrContent() {
        val c = cfg(b("退订"), b("1069"))
        assertTrue(MsgFilterMatcher.check(c, "sms", "95588", "双11大促，回T退订").blocked) // 内容
        assertTrue(MsgFilterMatcher.check(c, "sms", "10690000", "正常").blocked)          // 号码(模糊)
        assertFalse(MsgFilterMatcher.check(c, "sms", "95588", "正常通知").blocked)
    }

    @Test
    fun caseInsensitiveAndPhoneNormalization() {
        assertTrue(MsgFilterMatcher.check(cfg(b("SALE")), "sms", "x", "big sale now").blocked)
        val c = cfg(b("138 0013 8000"))
        assertTrue(MsgFilterMatcher.check(c, "sms", "+8613800138000", "x").blocked)
        assertTrue(MsgFilterMatcher.check(c, "sms", "138-0013-8000", "x").blocked)
        assertFalse(MsgFilterMatcher.check(c, "sms", "13800138001", "x").blocked)
    }

    @Test
    fun regexCharactersAreLiteral() {
        val c = cfg(b("a.c"))
        assertTrue(MsgFilterMatcher.check(c, "sms", "x", "a.c").blocked)
        assertFalse(MsgFilterMatcher.check(c, "sms", "x", "abc").blocked)
    }

    @Test
    fun blankKeywordNeverMatches() {
        assertFalse(MsgFilterMatcher.check(cfg(b("  ")), "sms", "1", "abc").blocked)
    }

    @Test
    fun offAndTypes() {
        assertFalse(MsgFilterMatcher.check(cfg(b("10086"), mode = MsgFilterConfig.MODE_OFF), "sms", "10086", "x").blocked)
        assertEquals("none", MsgFilterMatcher.check(cfg(b("10086")), "call", "10086", "x").reason)
    }

    @Test
    fun whiteOnlyModeFromImport() {
        val c = cfg(b("10086"), w("验证码"), mode = MsgFilterConfig.MODE_WHITE_ONLY)
        assertFalse(MsgFilterMatcher.check(c, "sms", "10086", "验证码").blocked)
        assertTrue(MsgFilterMatcher.check(c, "sms", "13800138000", "hello").blocked)
    }
}
