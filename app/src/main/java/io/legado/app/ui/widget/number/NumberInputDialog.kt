package io.legado.app.ui.widget.number

import android.content.Context
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import io.legado.app.R
import io.legado.app.lib.dialogs.setUiTitle
import io.legado.app.utils.applyTint
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.showSoftInput

/**
 * 手动数字输入对话框，替代滚轮选择。
 * API 与 [NumberPickerDialog] 一致，越界自动收敛、留空保持原值。
 */
class NumberInputDialog(private val context: Context) {
    private val builder = AlertDialog.Builder(context)
    private var editText: EditText? = null
    private var maxValue: Int = Int.MAX_VALUE
    private var minValue: Int = 0
    private var value: Int = 0

    init {
        builder.setView(R.layout.dialog_number_input)
    }

    fun setTitle(title: String): NumberInputDialog {
        builder.setUiTitle(context, title)
        return this
    }

    fun setMaxValue(value: Int): NumberInputDialog {
        maxValue = value
        return this
    }

    fun setMinValue(value: Int): NumberInputDialog {
        minValue = value
        return this
    }

    fun setValue(value: Int): NumberInputDialog {
        this.value = value
        return this
    }

    fun show(callBack: ((value: Int) -> Unit)?) {
        builder.setPositiveButton(R.string.ok) { _, _ ->
            editText?.let {
                it.clearFocus()
                it.hideSoftInput()
                val result = NumberInput.parse(it.text?.toString(), minValue, maxValue, value)
                callBack?.invoke(result)
            }
        }
        builder.setNegativeButton(R.string.cancel, null)
        val dialog = builder.show().applyTint()
        editText = dialog.findViewById(R.id.edit_number)
        editText?.let {
            it.hint = context.getString(R.string.number_input_range, minValue, maxValue)
            it.setText(value.toString())
            it.selectAll()
            it.post { it.showSoftInput() }
        }
    }
}