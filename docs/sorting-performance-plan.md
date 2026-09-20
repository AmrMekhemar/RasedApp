# Sorting performance experiment and implementation plan

Status: implemented in production. Historical prototype measurements and the
original plan are retained below; final Room measurements follow here.

## Production Room results

Same emulator and fixtures, three repeat sorts per size; medians include result
materialization, count, and the first 200 rows. All result checksums match.

| Data / wallet rows | Original baseline | Room repeat sort | Speedup vs original | Room import, including durable copies |
| --- | ---: | ---: | ---: | ---: |
| 10,000 / 1,000 | 3.818 s | 0.029 s | 132x | 2.444 s |
| 100,000 / 10,000 | 35.782 s | 0.081 s | 442x | 21.344 s |

The optimized reader also improved the legacy matching path: its new medians are
2.840 s and 25.525 s. Room repeat sorts are therefore 98x and 315x faster than
that optimized legacy path in the same run. Import timings are single samples,
not medians; the original prototype import times were 3.761 s and 33.797 s and
excluded durable copies. Initial import is still necessary on new inputs.

Changes implemented: Room schema v2 with automatic v1 migration, bounded row
imports committed atomically with saved-file metadata, normalized-plate indexes,
ordered join snapshots, unchanged export/share behavior, one-time upgrade import,
pasted-wallet isolation, import progress, direct private-file reads, buffered
shared-string writes, a byte-bounded string cache, selected-column decoding, and
reuse of the normalization regex. Snapshots are independent of input replacement.

Device checks passed for migration, original-source deletion, restart without
re-import, failed/cancelled replacement rollback, independent input replacement,
aliases, Arabic normalization, duplicates, no matches, pasted wallets, snapshot
stability, paging, and exported result contents. These are emulator measurements;
performance on physical phones and user workbooks has not been measured.

Raw production output: [sorting-room-benchmark-results.txt](sorting-room-benchmark-results.txt).

Final validation (2026-09-20): `assembleDebug`, `assembleDebugAndroidTest`, and
`lintDebug` passed. The full device regression also passed: a 140 MiB worksheet,
an 18 MB compressed XLSX with 30,000 distinct matches, table/card paging in both
directions, horizontal scrolling, clipboard limits, and full Excel export. Peak
sampled managed heap in the large sorting test was 28 MiB against a 192 MiB limit.
The large test injects a repository with separate test slots, preserving the
user's actual sorting inputs.

## Measurement

Run the test-only `SortingSpeedBenchmark` through:

```text
adb shell am instrument -w -e benchmark sorting com.rased.app.test/com.rased.app.XlsxRegressionInstrumentation
```

Environment: Android 17 x86_64 emulator (`sdk_gphone16k_x86_64`), debug build.
Fixtures have six columns, a shared-string table with distinct notes, reversed
wallet order, duplicate data and wallet plates, and an unmatched wallet plate.
The fixture sizes below exclude the extra duplicate and unmatched rows.

The baseline uses the production XlsxReader and SortingStore sequence: read wallet,
read data, match, materialize results, count, and fetch the first 200 rows. The
prototype imports normalized rows into indexed SQLite tables once, then performs
an indexed join, materializes all results, counts, and fetches the first 200 rows.
All result columns and their order are compared using SHA-256 outside timed work.

The prototype exercises the SQLite design intended for Room; it is not a completed
Room implementation or an end-to-end UI benchmark. Measurements use one small
warm-up case followed by three iterations per case in one process. File selection,
initial durable file copying, fixture generation, correctness hashing, UI drawing,
and Excel export are outside the timings. OS disk caches are not cleared. Indexed
import is measured once per case, after the baseline runs. Synthetic results do
not establish performance on the user's phone or actual workbooks.

Measured on 2026-09-19; medians of three timed sorts:

| Data / wallet rows | Current sort | Cached sort | Repeat-sort speedup | One-time indexed import |
| --- | ---: | ---: | ---: | ---: |
| 10,000 / 1,000 | 3.818 s | 0.019 s | 201x | 3.761 s |
| 100,000 / 10,000 | 35.782 s | 0.047 s | 761x | 33.797 s |

All checksums matched. The larger baseline spent a median 32.534 s in combined
data-sheet reading and row matching, versus 3.177 s reading/inserting the wallet.
These combined timings do not separate XML parsing from SQLite or normalization.
The input archives total 284,791 and 2,833,637 bytes respectively; repetitive
synthetic content compresses well, so file size alone is not a workload predictor.

The large repeat-sort improvement is real for this prototype, but primarily comes
from avoiding re-import. The initial import still costs roughly as much as a
current sort. Production Room overhead, UI rendering, real file characteristics,
and physical-device behavior require measurement after implementation.

Raw output: [sorting-benchmark-results.txt](sorting-benchmark-results.txt).

## Proposed implementation

1. **Add indexed, versioned input rows to shared Room storage.**
   Migrate version 1 without deleting existing saved-file records or copies. Add
   import revisions, data rows keyed by `(revision, normalizedPlate)`, and wallet
   rows indexed by `(revision, normalizedPlate)` with explicit source order.
   Resolve header aliases and normalize plates once during import. Keep the first
   data and wallet occurrence, matching current behavior. Persist only fields used
   by sorting, and retain the original Excel file.

2. **Import only when an input changes.**
   Stage the replacement file and its parsed rows under a new revision. Stream
   parsing and batched inserts on the I/O dispatcher, keeping memory bounded.
   Activate the new saved-file record and input revision together only after
   successful parsing. Preserve the previous selection on cancellation or failure.
   Remove superseded copies/rows only once no active operation needs them.
   Existing saved files receive a one-time import after upgrade. Track parser and
   normalization versions so stale derived rows can be rebuilt from retained files.

3. **Replace repeated Excel scanning with an indexed join.**
   Match the active wallet revision against the active data revision, explicitly
   ordering results by wallet sequence. Materialize a result snapshot in Room in
   one transaction so paging, copying, saving, and sharing read the same results.
   Keep result snapshots separate from permanent input records; cleaning results
   must never remove saved inputs. Pasted wallet text creates a temporary wallet
   revision and reuses the imported data without replacing the saved wallet file.

4. **Address first-import latency separately.**
   Open app-owned seekable Excel files directly rather than making another ZIP
   copy. Profile the shared-string reader before changing it: buffered writes and
   a byte-bounded memory cache are candidates, with disk fallback for large files.
   Skip unused column values after resolving the headers. Display distinct
   importing and matching states; report measured progress where available.
   Caching alone does not eliminate the initial import cost.

5. **Validate correctness, persistence, and speed.**
   Compare every result against the existing path for header aliases, Arabic
   normalization, duplicate plates, missing matches, empty wallets, and first-sheet
   selection. Exercise process restart, migration from version 1, interrupted
   imports, independent data/wallet replacement, pasted wallets, paging, and full
   Excel export/share. Include large files and memory measurements. Re-run this
   benchmark against the actual Room path, then test representative user files on
   a physical phone before making a user-facing performance promise.

## Acceptance targets

- Unchanged files are not copied or parsed again when Sort is tapped.
- Replacing only the wallet does not re-import the data file, and vice versa.
- Repeat sorting of the 100,000 / 10,000 fixture completes within one second on
  this emulator, including materialization and the first page. This is a proposed
  engineering target, not a guarantee for every device or workbook.
- Results and ordering remain identical; importing remains bounded in memory.
- Original files and active parsed input revisions survive restarts until replaced.

Recommended order: implement steps 1–3 first for repeat-sort latency, measure the
actual Room implementation, then use profiling to prioritize step 4. Keep the
existing sorting path available during development for correctness comparisons.
