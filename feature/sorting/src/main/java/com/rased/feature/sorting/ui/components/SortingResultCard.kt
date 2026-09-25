package com.rased.feature.sorting.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rased.feature.sorting.R
import com.rased.feature.sorting.domain.SortingResult

private val CardInk = Color(0xFF091426)
private val CardMuted = Color(0xFF6C788B)
private val CardLine = Color(0xFFEBEFF5)
private val CardMint = Color(0xFFEDF8F4)
private val CardGreen = Color(0xFF00976D)

/** Reference layout with optional sections that collapse when their values are blank. */
@Composable
internal fun SortingResultCard(result: SortingResult) {
    val hasAddress = !result.district.isNullOrBlank() || !result.street.isNullOrBlank()
    val hasFooter = !result.date.isNullOrBlank() || !result.location.isNullOrBlank()
    val hasDetails = hasAddress || hasFooter || !result.note.isNullOrBlank()
    var expanded by rememberSaveable(result) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ResultCardHeader(result)
            if (hasDetails) {
                HorizontalDivider(color = CardLine)
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                        stateDescription = if (expanded) "موسّع" else "مطوي"
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(if (expanded) "إخفاء التفاصيل" else "عرض التفاصيل")
                }
                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (hasAddress) {
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (!result.district.isNullOrBlank())
                                    AddressField("الحي", result.district, R.drawable.ic_result_pin, Modifier.weight(1f))
//                                if (!result.district.isNullOrBlank() && !result.street.isNullOrBlank()) SectionDivider()
                            }
                        }
                        if (hasFooter) ResultCardFooter(result)
                        if (!result.note.isNullOrBlank()) {
                            Text(buildAnnotatedString {
                                withStyle(SpanStyle(color = CardMuted)) { append("الملاحظة: ") }
                                append(result.note)
                            }, color = CardInk, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCardHeader(result: SortingResult) {
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            Box(
                modifier = Modifier.size(48.dp)
                    .background(Color(0xFFE2EFFE), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_result_car), null, tint = Color(0xFF387DBA),
                    modifier = Modifier.size(28.dp))
            }
            if (result.plate.isNotBlank()) {
                Text(result.plate, color = CardInk, fontWeight = FontWeight.ExtraBold,
                    fontSize = 25.sp, lineHeight = 32.sp, modifier = Modifier.weight(1f))
            } else Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CardAction(R.drawable.ic_copy_result, "نسخ النتيجة") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("نتيجة الفرز", result.cardDetails()))
                }
                CardAction(R.drawable.ic_share_result, "مشاركة النتيجة") {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, result.cardDetails())
                    }
                    context.startActivity(Intent.createChooser(intent, "مشاركة النتيجة"))
                }
            }
        }
        if (!result.type.isNullOrBlank() || !result.walletModel.isNullOrBlank() || !result.walletType.isNullOrBlank()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(R.drawable.ic_result_truck), null, tint = CardMuted,
                    modifier = Modifier.size(18.dp))
                Text(buildAnnotatedString {
                    val fields = listOf(
                        "النوع" to result.type,
                        "الطراز" to result.walletModel,
                        "اللون" to result.walletType,
                        "الشارع" to result.street
                    ).filter { !it.second.isNullOrBlank() }
                    fields.forEachIndexed { index, (label, value) ->
                        if (index > 0) withStyle(SpanStyle(color = CardMuted)) { append("  |  ") }
                        withStyle(SpanStyle(color = CardMuted)) { append("$label: ") }
                        append(value)
                    }
                }, color = CardInk, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CardAction(icon: Int, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)
        .border(1.dp, CardLine, RoundedCornerShape(12.dp))) {
        Icon(painterResource(icon), label, tint = Color(0xFF42536B), modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun AddressField(label: String, value: String, icon: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(painterResource(icon), null, tint = CardMuted, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = CardMuted, style = MaterialTheme.typography.bodySmall)
            Text(value, color = CardInk, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SectionDivider(color: Color = CardLine) {
    Spacer(Modifier.width(1.dp).fillMaxHeight().background(color))
}

@Composable
private fun ResultCardFooter(result: SortingResult) {
    val context = LocalContext.current
    val uri = LocationLinks.uri(result.location)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8 .dp))
        .background(CardMint)
        .border(BorderStroke(0.5.dp, Color(0xFFD6F1E6)), RoundedCornerShape(8.dp))
        .height(IntrinsicSize.Min)
        .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!result.date.isNullOrBlank()) {
            Row(Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(painterResource(R.drawable.ic_result_calendar), null, tint = CardMuted,
                    modifier = Modifier.size(24.dp))
                Column(
                   modifier =  Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("التاريخ", color = CardMuted, style = MaterialTheme.typography.labelSmall)
                    Text(result.date, color = CardInk, style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium)
                }
            }
        }
        if (!result.date.isNullOrBlank() && !result.location.isNullOrBlank()) SectionDivider(Color(0xFFCEEDE2))
        if (!result.location.isNullOrBlank()) {
            Row(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
                .then(if (uri != null) Modifier.clickable(role = Role.Button, onClickLabel = "فتح الموقع") {
                    LocationLinks.open(context, uri)
                } else Modifier), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                Text(if (uri != null) "فتح الموقع" else result.location,
                    color = if (uri != null) CardGreen else CardInk,
                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f, fill = false))
                if (uri != null) Icon(painterResource(R.drawable.ic_result_open), null,
                    tint = CardGreen, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun SortingResult.cardDetails(): String = listOf(
    "اللوحة" to plate, "النوع" to type, "الحي" to district, "الشارع" to street,
    "الموقع" to location, "التاريخ" to date, "اللون" to walletType, "الملاحظة" to note
).filter { (_, value) -> !value.isNullOrBlank() }
    .joinToString("\n") { (label, value) -> "$label: $value" }

@Preview(showBackground = true, locale = "ar", widthDp = 360, name = "Reference card")
@Preview(showBackground = true, locale = "ar", widthDp = 320, fontScale = 1.4f, name = "Narrow with large text")
@Composable
private fun SortingResultCardPreview() {
    SortingTheme {
        SortingResultCard(SortingResult("بعب 2577", "نقل", null,
            "ع130 ذهاب", "المغرزات", "الأحد 16/8/2026", "2023", "https://maps.google.com"))
    }
}

@Preview(showBackground = true, locale = "ar", widthDp = 360, name = "Sparse card")
@Composable
private fun SparseSortingResultCardPreview() {
    SortingTheme { SortingResultCard(SortingResult("بعب 2577", null, null, null, null, null, null)) }
}
