# Shared persistence

Features depend on `:core:database`. `RasedDatabase.getInstance(context)` provides
the application-scoped Room database. Versioned schemas are committed in `schemas/`.
Add migrations when changing the schema; do not enable destructive fallback.

`SavedFileStorage` keeps one file per feature-owned slot (currently `sorting.data`
and `sorting.wallet`). Room stores the original display name, byte count, and
internal filename. File bytes live in `filesDir/saved_files`, outside Android's
evictable cache. Files survive process restarts and source-file removal. As with
other private app data, uninstalling or clearing app storage removes them.

`replace` streams the new copy on the I/O dispatcher, flushes it, then commits the
Room record before deleting the replaced copy. Failed or cancelled copies retain
the old record and file. There is no expiration or automatic removal of active
files. Callers must finish reading a slot before replacing it; sorting prevents
replacement while a sort is running. Use the returned private URI only inside the
app (external sharing requires FileProvider).

Setup follows [Android's Room documentation](https://developer.android.com/training/data-storage/room).
