package com.rased.feature.sorting.domain

object PlateNormalizer {
    fun normalize(input: String?): String? {
        if (input.isNullOrBlank()) return null

        val cleaned = input
            .trim()
            .replace("\u00A0", "")
            .replace("\\s+".toRegex(), "")
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ٱ", "ا")
            .replace("ى", "ي")
            .replace("ة", "ه")
            .replace("ـ", "")
            .let(::convertArabicDigitsToEnglish)

        val letters = cleaned.filter { it.isArabicLetter() }
        val digits = cleaned.filter { it.isDigit() }

        if (letters.isBlank() || digits.isBlank()) return null
        if (digits.length > 4) return null

        return letters + digits.padStart(4, '0')
    }

    private fun Char.isArabicLetter(): Boolean = this in '\u0600'..'\u06FF' && !this.isDigit()

    private fun convertArabicDigitsToEnglish(value: String): String {
        val arabic = "٠١٢٣٤٥٦٧٨٩"
        val persian = "۰۱۲۳۴۵۶۷۸۹"
        return buildString {
            value.forEach { ch ->
                val a = arabic.indexOf(ch)
                val p = persian.indexOf(ch)
                append(
                    when {
                        a >= 0 -> '0' + a
                        p >= 0 -> '0' + p
                        else -> ch
                    }
                )
            }
        }
    }
}
