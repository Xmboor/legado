package io.legado.app.ui.widget

/** 把源列表映射为横向切换条所需的名称列表与选中索引（纯函数，便于单测）。 */
object SourceSwitcherBar {
    fun <T> build(
        sources: List<T>,
        selectedKey: String?,
        key: (T) -> String,
        name: (T) -> String
    ): Pair<List<String>, Int> {
        val names = sources.map(name)
        val index = if (selectedKey == null) -1
                    else sources.indexOfFirst { key(it) == selectedKey }
        return names to index
    }
}