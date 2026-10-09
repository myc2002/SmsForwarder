package cn.ppps.forwarder.utils

import cn.ppps.forwarder.entity.MsgFilterConfig
import cn.ppps.forwarder.entity.MsgFilterEntry
import cn.ppps.forwarder.entity.MsgFilterResult
import java.util.regex.Pattern

/**
 * 黑白名单匹配逻辑（纯 Kotlin，不依赖 Android，便于单元测试）
 */
object MsgFilterMatcher {

    private val PHONE_SEPARATORS = Regex("[\\s\\-()（）]")
    private val patternCache = HashMap<String, Pattern?>()

    /** 号码归一化：去掉空格/横线/括号，去掉 +86 / 0086 / 86(后接11位手机号) 前缀 */
    fun normalizeSender(raw: String?): String {
        var s = (raw ?: "").replace(PHONE_SEPARATORS, "")
        when {
            s.startsWith("+86") -> s = s.substring(3)
            s.startsWith("0086") -> s = s.substring(4)
            s.length == 13 && s.startsWith("86") && s.all { it.isDigit() } -> s = s.substring(2)
        }
        return s
    }

    fun splitValues(value: String?): List<String> {
        return (value ?: "").split('\n').map { it.trim().removeSuffix("\r") }.filter { it.isNotEmpty() }
    }

    private fun compile(regex: String): Pattern? {
        synchronized(patternCache) {
            if (patternCache.containsKey(regex)) return patternCache[regex]
            val p = try {
                Pattern.compile(regex, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
            } catch (e: Exception) {
                null
            }
            if (patternCache.size > 500) patternCache.clear()
            patternCache[regex] = p
            return p
        }
    }

    /** 校验正则，返回错误信息，合法返回 null */
    fun regexError(value: String?): String? {
        for (line in splitValues(value)) {
            try {
                Pattern.compile(line)
            } catch (e: Exception) {
                return line
            }
        }
        return null
    }

    /**
     * 单个条件：mode=any 恒为 true；值为空也视为不限；多行任一命中
     */
    fun matchField(mode: String, value: String, target: String?, isSender: Boolean): Boolean {
        if (mode == MsgFilterEntry.MODE_ANY) return true
        val values = splitValues(value)
        if (values.isEmpty()) return true
        val raw = target ?: ""
        return values.any { v ->
            if (mode == MsgFilterEntry.MODE_REGEX) {
                val p = compile(v) ?: return@any false
                p.matcher(raw).find() || (isSender && p.matcher(normalizeSender(raw)).find())
            } else {
                val t = (if (isSender) normalizeSender(raw) else raw).lowercase()
                val c = (if (isSender) normalizeSender(v) else v).lowercase()
                when (mode) {
                    MsgFilterEntry.MODE_CONTAINS -> t.contains(c)
                    MsgFilterEntry.MODE_EQUALS -> t == c
                    MsgFilterEntry.MODE_START -> t.startsWith(c)
                    MsgFilterEntry.MODE_END -> t.endsWith(c)
                    else -> false
                }
            }
        }
    }

    fun matchEntry(entry: MsgFilterEntry, from: String?, content: String?): Boolean {
        if (!entry.enabled) return false
        return matchField(entry.senderMode, entry.sender, from, true) &&
                matchField(entry.contentMode, entry.content, content, false)
    }

    /**
     * @param type 消息类型 sms/call/app
     * @param from 号码（APP 通知为包名）
     * @param content 用于匹配的内容（APP 通知为 标题+换行+内容）
     */
    fun check(config: MsgFilterConfig, type: String, from: String?, content: String?): MsgFilterResult {
        if (config.mode == MsgFilterConfig.MODE_OFF || !config.types.contains(type)) {
            return MsgFilterResult(false, "none")
        }
        // 1. 白名单优先
        config.entries.firstOrNull { it.isWhite && matchEntry(it, from, content) }?.let {
            return MsgFilterResult(false, "white", it)
        }
        // 2. 黑名单
        if (config.mode == MsgFilterConfig.MODE_BLACK_WHITE) {
            config.entries.firstOrNull { !it.isWhite && matchEntry(it, from, content) }?.let {
                return MsgFilterResult(true, "black", it)
            }
            return MsgFilterResult(false, "default")
        }
        // 3. 仅白名单模式：未命中白名单即拦截
        return MsgFilterResult(true, "default")
    }
}
