package cn.ppps.forwarder.fragment

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
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

    private val TAG: String = MsgFilterFragment::class.java.simpleName
    private lateinit var config: MsgFilterConfig
    private var loading = false

    private val modeLabels = listOf("不限", "包含", "等于", "开头是", "结尾是", "正则")

    override fun viewBindingInflate(inflater: LayoutInflater, container: ViewGroup): FragmentMsgFilterBinding {
        return FragmentMsgFilterBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        val titleBar = super.initTitle()!!.setImmersive(false)
        titleBar.setTitle("黑白名单")
        return titleBar
    }

    override fun initViews() {
        config = MsgFilterUtils.loadConfig()
        bindConfig()
    }

    private fun bindConfig() {
        loading = true
        val b = binding!!
        b.rgMode.check(
            when (config.mode) {
                MsgFilterConfig.MODE_BLACK_WHITE -> R.id.rb_mode_black_white
                MsgFilterConfig.MODE_WHITE_ONLY -> R.id.rb_mode_white_only
                else -> R.id.rb_mode_off
            }
        )
        b.cbTypeSms.isChecked = config.types.contains("sms")
        b.cbTypeCall.isChecked = config.types.contains("call")
        b.cbTypeApp.isChecked = config.types.contains("app")
        loading = false
        renderLists()
    }

    override fun initListeners() {
        val b = binding!!
        b.rgMode.setOnCheckedChangeListener { _, checkedId ->
            if (loading) return@setOnCheckedChangeListener
            config.mode = when (checkedId) {
                R.id.rb_mode_black_white -> MsgFilterConfig.MODE_BLACK_WHITE
                R.id.rb_mode_white_only -> MsgFilterConfig.MODE_WHITE_ONLY
                else -> MsgFilterConfig.MODE_OFF
            }
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

        b.btnAddWhite.setOnClickListener { editEntry(null, MsgFilterEntry.LIST_WHITE) }
        b.btnAddBlack.setOnClickListener { editEntry(null, MsgFilterEntry.LIST_BLACK) }
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

    private fun modeText(mode: String, value: String): String? {
        if (mode == MsgFilterEntry.MODE_ANY || MsgFilterMatcher.splitValues(value).isEmpty()) return null
        val idx = MsgFilterEntry.MODES.indexOf(mode).coerceAtLeast(0)
        return modeLabels[idx] + " " + MsgFilterMatcher.splitValues(value).joinToString(" | ")
    }

    private fun renderLists() {
        val b = binding!!
        b.llWhite.removeAllViews()
        b.llBlack.removeAllViews()
        val white = config.entries.filter { it.isWhite }
        val black = config.entries.filter { !it.isWhite }
        b.tvWhiteTitle.text = "白名单（优先放行）· ${white.size}"
        b.tvBlackTitle.text = "黑名单（拦截）· ${black.size}"
        if (white.isEmpty()) b.llWhite.addView(emptyView())
        if (black.isEmpty()) b.llBlack.addView(emptyView())
        white.forEach { b.llWhite.addView(entryView(it)) }
        black.forEach { b.llBlack.addView(entryView(it)) }
    }

    private fun emptyView(): TextView {
        return TextView(requireContext()).apply {
            text = "（空）"
            setPadding(dp(15), dp(8), dp(15), dp(8))
            setTextColor(Color.GRAY)
        }
    }

    private fun entryView(entry: MsgFilterEntry): View {
        val ctx = requireContext()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), if (entry.isWhite) Color.parseColor("#2E9E4F") else Color.parseColor("#D93025"))
            }
            alpha = if (entry.enabled) 1f else 0.45f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(5), dp(5), dp(5), 0)
            }
        }
        val title = entry.note.ifBlank { if (entry.isWhite) "白名单" else "黑名单" } + if (entry.enabled) "" else "（已停用）"
        card.addView(TextView(ctx).apply {
            text = title
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#333333"))
        })
        val s = modeText(entry.senderMode, entry.sender)
        val c = modeText(entry.contentMode, entry.content)
        val desc = StringBuilder()
        desc.append("号码：").append(s ?: "不限").append('\n')
        desc.append("内容：").append(c ?: "不限")
        if (s == null && c == null) desc.append("\n⚠ 两个条件都不限，会匹配所有消息")
        card.addView(TextView(ctx).apply {
            text = desc.toString()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#555555"))
        })
        card.setOnClickListener { editEntry(entry, entry.list) }
        card.setOnLongClickListener {
            MaterialDialog.Builder(ctx).title("删除名单项").content("确定删除「$title」？")
                .positiveText(R.string.lab_yes).negativeText(R.string.lab_no)
                .onPositive { _, _ ->
                    config.entries.remove(entry)
                    save()
                    renderLists()
                }.show()
            true
        }
        return card
    }

    @SuppressLint("InflateParams")
    private fun editEntry(entry: MsgFilterEntry?, list: String) {
        val ctx = requireContext()
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_msg_filter_entry, null)
        val rgList = view.findViewById<RadioGroup>(R.id.rg_list)
        val spSender = view.findViewById<Spinner>(R.id.sp_sender_mode)
        val etSender = view.findViewById<EditText>(R.id.et_sender)
        val spContent = view.findViewById<Spinner>(R.id.sp_content_mode)
        val etContent = view.findViewById<EditText>(R.id.et_content)
        val etNote = view.findViewById<EditText>(R.id.et_note)
        val cbEnabled = view.findViewById<CheckBox>(R.id.cb_enabled)

        val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, modeLabels)
        spSender.adapter = adapter
        spContent.adapter = adapter

        val e = entry ?: MsgFilterEntry(
            id = System.currentTimeMillis(),
            list = list,
            senderMode = MsgFilterEntry.MODE_EQUALS,
            contentMode = if (list == MsgFilterEntry.LIST_WHITE) MsgFilterEntry.MODE_CONTAINS else MsgFilterEntry.MODE_ANY,
        )
        rgList.check(if (e.list == MsgFilterEntry.LIST_WHITE) R.id.rb_list_white else R.id.rb_list_black)
        spSender.setSelection(MsgFilterEntry.MODES.indexOf(e.senderMode).coerceAtLeast(0))
        spContent.setSelection(MsgFilterEntry.MODES.indexOf(e.contentMode).coerceAtLeast(0))
        etSender.setText(e.sender)
        etContent.setText(e.content)
        etNote.setText(e.note)
        cbEnabled.isChecked = e.enabled

        MaterialDialog.Builder(ctx)
            .title(if (entry == null) "添加名单项" else "编辑名单项")
            .customView(view, true)
            .autoDismiss(false)
            .positiveText(R.string.save)
            .negativeText(R.string.cancel)
            .onNegative { d, _ -> d.dismiss() }
            .onPositive { d, _ ->
                val senderMode = MsgFilterEntry.MODES[spSender.selectedItemPosition.coerceAtLeast(0)]
                val contentMode = MsgFilterEntry.MODES[spContent.selectedItemPosition.coerceAtLeast(0)]
                val sender = etSender.text.toString().trim()
                val content = etContent.text.toString().trim()
                if (senderMode == MsgFilterEntry.MODE_REGEX) {
                    MsgFilterMatcher.regexError(sender)?.let { XToastUtils.error("号码正则有误：$it"); return@onPositive }
                }
                if (contentMode == MsgFilterEntry.MODE_REGEX) {
                    MsgFilterMatcher.regexError(content)?.let { XToastUtils.error("内容正则有误：$it"); return@onPositive }
                }
                if (senderMode != MsgFilterEntry.MODE_ANY && sender.isEmpty()) {
                    XToastUtils.error("请填写号码，或把号码条件改为“不限”"); return@onPositive
                }
                if (contentMode != MsgFilterEntry.MODE_ANY && content.isEmpty()) {
                    XToastUtils.error("请填写内容关键字，或把内容条件改为“不限”"); return@onPositive
                }
                e.list = if (rgList.checkedRadioButtonId == R.id.rb_list_white) MsgFilterEntry.LIST_WHITE else MsgFilterEntry.LIST_BLACK
                e.senderMode = senderMode
                e.sender = sender
                e.contentMode = contentMode
                e.content = content
                e.note = etNote.text.toString().trim()
                e.enabled = cbEnabled.isChecked
                if (entry == null) config.entries.add(e)
                if (config.mode == MsgFilterConfig.MODE_OFF) {
                    config.mode = MsgFilterConfig.MODE_BLACK_WHITE
                    bindConfig()
                    XToastUtils.info("已自动开启“黑名单 + 白名单”模式")
                }
                save()
                renderLists()
                d.dismiss()
            }.show()
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
        val etContent = EditText(ctx).apply { hint = "短信内容（APP通知为 标题+内容）"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        val tvResult = TextView(ctx).apply { setPadding(0, dp(8), 0, 0); setTypeface(typeface, Typeface.BOLD) }
        box.addView(rg); box.addView(etFrom); box.addView(etContent); box.addView(tvResult)

        MaterialDialog.Builder(ctx).title("测试黑白名单").customView(box, true).autoDismiss(false)
            .positiveText(R.string.action_test).negativeText(R.string.action_back)
            .onNegative { d, _ -> d.dismiss() }
            .onPositive { _, _ ->
                val type = types[(rg.checkedRadioButtonId - 1000).coerceIn(0, 2)].first
                val r = MsgFilterMatcher.check(config, type, etFrom.text.toString(), etContent.text.toString())
                tvResult.text = (if (r.blocked) "✗ 拦截\n" else "✓ 放行\n") + MsgFilterUtils.describe(r)
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
            imported.entries.forEach { it.id = base++ }
            config.entries.addAll(imported.entries)
        }
        save()
        bindConfig()
        XToastUtils.success("已导入 ${imported.entries.size} 条")
        dialog.dismiss()
    }
}
