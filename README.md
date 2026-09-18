# راصد - Rased Android MVP

تطبيق Android محلي بالكامل لتنفيذ أول نسخة من قسم الفرز.

## ما الموجود في النسخة

- Kotlin + Jetpack Compose
- Local only بدون Login وبدون Backend
- عربي RTL بالكامل
- تصميم بسيط بخلفية بيضاء
- شاشة رئيسية بها:
  - الفرز
  - التفريغ - قريبًا
  - التشييك - قريبًا
- قسم الفرز يدعم:
  - اختيار ملف الداتا Excel
  - اختيار ملف المحفظة Excel أو لصق اللوحات يدويًا
  - قراءة شيت `داتا` من ملف الداتا
  - قراءة شيت `ورقة1` من ملف المحفظة
  - التعرف على عمود اللوحة من: `اللوحة` / `اللوحه` / `لوحة` / `لوحه`
  - التعرف على نوع المحفظة من: `النوع` / `الماركة` / `الموديل`
  - توحيد اللوحات قبل المطابقة
  - عرض المطابق فقط
  - منع التكرارات
  - عرض أول صف فقط عند تكرار اللوحة في الداتا
  - نسخ النتائج بصيغة قابلة للصق في Excel

## طريقة التشغيل

1. فك ضغط الملف.
2. افتح فولدر `RasedApp` من Android Studio.
3. اختر JDK 17 في إعدادات Gradle ثم اضغط Sync Gradle.
4. شغل التطبيق على Emulator أو موبايل Android.

## ملاحظات مهمة

- يدعم `.xlsx` فقط في هذه النسخة.
- ملف الداتا يجب أن يحتوي على Sheet باسم `داتا`.
- ملف المحفظة يجب أن يحتوي على Sheet باسم `ورقة1`.
- حفظ النتائج كاملة في ملف Excel بصيغة `.xlsx`.

## Project structure

The app uses Android library feature modules. Features do not depend on the app
or on one another:

```text
:app                 Activity, home screen, and navigation
:feature:sorting     الفرز: UI, state/ViewModel, data repository, and domain rules
:feature:unloading   التفريغ: dedicated screen (قريبًا)
:feature:checking    التشييك: dedicated screen (قريبًا)
:core:ui             Shared RTL theme, feature scaffold, and placeholder content
:core:excel          Streaming XLSX reader and writer
```

`app` depends on the three feature modules and `core:ui`. Sorting depends on
`core:ui` and `core:excel`; the other features depend only on `core:ui`.
Unloading and checking have separate destinations; their business workflows
are not implemented yet.

Within sorting, `SortingRoute` connects Android file pickers and the ViewModel.
`SortingScreen` receives state and callbacks, and delegates to separate wallet,
file-picker, results-actions, and results-table components. The ViewModel
coordinates state and background jobs; `SortingRepository` owns file access and
matching, `SortingStore` owns SQLite storage, and `domain` holds the matching
models and normalization rules.

Open a screen or component Kotlin file in Android Studio's Split/Design view
to see its `@Preview`. Previews use the shared Arabic RTL theme and sample data,
without constructing a ViewModel or opening files. Sorting has empty, loading,
error, and results previews, plus wallet and result component previews.
Home, unloading, and checking each have their own preview.

Build all modules and run their lint checks:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest lintDebug
```

## Large Excel regression check

The reader streams worksheet XML and stores shared strings in temporary cache
files. Wallet plates, matching, and results use a temporary SQLite database;
only a 200-row window is loaded for the table. Memory usage no longer grows with
the number of wallet rows or matching results. First occurrences and wallet
order are preserved. The database is replaced on the next sort and closed when
the ViewModel is cleared. Reader temporary files are removed after each read,
including failed reads.

Use **حفظ النتائج Excel** to stream all results to a native Excel `.xlsx`
file. Clipboard copying is limited to small result sets to avoid large heap
allocations and Android clipboard transaction limits.

With JDK 17 and an Android emulator/device connected:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.rased.app.test/com.rased.app.XlsxRegressionInstrumentation
```

The device check prints `PASS` or `FAIL`. It generates a worksheet larger than
140 MiB uncompressed, followed by an XLSX archive exceeding 15 MB with 30,000
distinct matching plates and long shared-string notes. It runs the production
ViewModel and Compose table, checks forward/backward paging and horizontal
scrolling, duplicate suppression, first-match handling, wallet order, no matches,
bounded clipboard copying, full XLSX export, and reader temporary-file cleanup.
It reports peak sampled Java heap usage against the device's normal heap limit.
