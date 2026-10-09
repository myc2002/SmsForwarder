package cn.ppps.forwarder.fragment

import android.graphics.Color
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import cn.ppps.forwarder.R
import cn.ppps.forwarder.core.BaseFragment
import cn.ppps.forwarder.databinding.FragmentMsgFilterBinding
import cn.ppps.forwarder.entity.MsgFilterConfig
import cn.ppps.forwarder.entity.MsgFilterEntry
import cn.ppps.forwarder.utils.MsgFilterMatcher
import cn.ppps.forwarder.utils.MsgFilterUtils
import cn.ppps.forwarder.utils.XToastUtils
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xui.widget.actionbar.TitleBar
import com.xuexiang.xui.widget.dialog.materialdialog.MaterialDialog
import com.xuexiang.xutil.system.ClipboardUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Page(name = "黑白名单")
@Suppress("PrivatePropertyName", "SetTextI18n")
class MsgFilterFragment : BaseFragment<FragmentMsgFilterBinding?>() {

    private lateinit var config: MsgFilterConfig
    private var loading = false

    override fun viewBindingInflate(inflater: LayoutInflater, container: ViewGroup): FragmentMsgFilterBinding {
        return FragmentMsgFilterBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        val titleBar = super.initTitle()!!.setImmersive(false)
        titleBar.setTitle(R.string.msg_filter_title)
        return titleBar
    }

    override fun initViews() {
        config = MsgFilterUtils.loadConfig()
        bindConfig()
    }

    private fun bindConfig() {
        loading = true
        val b = binding!!
        b.sbEnable.isChecked = config.mode != MsgFilterConfig.MODE_OFF
        b.cbTypeSms.isChecked = config.types.contains("sms")
        b.cbTypeCall.isChecked = config.types.contains("call")
        b.cbTypeApp.isChecked = config.types.contains("app")
        loading = false
        renderLists()
    }

