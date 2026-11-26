package com.hdn.adsmodule.ads

import org.junit.Assert.assertEquals
import org.junit.Test

class AdUnitParserTest {
    @Test
    fun parse() {
        assertEquals(listOf("a", "b"), AdUnitParser.parse("[\"a\", \" b \", \"\"]", "rl"))
        assertEquals(listOf("one"), AdUnitParser.parse(" one ", "rl"))
        assertEquals(listOf("rl"), AdUnitParser.parse("", "rl"))
        assertEquals(listOf("rl"), AdUnitParser.parse("[]", "rl"))
        assertEquals(listOf("rl"), AdUnitParser.parse("[\"\"]", "rl"))
        // Chưa cấu hình ID nào -> list rỗng, ads chỉ load fail chứ không crash
        assertEquals(emptyList<String>(), AdUnitParser.parse("", ""))
    }
}
