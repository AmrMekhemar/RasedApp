package com.rased.feature.sorting.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rased.core.ui.RasedTheme

@Composable
internal fun WalletInputCard(
    useTextWallet: Boolean,
    walletText: String,
    walletFileName: String?,
    onUseTextWalletChange: (Boolean) -> Unit,
    onWalletTextChange: (String) -> Unit,
    onPickWallet: () -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFF7F7F7))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("المحفظة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = !useTextWallet, onClick = { onUseTextWalletChange(false) })
                Text("رفع ملف Excel")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = useTextWallet, onClick = { onUseTextWalletChange(true) })
                Text("لصق لوحات، كل لوحة في سطر")
            }
            if (useTextWallet) {
                OutlinedTextField(
                    value = walletText,
                    onValueChange = onWalletTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    label = { Text("الصق اللوحات هنا") },
                    placeholder = { Text("امط8845\nبكب1234") }
                )
            } else {
                FilePickerInline(
                    fileName = walletFileName,
                    text = "اختيار ملف المحفظة - شيت ورقة1",
                    onPick = onPickWallet
                )
            }
        }
    }
}

@Preview(showBackground = true, locale = "ar", name = "Excel wallet")
@Composable
private fun WalletFilePreview() {
    RasedTheme { WalletInputCard(false, "", "wallet.xlsx", {}, {}, {}) }
}

@Preview(showBackground = true, locale = "ar", name = "Pasted plates")
@Composable
private fun WalletTextPreview() {
    RasedTheme { WalletInputCard(true, "ابج1234\nدهو5678", null, {}, {}, {}) }
}
