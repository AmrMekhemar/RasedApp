package com.rased.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "sorting_imports")
data class SortingImport(@PrimaryKey val slot: String, val revision: String, val parserVersion: Int)

@Entity(tableName = "sorting_data", primaryKeys = ["revision", "normalized"])
data class IndexedDataRow(
    val revision: String, val normalized: String, val plate: String,
    val type: String?, val note: String?, val street: String?, val district: String?, val date: String?
)

@Entity(tableName = "sorting_wallet", primaryKeys = ["revision", "normalized"],
    indices = [Index(value = ["revision", "sequence"], unique = true)])
data class IndexedWalletRow(val revision: String, val normalized: String, val sequence: Long, val walletType: String?)

@Entity(tableName = "sorting_results", indices = [Index(value = ["runId", "id"])])
data class IndexedResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String, val plate: String, val type: String?, val note: String?,
    val street: String?, val district: String?, val date: String?, val walletType: String?
)

@Dao
interface SortingDao {
    @Query("SELECT * FROM sorting_imports WHERE slot = :slot")
    fun imported(slot: String): SortingImport?

    @Upsert fun activate(imported: SortingImport)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertData(rows: List<IndexedDataRow>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertWallet(rows: List<IndexedWalletRow>)
    @Query("DELETE FROM sorting_data WHERE revision = :revision") fun deleteData(revision: String)
    @Query("DELETE FROM sorting_wallet WHERE revision = :revision") fun deleteWallet(revision: String)

    @Query("""INSERT INTO sorting_results(runId,plate,type,note,street,district,date,walletType)
        SELECT :runId,d.plate,d.type,d.note,d.street,d.district,d.date,w.walletType
        FROM sorting_wallet w JOIN sorting_data d ON d.revision = :dataRevision AND d.normalized = w.normalized
        WHERE w.revision = :walletRevision ORDER BY w.sequence""")
    fun match(runId: String, dataRevision: String, walletRevision: String)

    @Query("SELECT COUNT(*) FROM sorting_results WHERE runId = :runId") fun count(runId: String): Int
    @Query("SELECT MIN(id) FROM sorting_results WHERE runId = :runId") fun firstId(runId: String): Long?
    @Query("SELECT * FROM sorting_results WHERE runId = :runId AND id >= :fromId ORDER BY id LIMIT :count")
    fun page(runId: String, fromId: Long, count: Int): List<IndexedResult>
    @Query("DELETE FROM sorting_results WHERE runId = :runId") fun deleteResults(runId: String)
}
