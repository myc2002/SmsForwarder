package cn.ppps.forwarder.utils

import cn.ppps.forwarder.entity.MsgFilterConfig
import cn.ppps.forwarder.entity.MsgFilterEntry
import cn.ppps.forwarder.entity.MsgFilterResult

/**
 * 黑白名单匹配逻辑（纯 Kotlin，不依赖 Android，便于单元测试）
 */
object MsgFilterMatcher {

    private val PHONE_SEPARATORS = Regex("[\\s\\-()（）]")

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

    /** 按行拆分，去掉空行 */
    fun splitValues(value: String?): List<String> {
        return (value ?: "").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** 关键字命中号码或内容任一处即可（不区分大小写；号码忽略 +86、空格、横线） */
    fun matchKeyword(keyword: String, from: String?, content: String?): Boolean {
        val k = keyword.trim().lowercase()
        if (k.isEmpty()) return false
        if ((content ?: "").lowercase().contains(k)) return true
        if ((from ?: "").lowercase().contains(k)) return true
        val nk = normalizeSender(k)
        return nk.isNotEmpty() && normalizeSender(from).lowercase().contains(nk)
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
        config.entries.firstOrNull { it.isWhite && matchKeyword(it.keyword, from, content) }?.let {
            return MsgFilterResult(false, "white", it)
        }
        // 2. 黑名单
        if (config.mode == MsgFilterConfig.MODE_BLACK_WHITE) {
            config.entries.firstOrNull { !it.isWhite && matchKeyword(it.keyword, from, content) }?.let {
                return MsgFilterResult(true, "black", it)
            }
            return MsgFilterResult(false, "default")
        }
        // 3. 仅白名单模式：未命中白名单即拦截
        return MsgFilterResult(true, "default")
    }
}
