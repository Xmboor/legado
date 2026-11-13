package io.legado.app.ui.widget.number

/**
 * 手动数字输入的解析与收敛（纯函数，便于单测）。
 * 空白/非数字回退到 fallback，越界收敛到 [min, max]。
 */
object NumberInput {
    fun parse(text: String?, min: Int, max: Int, fallback: Int): Int {
        val value = text?.trim()?.toIntOrNull() ?: return fallback
        return value.coerceIn(min, max)
    }
}