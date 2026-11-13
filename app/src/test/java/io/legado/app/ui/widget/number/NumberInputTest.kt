package io.legado.app.ui.widget.number

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberInputTest {

    @Test
    fun parse_blank_returnsFallback() {
        assertEquals(7, NumberInput.parse("", 0, 100, 7))
        assertEquals(7, NumberInput.parse("   ", 0, 100, 7))
        assertEquals(7, NumberInput.parse(null, 0, 100, 7))
    }

    @Test
    fun parse_nonNumeric_returnsFallback() {
        assertEquals(7, NumberInput.parse("abc", 0, 100, 7))
        assertEquals(7, NumberInput.parse("12x", 0, 100, 7))
    }

    @Test
    fun parse_belowMin_clampsToMin() {
        assertEquals(0, NumberInput.parse("-5", 0, 100, 7))
        assertEquals(10, NumberInput.parse("3", 10, 100, 7))
    }

    @Test
    fun parse_aboveMax_clampsToMax() {
        assertEquals(100, NumberInput.parse("999", 0, 100, 7))
    }

    @Test
    fun parse_valid_returnsValue() {
        assertEquals(42, NumberInput.parse("42", 0, 100, 7))
        assertEquals(42, NumberInput.parse("  42  ", 0, 100, 7))
    }

    @Test
    fun parse_boundary_returnsValue() {
        assertEquals(0, NumberInput.parse("0", 0, 100, 7))
        assertEquals(100, NumberInput.parse("100", 0, 100, 7))
    }
}