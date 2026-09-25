package com.rased.feature.sorting.domain

import com.rased.core.excel.ExcelHeaders

object WalletHeaders {
    val modelNames: Set<String> = setOf(
        "طراز", "الطراز",
        "طراز المركبة", "نوع المركبة",
        "طراز السيارة", "نوع السيارة",
        "طراز المركبة الأساسي", "نوع المركبة الأساسي"
    )

    fun modelValue(row: Map<String, String>): String? {
        val normalizedNames = modelNames.map(ExcelHeaders::normalize).toSet()
        val exact = row.entries.firstOrNull { ExcelHeaders.normalize(it.key) in normalizedNames }?.value
        val fuzzy = row.entries.firstOrNull {
            val key = ExcelHeaders.normalize(it.key)
            key.contains("نوع") || key.contains("طراز")
        }?.value
        return (exact ?: fuzzy)?.ifBlank { null }
    }
}
