# Shared persistence

Features depend on `:core:database`. `RasedDatabase.getInstance(context)` provides
the application-scoped Room database. Versioned schemas are committed in `schemas/`.
Add migrations when changing the schema; do not enable destructive fallback.

`SavedFileStorage` keeps one file per feature-owned slot (currently `sorting.data`
and `sorting.wallet`). Room stores the original display name, byte count, and
internal filename. File bytes live in `filesDir/saved_files`, outside Android's
evictable cache. Files survive process restarts and source-file removal. As with
other private app data, uninstalling or clearing app storage removes them.

`replace` streams the new copy on the I/O dispatcher, flushes it, then runs its
`beforeCommit` callback and updates the saved-file record in one Room transaction.
Sorting imports its indexed rows in that callback. Failed or cancelled copies/imports retain
the old record and file. There is no expiration or automatic removal of active
files. Callers must finish reading a slot before replacing it; sorting prevents
replacement while a sort is running. Use the returned private URI only inside the
app (external sharing requires FileProvider).

Schema version 2 adds `sorting_imports`, `sorting_data`, `sorting_wallet`, and
`sorting_results`, with a generated, non-destructive migration from version 1.
`SortingRepository.loadInputs` imports existing metadata-only files once.
Import revisions encode the retained filename and parser/normalizer version.
Keep `PARSER_VERSION` current when import semantics change. Data and wallet rows
deduplicate normalized plates using first occurrence; wallet sequence preserves
source order. Imports use bounded batches under an outer transaction.

Each sort materializes an immutable snapshot with an indexed join. Results are
paged by `(runId, id)` and streamed for Excel exports. Closing a snapshot deletes
only its results. The singleton's process-open callback removes abandoned result
snapshots; imported rows and original files are permanent until replaced.
Pasted wallets use a temporary revision removed in the matching transaction.

Device checks: `-e room seed` followed in a new process by `-e room verify`, using
the app's `XlsxRegressionInstrumentation` runner. The `-e benchmark sorting` mode
compares the legacy matcher, SQL prototype, and production Room repository.

Setup follows [Android's Room documentation](https://developer.android.com/training/data-storage/room).
