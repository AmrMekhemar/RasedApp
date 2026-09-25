package com.rased.feature.sorting.ui.components

import com.rased.feature.sorting.ui.IndexingProgress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun WalletInputCard(
    useTextWallet: Boolean,
    walletText: String,
    walletFileName: String?,
    onUseTextWalletChange: (Boolean) -> Unit,
    onWalletTextChange: (String) -> Unit,
    onPickWallet: () -> Unit,
    modifier: Modifier = Modifier,
    indexing: IndexingProgress? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SortingStyle.Border)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            InputStepHeading("٢", "المحفظة", "اختر طريقة إضافة اللوحات للمطابقة")
            Row(
                Modifier.fillMaxWidth().background(SortingStyle.Background, RoundedCornerShape(14.dp))
                    .padding(4.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(false to "ملف Excel", true to "لصق اللوحات").forEach { (isText, label) ->
                    val selected = useTextWallet == isText
                    Text(
                        label,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(11.dp))
                            .background(if (selected) SortingStyle.Teal else Color.Transparent)
                            .selectable(selected, role = Role.RadioButton, onClick = { onUseTextWalletChange(isText) })
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        color = if (selected) Color.White else SortingStyle.Muted,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (useTextWallet) {
                OutlinedTextField(
                    value = walletText, onValueChange = onWalletTextChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                    shape = RoundedCornerShape(16.dp),
                    minLines = 3, maxLines = 6,
                    label = { Text("اللوحات") },
                    supportingText = { Text("كل لوحة في سطر مستقل") },
                    placeholder = { Text("امط8845\nبكب1234") }
                )
            } else {
                FilePickerInline(walletFileName, "اختيار ملف المحفظة", onPickWallet, indexing = indexing)
                Text("سيتم قراءة أول شيت ظاهر في الملف", style = MaterialTheme.typography.bodySmall, color = SortingStyle.Muted)
            }
        }
    }
}

@Preview(showBackground = true, locale = "ar", name = "Excel wallet")
@Composable
private fun WalletFilePreview() {
    SortingTheme { WalletInputCard(false, "", "wallet.xlsx", {}, {}, {}) }
}

@Preview(showBackground = true, locale = "ar", name = "Pasted plates", widthDp = 320, fontScale = 1.3f)
@Composable
private fun WalletTextPreview() {
    SortingTheme { WalletInputCard(true, "ابج1234\nدهو5678", null, {}, {}, {}) }
}