    override fun initListeners() {
        val b = binding!!
        b.sbEnable.setOnCheckedChangeListener { _, isChecked ->
            if (loading) return@setOnCheckedChangeListener
            config.mode = if (isChecked) MsgFilterConfig.MODE_BLACK_WHITE else MsgFilterConfig.MODE_OFF
            save()
        }
        val typeListener = View.OnClickListener {
            if (!loading) {
                val types = mutableListOf<String>()
                if (b.cbTypeSms.isChecked) types.add("sms")
                if (b.cbTypeCall.isChecked) types.add("call")
                if (b.cbTypeApp.isChecked) types.add("app")
                config.types = types
                save()
            }
        }
        b.cbTypeSms.setOnClickListener(typeListener)
        b.cbTypeCall.setOnClickListener(typeListener)
        b.cbTypeApp.setOnClickListener(typeListener)

        b.btnAddWhite.setOnClickListener { addKeyword(b.etWhite, MsgFilterEntry.LIST_WHITE) }
        b.btnAddBlack.setOnClickListener { addKeyword(b.etBlack, MsgFilterEntry.LIST_BLACK) }
        b.etWhite.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { addKeyword(b.etWhite, MsgFilterEntry.LIST_WHITE); true } else false }
        b.etBlack.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { addKeyword(b.etBlack, MsgFilterEntry.LIST_BLACK); true } else false }

        b.btnTest.setOnClickListener { showTestDialog() }
        b.btnLogs.setOnClickListener { showLogsDialog() }
        b.btnExport.setOnClickListener {
            ClipboardUtils.copyText(MsgFilterUtils.exportJson())
            XToastUtils.success("名单已复制到剪贴板")
        }
        b.btnImport.setOnClickListener { showImportDialog() }
    }

    private fun save() {
        MsgFilterUtils.saveConfig(config)
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    /** 输入框里可以一次填多个，用换行、逗号分隔 */
    private fun addKeyword(et: EditText, list: String) {
        val words = et.text.toString().split('\n', ',', '，', '、', ';', '；').map { it.trim() }.filter { it.isNotEmpty() }
        if (words.isEmpty()) {
            XToastUtils.error("请先输入号码或关键字")
            return
        }
        var added = 0
        var base = System.currentTimeMillis()
        for (w in words) {
            if (config.entries.any { it.list == list && it.keyword.equals(w, ignoreCase = true) }) continue
            config.entries.add(MsgFilterEntry(base++, list, w))
            added++
        }
        et.setText("")
        if (config.mode == MsgFilterConfig.MODE_OFF) {
            config.mode = MsgFilterConfig.MODE_BLACK_WHITE
            bindConfig()
            XToastUtils.info("已自动开启黑白名单")
        }
        if (added == 0) XToastUtils.info("已经在名单里了")
        save()
        renderLists()
    }

    private fun renderLists() {
        val b = binding!!
        b.llWhite.removeAllViews()
        b.llBlack.removeAllViews()
        val white = config.entries.filter { it.isWhite }
        val black = config.entries.filter { !it.isWhite }
        b.tvWhiteTitle.text = "白名单（一定转发）· ${white.size}"
        b.tvBlackTitle.text = "黑名单（不转发）· ${black.size}"
        white.forEach { b.llWhite.addView(entryView(it)) }
        black.forEach { b.llBlack.addView(entryView(it)) }
    }

    private fun entryView(entry: MsgFilterEntry): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        row.addView(TextView(ctx).apply {
            text = entry.keyword
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(ctx).apply {
            text = "删除"
            setTextColor(Color.parseColor("#D93025"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(12), dp(10), dp(4), dp(10))
            setOnClickListener {
                config.entries.remove(entry)
                save()
                renderLists()
            }
        })
        return row
    }

    private fun showTestDialog() {
        val ctx = requireContext()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        val rg = RadioGroup(ctx).apply { orientation = RadioGroup.HORIZONTAL }
        val types = listOf("sms" to "短信", "call" to "通话", "app" to "APP通知")
        types.forEachIndexed { i, (_, label) ->
            rg.addView(RadioButton(ctx).apply { id = 1000 + i; text = label })
        }
        rg.check(1000)
        val etFrom = EditText(ctx).apply { hint = "号码 / 包名"; inputType = InputType.TYPE_CLASS_TEXT }
        val etContent = EditText(ctx).apply { hint = "短信内容"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        val tvResult = TextView(ctx).apply { setPadding(0, dp(8), 0, 0) }
        box.addView(rg); box.addView(etFrom); box.addView(etContent); box.addView(tvResult)

        MaterialDialog.Builder(ctx).title("测试黑白名单").customView(box, true).autoDismiss(false)
            .positiveText(R.string.action_test).negativeText(R.string.action_back)
            .onNegative { d, _ -> d.dismiss() }
            .onPositive { _, _ ->
                val type = types[(rg.checkedRadioButtonId - 1000).coerceIn(0, 2)].first
                // 测试时不受“启用”开关影响，直接按名单判断
                val cfg = config.copy(mode = if (config.mode == MsgFilterConfig.MODE_OFF) MsgFilterConfig.MODE_BLACK_WHITE else config.mode, types = mutableListOf(type))
                val r = MsgFilterMatcher.check(cfg, type, etFrom.text.toString(), etContent.text.toString())
                tvResult.text = (if (r.blocked) "✗ 不转发\n" else "✓ 转发\n") + MsgFilterUtils.describe(r)
                tvResult.setTextColor(if (r.blocked) Color.parseColor("#D93025") else Color.parseColor("#2E9E4F"))
            }.show()
    }

    private fun showLogsDialog() {
        val logs = MsgFilterUtils.getLogs()
        val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
        val text = if (logs.isEmpty()) "暂无拦截记录" else logs.joinToString("\n\n") {
            "${fmt.format(Date(it.time))} [${it.type}] ${it.from}\n${it.reason}\n${it.content.take(120)}"
        }
        MaterialDialog.Builder(requireContext()).title("拦截记录（最近 ${logs.size} 条）").content(text)
            .positiveText(R.string.action_back)
            .negativeText("清空")
            .onNegative { _, _ ->
                MsgFilterUtils.clearLogs()
                XToastUtils.success("已清空")
            }.show()
    }

    private fun showImportDialog() {
        val ctx = requireContext()
        val et = EditText(ctx).apply {
            hint = "粘贴导出的 JSON"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
        }
        MaterialDialog.Builder(ctx).title("导入名单").customView(et, true).autoDismiss(false)
            .positiveText("覆盖导入").neutralText("追加导入").negativeText(R.string.cancel)
            .onNegative { d, _ -> d.dismiss() }
            .onNeutral { d, _ -> doImport(et.text.toString(), false, d) }
            .onPositive { d, _ -> doImport(et.text.toString(), true, d) }
            .show()
    }

    private fun doImport(json: String, replace: Boolean, dialog: MaterialDialog) {
        val imported = MsgFilterUtils.parseConfig(json.trim())
        if (imported == null) {
            XToastUtils.error("JSON 格式不正确")
            return
        }
        if (replace) {
            config = imported
        } else {
            var base = System.currentTimeMillis()
            imported.entries.forEach { e ->
                if (config.entries.none { it.list == e.list && it.keyword.equals(e.keyword, true) }) {
                    e.id = base++
                    config.entries.add(e)
                }
            }
        }
        save()
        bindConfig()
        XToastUtils.success("已导入 ${imported.entries.size} 条")
        dialog.dismiss()
    }
}
