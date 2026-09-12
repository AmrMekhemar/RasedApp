package com.rased.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rased.app.domain.SortingResult
import com.rased.app.ui.SortingViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RasedApp() }
    }
}

private enum class Screen { Home, Sorting, ComingSoon }

@Composable
fun RasedApp() {
    var screen by remember { mutableStateOf(Screen.Home) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            background = androidx.compose.ui.graphics.Color.White,
            surface = androidx.compose.ui.graphics.Color.White,
            primary = androidx.compose.ui.graphics.Color(0xFF111111)
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
            ) {
                when (screen) {
                    Screen.Home -> HomeScreen(
                        onOpenSorting = { screen = Screen.Sorting },
                        onOpenComingSoon = { screen = Screen.ComingSoon }
                    )
                    Screen.Sorting -> SortingScreen(onBack = { screen = Screen.Home })
                    Screen.ComingSoon -> ComingSoonScreen(onBack = { screen = Screen.Home })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onOpenSorting: () -> Unit, onOpenComingSoon: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("راصد", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(androidx.compose.ui.graphics.Color.White)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("أهلاً بك في راصد", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("اختر القسم المطلوب")

            SectionCard(title = "الفرز", subtitle = "مطابقة ملف الداتا مع المحفظة", enabled = true, onClick = onOpenSorting)
            SectionCard(title = "التشييك", subtitle = "قريبًا", enabled = false, onClick = onOpenComingSoon)
        }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (enabled) androidx.compose.ui.graphics.Color(0xFFF7F7F7) else androidx.compose.ui.graphics.Color(0xFFFAFAFA)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = androidx.compose.ui.graphics.Color.DarkGray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortingScreen(onBack: () -> Unit, viewModel: SortingViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val excelMimeTypes = arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/octet-stream",
        "application/zip",
        "*/*"
    )

    val dataPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.takeReadPermission(it); viewModel.setDataFile(it) }
    }
    val walletPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.takeReadPermission(it); viewModel.setWalletFile(it) }
    }
    val resultsSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/tab-separated-values")) { uri ->
        uri?.let(viewModel::exportResults)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("قسم الفرز") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                FilePickerCard(
                    title = "ملف الداتا",
                    description = "يجب أن يحتوي على شيت باسم: داتا",
                    uri = state.dataFileUri,
                    buttonText = "اختيار ملف الداتا",
                    onPick = { dataPicker.launch(excelMimeTypes) }
                )
            }

            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFF7F7F7))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("المحفظة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !state.useTextWallet, onClick = { viewModel.setUseTextWallet(false) })
                            Text("رفع ملف Excel")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = state.useTextWallet, onClick = { viewModel.setUseTextWallet(true) })
                            Text("لصق لوحات، كل لوحة في سطر")
                        }
                        if (state.useTextWallet) {
                            OutlinedTextField(
                                value = state.walletText,
                                onValueChange = viewModel::setWalletText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                label = { Text("الصق اللوحات هنا") },
                                placeholder = { Text("امط8845\nبكب1234") }
                            )
                        } else {
                            FilePickerInline(
                                uri = state.walletFileUri,
                                text = "اختيار ملف المحفظة - شيت ورقة1",
                                onPick = { walletPicker.launch(excelMimeTypes) }
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = viewModel::startSorting,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && !state.isExporting
                ) { Text(if (state.isLoading) "جاري الفرز..." else "ابدأ الفرز") }
            }

            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            state.message?.let { message ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFF1F1F1))) {
                        Text(message, modifier = Modifier.padding(14.dp))
                    }
                }
            }

            if (state.resultCount > 0) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("النتائج: ${state.resultCount}", fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = viewModel::copyResults, enabled = !state.isExporting) { Text("نسخ النتائج") }
                    }
                }
                item {
                    OutlinedButton(onClick = { resultsSaver.launch("Rased-results.tsv") }, enabled = !state.isExporting) {
                        Text(if (state.isExporting) "جاري تجهيز النتائج..." else "حفظ النتائج كاملة")
                    }
                }
                item { ResultsTable(state.results, state.resultCount, state.resultStart, viewModel::loadVisibleRows) }
            }
        }
    }
}

@Composable
private fun FilePickerCard(title: String, description: String, uri: Uri?, buttonText: String, onPick: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFF7F7F7))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description)
            FilePickerInline(uri = uri, text = buttonText, onPick = onPick)
        }
    }
}

@Composable
private fun FilePickerInline(uri: Uri?, text: String, onPick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPick) { Text(text) }
        Text(
            text = uri?.lastPathSegment ?: "لم يتم اختيار ملف",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = androidx.compose.ui.graphics.Color.DarkGray
        )
    }
}

private data class ResultColumn(
    val title: String,
    val width: Dp,
    val value: (SortingResult) -> String?
)

private val resultColumns = listOf(
    ResultColumn("اللوحة", 140.dp) { it.plate },
    ResultColumn("النوع", 160.dp) { it.type },
    ResultColumn("الملاحظة", 280.dp) { it.note },
    ResultColumn("الشارع", 180.dp) { it.street },
    ResultColumn("الحي", 160.dp) { it.district },
    ResultColumn("التاريخ", 180.dp) { it.date },
    ResultColumn("نوع المحفظة", 180.dp) { it.walletType }
)

@Composable
internal fun ResultsTable(
    results: List<SortingResult>,
    resultCount: Int,
    resultStart: Int,
    onVisibleRow: (Int) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    horizontalScroll: ScrollState = rememberScrollState()
) {
    LaunchedEffect(listState, onVisibleRow) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { onVisibleRow(it) }
    }
    // One horizontal viewport keeps the header and every row aligned. The
    // bounded LazyColumn composes only visible rows, even for large workbooks.
    Box(Modifier.fillMaxWidth().horizontalScroll(horizontalScroll)) {
        Column(
            Modifier.width(resultColumns.fold(0.dp) { width, column -> width + column.width })
                .height(420.dp)
        ) {
            Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                resultColumns.forEach { column ->
                    ResultCell(column.title, column.width, bold = true)
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
                items(count = resultCount) { index ->
                    val result = results.getOrNull(index - resultStart)
                    if (result == null) {
                        Row { resultColumns.forEach { ResultCell("…", it.width) } }
                    } else {
                        ResultCard(result, index)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: SortingResult, index: Int) {
    Row(
        Modifier.background(
            if (index % 2 == 0) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        resultColumns.forEachIndexed { columnIndex, column ->
            ResultCell(column.value(result).orEmpty(), column.width, bold = columnIndex == 0)
        }
    }
}

@Composable
private fun ResultCell(value: String, width: Dp, bold: Boolean = false) {
    Box(
        modifier = Modifier.width(width).height(48.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComingSoonScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التشييك") },
                navigationIcon = { TextButton(onClick = onBack) { Text("رجوع") } }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text("قريبًا", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private fun Context.takeReadPermission(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
