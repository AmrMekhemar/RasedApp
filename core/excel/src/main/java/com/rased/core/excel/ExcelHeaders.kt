package com.rased.core.excel

/** Ignore presentation-only differences in spreadsheet column labels. */
object ExcelHeaders {
    fun normalize(value: String): String = buildString {
        value.forEach { char ->
            if (!char.isWhitespace() && !Character.isSpaceChar(char) &&
                Character.getType(char) != Character.FORMAT.toInt() &&
                char != '\u0640' && char !in '\u064B'..'\u065F' && char != '\u0670') {
                append(char)
            }
        }
    }.lowercase()
}
