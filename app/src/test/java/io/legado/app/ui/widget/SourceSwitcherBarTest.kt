package io.legado.app.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceSwitcherBarTest {
    private data class Src(val url: String, val name: String)
    private val list = listOf(Src("a", "甲"), Src("b", "乙"), Src("c", "丙"))
    private val key = { s: Src -> s.url }
    private val name = { s: Src -> s.name }

    @Test
    fun build_empty_returnsEmptyNamesAndMinusOne() {
        val (names, index) = SourceSwitcherBar.build(emptyList<Src>(), "a", key, name)
        assertEquals(emptyList<String>(), names)
        assertEquals(-1, index)
    }

    @Test
    fun build_nullSelectedKey_returnsMinusOne() {
        val (names, index) = SourceSwitcherBar.build(list, null, key, name)
        assertEquals(listOf("甲", "乙", "丙"), names)
        assertEquals(-1, index)
    }

    @Test
    fun build_selectedKeyMatches_returnsIndex() {
        assertEquals(1, SourceSwitcherBar.build(list, "b", key, name).second)
        assertEquals(2, SourceSwitcherBar.build(list, "c", key, name).second)
        assertEquals(0, SourceSwitcherBar.build(list, "a", key, name).second)
    }

    @Test
    fun build_selectedKeyNotFound_returnsMinusOne() {
        assertEquals(-1, SourceSwitcherBar.build(list, "zzz", key, name).second)
    }

    @Test
    fun build_namesMappedInOrder() {
        assertEquals(listOf("甲", "乙", "丙"), SourceSwitcherBar.build(list, "a", key, name).first)
    }
}