package com.rased.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rased.app.domain.SortingResult
import com.rased.app.ui.SortingViewModel
import java.io.File

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
                    enabled = !state.isLoading
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

            if (state.results.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("النتائج: ${state.results.size}", fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("نتائج راصد", viewModel.tsvResults()))
                            viewModel.clearMessage()
                        }) { Text("نسخ النتائج") }
                    }
                }
                items(state.results) { result -> ResultCard(result) }
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

@Composable
private fun ResultCard(result: SortingResult) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(result.plate, fontWeight = FontWeight.Bold)
            Divider()
            Text("النوع: ${result.type.orEmpty()}")
            Text("الملاحظة: ${result.note.orEmpty()}")
            Text("الشارع: ${result.street.orEmpty()}")
            Text("الحي: ${result.district.orEmpty()}")
            Text("التاريخ: ${result.date.orEmpty()}")
            Text("نوع المحفظة: ${result.walletType.orEmpty()}")
        }
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
