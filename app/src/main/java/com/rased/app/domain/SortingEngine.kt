package com.rased.app.domain

object SortingEngine {
    private val plateColumnNames = setOf("اللوحة", "اللوحه", "لوحة", "لوحه")
    private val walletTypeNames = setOf("النوع", "الماركة", "الموديل")

    fun plateNames(): Set<String> = plateColumnNames
    fun walletTypeNames(): Set<String> = walletTypeNames

    fun sort(dataRows: List<Map<String, String>>, walletRows: List<Map<String, String>>): List<SortingResult> {
        val dataPlateHeader = findHeader(dataRows.firstOrNull()?.keys.orEmpty(), plateColumnNames)
            ?: error("لم يتم العثور على عمود اللوحة في شيت الداتا")

        val walletPlateHeader = findHeader(walletRows.firstOrNull()?.keys.orEmpty(), plateColumnNames)
            ?: error("لم يتم العثور على عمود اللوحة في شيت المحفظة")
        val walletTypeHeader = findHeader(walletRows.firstOrNull()?.keys.orEmpty(), walletTypeNames)

        val dataIndex = linkedMapOf<String, Map<String, String>>()
        dataRows.forEach { row ->
            val normalized = PlateNormalizer.normalize(row[dataPlateHeader]) ?: return@forEach
            dataIndex.putIfAbsent(normalized, row)
        }

        val seenWalletPlates = mutableSetOf<String>()
        val results = mutableListOf<SortingResult>()

        walletRows.forEach { walletRow ->
            val normalized = PlateNormalizer.normalize(walletRow[walletPlateHeader]) ?: return@forEach
            if (!seenWalletPlates.add(normalized)) return@forEach

            val dataRow = dataIndex[normalized] ?: return@forEach
            results += SortingResult(
                plate = firstValue(dataRow, listOf("اللوحة", "اللوحه", "لوحة", "لوحه")),
                type = firstValueOrNull(dataRow, listOf("النوع")),
                note = firstValueOrNull(dataRow, listOf("الملاحظة", "ملاحظة", "الملاحظات")),
                street = firstValueOrNull(dataRow, listOf("الشارع", "شارع")),
                district = firstValueOrNull(dataRow, listOf("الحي", "حى")),
                date = firstValueOrNull(dataRow, listOf("التاريخ", "تاريخ")),
                walletType = walletTypeHeader?.let { walletRow[it].orEmpty().ifBlank { null } }
            )
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
        val header = listOf("اللوحة", "النوع", "الملاحظة", "الشارع", "الحي", "التاريخ", "نوع المحفظة").joinToString("\t")
        return buildString {
            appendLine(header)
            results.forEach { appendLine(it.toTsvRow()) }
        }.trimEnd()
    }

    private fun findHeader(headers: Set<String>, aliases: Set<String>): String? {
        return headers.firstOrNull { header -> normalizeHeader(header) in aliases.map(::normalizeHeader) }
    }

    private fun normalizeHeader(value: String): String = value.trim().replace(" ", "")

    private fun firstValue(row: Map<String, String>, names: List<String>): String = firstValueOrNull(row, names).orEmpty()

    private fun firstValueOrNull(row: Map<String, String>, names: List<String>): String? {
        val normalizedNames = names.map(::normalizeHeader).toSet()
        val key = row.keys.firstOrNull { normalizeHeader(it) in normalizedNames }
        return key?.let { row[it] }?.ifBlank { null }
    }
}
