package com.rased.feature.sorting.domain

import com.rased.core.excel.ExcelHeaders

object SortingEngine {
    private val plateColumnNames = setOf(
        "اللوحة", "اللوحه", "لوحة", "لوحه", "رقم اللوحة", "رقم اللوحه", "رقم لوحة", "رقم لوحه",
        "لوحة المركبة", "لوحه المركبة", "لوحه المركبه", "لوحة المركبه", "رقم اللوحة عربي",
        "Plate", "plate_num", "plate#"
    )
    private val walletTypeNames = setOf("اللون", "لون", "color", "colour")

    fun noteNames(): Set<String> = setOf("الملاحظة","الملاحظه", "ملاحظة", "ملاحظه", "الملاحظات", "ملاحظات", "note", "notes")
    fun districtNames(): Set<String> = setOf("الحي", "الحى", "حي", "حى", "district")

    fun locationNames(): Set<String> = setOf("الموقع", "موقع")

    fun plateNames(): Set<String> = plateColumnNames
    fun walletTypeNames(): Set<String> = walletTypeNames

    fun sort(dataRows: List<Map<String, String>>, walletRows: List<Map<String, String>>): List<SortingResult> {
        val dataPlateHeader = findHeader(dataRows.firstOrNull()?.keys.orEmpty(), plateColumnNames)
            ?: error("لم يتم العثور على عمود اللوحة في شيت الداتا")

        val walletPlateHeader = findHeader(walletRows.firstOrNull()?.keys.orEmpty(), plateColumnNames)
            ?: error("لم يتم العثور على عمود اللوحة في شيت المحفظة")
        val walletTypeHeader = findHeader(walletRows.firstOrNull()?.keys.orEmpty(), walletTypeNames, allowColorColumn = true)

        val dataIndex = linkedMapOf<String, MutableList<Map<String, String>>>()
        dataRows.forEach { row ->
            val normalized = PlateNormalizer.normalize(row[dataPlateHeader]) ?: return@forEach
            dataIndex.getOrPut(normalized) { mutableListOf() }.add(row)
        }

        val seenWalletPlates = mutableSetOf<String>()
        val results = mutableListOf<SortingResult>()

        walletRows.forEach { walletRow ->
            val normalized = PlateNormalizer.normalize(walletRow[walletPlateHeader]) ?: return@forEach
            if (!seenWalletPlates.add(normalized)) return@forEach

            val matches = dataIndex[normalized] ?: return@forEach
            for (dataRow in matches) {
            results += SortingResult(
                plate = dataRow[dataPlateHeader].orEmpty(),
                type = firstValueOrNull(dataRow, listOf("النوع", "نوع")),
                note = firstValueOrNull(dataRow, noteNames().toList()) ?: firstValueOrNull(walletRow, noteNames().toList()),
                street = firstValueOrNull(dataRow, listOf("الشارع", "شارع")),
                district = firstValueOrNull(dataRow, districtNames().toList()) ?: firstValueOrNull(walletRow, districtNames().toList()),
                date = firstValueOrNull(dataRow, listOf("التاريخ", "تاريخ")),
                walletType = walletTypeHeader?.let { walletRow[it].orEmpty().ifBlank { null } },
                walletModel = WalletHeaders.modelValue(walletRow),
                location = firstValueOrNull(dataRow, locationNames().toList()) ?: firstValueOrNull(walletRow, locationNames().toList())
            )
            }
        }

        return results
    }

    fun sortWithPlainTextWallet(dataRows: List<Map<String, String>>, walletText: String): List<SortingResult> {
        val walletRows = walletText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { mapOf("اللوحة" to it) }
            .toList()
        return sort(dataRows, walletRows)
    }

    fun resultsToTsv(results: List<SortingResult>): String {
        val header = listOf("اللوحة", "النوع", "الملاحظة", "الشارع", "الحي", "التاريخ", "اللون", "الموقع").joinToString("\t")
        return buildString {
            appendLine(header)
            results.forEach { appendLine(it.toTsvRow()) }
        }.trimEnd()
    }

    private fun findHeader(headers: Set<String>, aliases: Set<String>, allowColorColumn: Boolean = false): String? {
        val normalizedAliases = aliases.map(::normalizeHeader).toSet()
        val matching = headers.filter { header -> normalizeHeader(header) in normalizedAliases }
        return matching.sortedWith(compareByDescending<String> { normalizeHeader(it).contains("عربي") }).firstOrNull()
            ?: headers.firstOrNull { header -> allowColorColumn && normalizeHeader(header).contains(normalizeHeader("لون")) }
    }
    private fun normalizeHeader(value: String): String = ExcelHeaders.normalize(value)

    private fun firstValue(row: Map<String, String>, names: List<String>): String = firstValueOrNull(row, names).orEmpty()

    private fun firstValueOrNull(row: Map<String, String>, names: List<String>): String? {
        val normalizedNames = names.map(::normalizeHeader).toSet()
        val key = row.keys.firstOrNull { normalizeHeader(it) in normalizedNames }
        return key?.let { row[it] }?.ifBlank { null }
    }
}
