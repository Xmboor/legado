package io.legado.app.data.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssSourceHelperTest {

    @Test
    fun getInvalidGroupNames_detectsInvalidAndTimeout() {
        val source = RssSource(sourceUrl = "https://a.com", sourceGroup = "正常,网站失效,校验超时,js失效")
        val names = source.getInvalidGroupNames()
        assertTrue(names.contains("网站失效"))
        assertTrue(names.contains("校验超时"))
        assertTrue(names.contains("js失效"))
        assertTrue(!names.contains("正常"))
    }

    @Test
    fun getInvalidGroupNames_nullGroup_returnsEmpty() {
        val source = RssSource(sourceUrl = "https://a.com", sourceGroup = null)
        assertEquals("", source.getInvalidGroupNames())
    }

    @Test
    fun getInvalidGroupNames_onlyValid_returnsEmpty() {
        val source = RssSource(sourceUrl = "https://a.com", sourceGroup = "科技,新闻")
        assertEquals("", source.getInvalidGroupNames())
    }

    @Test
    fun addErrorComment_prependsAndPreservesUserComment() {
        val source = RssSource(sourceUrl = "https://a.com", sourceComment = "我的备注")
        source.addErrorComment(RuntimeException("连接超时"))
        assertEquals("// Error: 连接超时\n\n我的备注", source.sourceComment)
    }

    @Test
    fun addErrorComment_nullComment() {
        val source = RssSource(sourceUrl = "https://a.com", sourceComment = null)
        source.addErrorComment(RuntimeException("失败"))
        assertEquals("// Error: 失败", source.sourceComment)
    }

    @Test
    fun removeErrorComment_keepsUserComment() {
        val source = RssSource(sourceUrl = "https://a.com", sourceComment = "// Error: 旧错误\n\n我的备注")
        source.removeErrorComment()
        assertEquals("我的备注", source.sourceComment)
    }

    @Test
    fun removeErrorComment_multipleErrors() {
        val source = RssSource(sourceUrl = "https://a.com", sourceComment = "// Error: 错误1\n\n// Error: 错误2\n\n备注")
        source.removeErrorComment()
        assertEquals("备注", source.sourceComment)
    }

    @Test
    fun addThenRemove_restoresUserComment() {
        val source = RssSource(sourceUrl = "https://a.com", sourceComment = "备注")
        source.addErrorComment(RuntimeException("x"))
        source.removeErrorComment()
        assertEquals("备注", source.sourceComment)
    }
}