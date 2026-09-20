package com.rased.feature.sorting.domain

data class DataRow(
    val normalizedPlate: String,
    val values: Map<String, String>
)

data class WalletRow(
    val normalizedPlate: String,
    val walletType: String?
)

data class SortingResult(
    val plate: String,
    val type: String?,
    val note: String?,
    val street: String?,
    val district: String?,
    val date: String?,
    val walletType: String?,
    val location: String? = null
) {
    fun toTsvRow(): String = listOf(
        plate, type.orEmpty(), note.orEmpty(), street.orEmpty(), district.orEmpty(), date.orEmpty(), walletType.orEmpty(), location.orEmpty()
    ).joinToString("\t")
}
