package cn.ppps.forwarder.entity

import java.io.Serializable

/**
 * 黑白名单（参考 MessageFilter 的双名单机制）
 *
 * 每条名单项由「号码条件」和「内容条件」组成，两者同时满足才算命中（未设置的条件视为不限）。
 * 判定顺序：白名单优先 —— 命中白名单直接放行；否则命中黑名单则拦截；都没命中按模式决定。
 *
 * 例：黑名单「号码=A」+ 白名单「号码=A 且 内容包含 关键字」
 *     → A 的其它短信全部屏蔽，A 发来的包含关键字的短信照常转发。
 */
data class MsgFilterEntry(
    var id: Long = 0,
    var list: String = LIST_BLACK,          // white / black
    var enabled: Boolean = true,
    var senderMode: String = MODE_ANY,      // any / contains / equals / start / end / regex
    var sender: String = "",                // 多个值一行一个，任一行命中即可
    var contentMode: String = MODE_ANY,
    var content: String = "",
    var note: String = "",
) : Serializable {

    companion object {
        const val LIST_WHITE = "white"
        const val LIST_BLACK = "black"

        const val MODE_ANY = "any"
        const val MODE_CONTAINS = "contains"
        const val MODE_EQUALS = "equals"
        const val MODE_START = "start"
        const val MODE_END = "end"
        const val MODE_REGEX = "regex"

        val MODES = listOf(MODE_ANY, MODE_CONTAINS, MODE_EQUALS, MODE_START, MODE_END, MODE_REGEX)
    }

    val isWhite: Boolean get() = list == LIST_WHITE
}

data class MsgFilterConfig(
    /** 0=关闭 1=黑名单+白名单(默认放行) 2=仅白名单(未命中白名单的全部拦截) */
    var mode: Int = MODE_OFF,
    /** 生效的消息类型：sms / call / app */
    var types: MutableList<String> = mutableListOf("sms"),
    var entries: MutableList<MsgFilterEntry> = mutableListOf(),
) : Serializable {
    companion object {
        const val MODE_OFF = 0
        const val MODE_BLACK_WHITE = 1
        const val MODE_WHITE_ONLY = 2
    }
}

data class MsgFilterResult(
    val blocked: Boolean,
    /** none=未启用/不在范围 white=命中白名单 black=命中黑名单 default=未命中任何名单 */
    val reason: String,
    val entry: MsgFilterEntry? = null,
)

data class MsgFilterLog(
    var time: Long = 0,
    var type: String = "",
    var from: String = "",
    var content: String = "",
    var reason: String = "",
) : Serializable
