package com.rased.feature.sorting.data

import com.rased.feature.sorting.domain.SortingResult
import java.io.Closeable
import java.io.OutputStream

interface ResultStore : Closeable {
    fun readPage(start: Int, count: Int): List<SortingResult>
    fun copyText(): String
    fun writeXlsx(output: OutputStream)
}
