# راصد - Rased Android MVP

تطبيق Android محلي بالكامل لتنفيذ أول نسخة من قسم الفرز.

## ما الموجود في النسخة

- Kotlin + Jetpack Compose
- Local only بدون Login وبدون Backend
- عربي RTL بالكامل
- تصميم بسيط بخلفية بيضاء
- شاشة رئيسية بها:
  - الفرز
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
- لا يوجد Export Excel في هذه النسخة، النسخ فقط.

## Large Excel regression check

The reader streams worksheet XML, stores shared strings in temporary cache files,
and keeps only the first data row matching each wallet plate. Temporary files are
removed after each read, including failed reads.

With JDK 17 and an Android emulator/device connected:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.rased.app.test/com.rased.app.XlsxRegressionInstrumentation
```

The device check prints `PASS` or `FAIL`. It generates a worksheet larger than
140 MiB uncompressed and checks shared strings, first-match handling, wallet
order, duplicate suppression, no matches, and temporary-file cleanup.
