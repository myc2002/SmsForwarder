package cn.ppps.forwarder.entity

import java.io.Serializable

/**
 * 黑白名单：每条只有一个关键字，同时匹配「号码」和「消息内容」，任一包含即命中。
 * 判定顺序：白名单优先放行 → 命中黑名单则拦截 → 都没命中则放行。
 *
 * 例：黑名单填 10086（屏蔽该号码全部短信），白名单填 验证码（含“验证码”的短信照常转发）。
 */
data class MsgFilterEntry(
    var id: Long = 0,
    var list: String = LIST_BLACK,          // white / black
    var keyword: String = "",
) : Serializable {

    companion object {
        const val LIST_WHITE = "white"
        const val LIST_BLACK = "black"
    }

    val isWhite: Boolean get() = list == LIST_WHITE
}

data class MsgFilterConfig(
    /** 0=关闭 1=黑名单+白名单(默认放行) 2=仅白名单(未命中白名单的全部拦截，仅导入的配置可能出现) */
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
    var blocked: Boolean = true,
) : Serializable
