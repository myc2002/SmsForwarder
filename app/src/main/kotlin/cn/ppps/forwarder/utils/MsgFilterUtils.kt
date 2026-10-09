package cn.ppps.forwarder.utils

import cn.ppps.forwarder.entity.MsgFilterConfig
import cn.ppps.forwarder.entity.MsgFilterEntry
import cn.ppps.forwarder.entity.MsgFilterLog
import cn.ppps.forwarder.entity.MsgFilterResult
import cn.ppps.forwarder.entity.MsgInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 黑白名单存储（SharedPreferences JSON，会随“一键换新机”一起导出/导入）
 */
object MsgFilterUtils {

    private const val TAG = "MsgFilterUtils"
    private const val MAX_LOGS = 200
    private val gson = Gson()
    private val lock = Any()
    @Volatile private var lastError: String = ""

    private var configJson: String by SharedPreference(SP_MSG_FILTER_CONFIG, "")
    private var logsJson: String by SharedPreference(SP_MSG_FILTER_LOGS, "")

    fun loadConfig(): MsgFilterConfig {
        return parseConfig(configJson) ?: MsgFilterConfig()
    }

    fun parseConfig(json: String?): MsgFilterConfig? {
        if (json.isNullOrBlank()) return null
        return try {
            val cfg = gson.fromJson(json, MsgFilterConfig::class.java) ?: return null
            // 兼容 Gson 反序列化缺字段时为 null 的情况
            @Suppress("SENSELESS_COMPARISON")
            if (cfg.types == null) cfg.types = mutableListOf("sms")
            @Suppress("SENSELESS_COMPARISON")
            if (cfg.entries == null) cfg.entries = mutableListOf()
            cfg.entries.removeAll { it.keyword == null || it.keyword.isBlank() }
            cfg.entries.forEach { e ->
                @Suppress("SENSELESS_COMPARISON")
                if (e.list == null) e.list = MsgFilterEntry.LIST_BLACK
            }
            cfg
        } catch (e: Exception) {
            Log.e(TAG, "parseConfig error: ${e.message}")
            null
        }
    }

    fun saveConfig(config: MsgFilterConfig) {
        synchronized(lock) {
            configJson = gson.toJson(config)
        }
    }

    fun exportJson(): String = gson.toJson(loadConfig())

    /** 用于匹配的内容：APP 通知为 标题 + 换行 + 内容 */
    fun matchContent(msgInfo: MsgInfo): String {
        return if (msgInfo.type == "app") msgInfo.simInfo + "\n" + msgInfo.content else msgInfo.content
    }

    fun check(msgInfo: MsgInfo): MsgFilterResult {
        return try {
            MsgFilterMatcher.check(loadConfig(), msgInfo.type, msgInfo.from, matchContent(msgInfo))
        } catch (e: Exception) {
            Log.e(TAG, "check error: ${e.message}")
            lastError = e.toString()
            MsgFilterResult(false, "error")
        }
    }

    fun describe(result: MsgFilterResult): String {
        val name = result.entry?.keyword ?: ""
        return when (result.reason) {
            "white" -> "命中白名单「$name」，转发"
            "black" -> "命中黑名单「$name」，不转发"
            "default" -> if (result.blocked) "未命中白名单（仅白名单模式）" else "未命中任何名单，正常转发"
            "off" -> "黑白名单总开关未开启，没有过滤"
            "type" -> "黑白名单没有勾选这类消息（短信/通话/APP通知），没有过滤"
            "error" -> "名单读取出错，没有过滤：$lastError"
            else -> "没有过滤"
        }
    }

    fun addLog(msgInfo: MsgInfo, result: MsgFilterResult) {
        synchronized(lock) {
            try {
                val list = getLogs().toMutableList()
                list.add(0, MsgFilterLog(System.currentTimeMillis(), msgInfo.type, msgInfo.from, matchContent(msgInfo).take(500), describe(result), result.blocked))
                while (list.size > MAX_LOGS) list.removeAt(list.size - 1)
                logsJson = gson.toJson(list)
            } catch (e: Exception) {
                Log.e(TAG, "addLog error: ${e.message}")
            }
        }
    }

    fun getLogs(): List<MsgFilterLog> {
        val json = logsJson
        if (json.isBlank()) return emptyList()
        return try {
            gson.fromJson<List<MsgFilterLog>>(json, object : TypeToken<List<MsgFilterLog>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearLogs() {
        synchronized(lock) { logsJson = "" }
    }
}
