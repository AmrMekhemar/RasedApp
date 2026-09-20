package com.rased.feature.sorting.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rased.feature.sorting.domain.SortingResult
import com.rased.feature.sorting.R

private val CardGreen = Color(0xFF118F86)
private val CardMint = Color(0xFFD9F5ED)
private val CardBorder = Color(0xFFD8DEDE)
private val CardText = Color(0xFF4F595F)

/** Compact reference-style card; tapping it reveals the complete record. */
@Composable
internal fun SortingResultCard(result: SortingResult) {
    var expanded by rememberSaveable(result.plate) { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFCFCFC)),
        border = BorderStroke(1.dp, if (expanded) CardGreen.copy(alpha = .55f) else CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ResultCardHeader(result, expanded)
            ResultChipRow(result)
            LocationChip(result.street)
            LocationLink(result.location, Modifier.fillMaxWidth().padding(vertical = 8.dp))
            ResultChipRow(ResultChipData("التاريخ", result.date ?: "—"))
            if (expanded) ExpandedResultDetails(result)
            else Text("اضغط لعرض تفاصيل الحالة", color = CardText.copy(alpha = .7f),
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.Start))
        }
    }
}

@Composable
private fun ResultCardHeader(result: SortingResult, expanded: Boolean) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("▱", color = CardGreen, style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(end = 8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(result.plate, color = Color(0xFF20282C), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text(if (expanded) "تم فتح تفاصيل النتيجة" else "فرز جديد",
                color = CardGreen, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.background(CardMint, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("نتيجة الفرز", result.cardDetails()))
            }) {
                Icon(painterResource(R.drawable.ic_copy_result), contentDescription = "نسخ النتيجة", tint = CardText,
                    modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, result.cardDetails())
                }
                context.startActivity(Intent.createChooser(sendIntent, "مشاركة النتيجة"))
            }) {
                Icon(painterResource(R.drawable.ic_share_result), contentDescription = "مشاركة النتيجة", tint = CardText,
                    modifier = Modifier.size(24.dp))
            }
        }
    }
}

private fun SortingResult.cardDetails(): String = listOf(
    "اللوحة" to plate,
    "النوع" to type,
    "الحي" to district,
    "الشارع" to street,
    "الموقع" to location,
    "التاريخ" to date,
    "نوع المحفظة" to walletType,
    "الملاحظة" to note
).joinToString("\n") { (label, value) -> "$label: ${value?.takeIf { it.isNotBlank() } ?: "—"}" }

private data class ResultChipData(val label: String, val value: String)

@Composable
private fun ResultChipRow(result: SortingResult) {
    val chips = listOf(ResultChipData("النوع", result.type ?: "—"), ResultChipData("الحي", result.district ?: "—"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.Start)) {
        chips.forEach { ResultChip(it) }
    }
}

@Composable
private fun ResultChipRow(chip: ResultChipData) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { ResultChip(chip) }
}

@Composable
private fun ResultChip(chip: ResultChipData) {
    Text("${chip.label}: ${chip.value}", color = CardText, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.background(Color(0xFFF4F5F5), RoundedCornerShape(9.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp))
}

@Composable
private fun LocationChip(street: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Text("⌖  الشارع: ${street ?: "—"}", color = CardGreen, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().background(CardMint.copy(alpha = .7f), RoundedCornerShape(9.dp))
                .padding(horizontal = 11.dp, vertical = 8.dp))
    }
}

@Composable
private fun ExpandedResultDetails(result: SortingResult) {
    HorizontalDivider(color = CardBorder)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
        LocationLink(result.location, Modifier.fillMaxWidth(), maxLines = Int.MAX_VALUE)
        DetailLine("نوع المحفظة", result.walletType)
        DetailLine("الملاحظة", result.note)
        Text("اضغط للطي", color = CardText.copy(alpha = .72f),
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.Start))
    }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = CardText, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(end = 12.dp))
        Text(value?.takeIf { it.isNotBlank() } ?: "—", color = Color(0xFF20282C),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Preview(showBackground = true, locale = "ar", widthDp = 360, name = "Reference card")
@Preview(showBackground = true, locale = "ar", widthDp = 360, fontScale = 1.4f, name = "Large text")
@Composable
private fun SortingResultCardPreview() {
    SortingTheme {
        SortingResultCard(SortingResult("حجس6703", "سيارة", "ملاحظة حالة طويلة تظهر بالكامل عند فتح البطاقة.",
            "شارع 448طويل", "الحي المنشاد", "2026-08-31", "خاصة"))
    }
}
