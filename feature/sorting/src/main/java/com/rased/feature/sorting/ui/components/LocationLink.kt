package com.rased.feature.sorting.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow

object LocationLinks {
    fun uri(value: String?): Uri? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = Uri.parse(if (text.startsWith("www.", ignoreCase = true)) "https://$text" else text)
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> uri.takeIf { !it.host.isNullOrBlank() }
            "geo", "google.navigation" -> uri.takeIf { !it.schemeSpecificPart.isNullOrBlank() }
            else -> null
        }
    }

    fun open(context: Context, uri: Uri) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "لا يوجد تطبيق لفتح رابط الموقع", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "تعذر فتح رابط الموقع", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
internal fun LocationLink(value: String?, modifier: Modifier = Modifier, showLabel: Boolean = true, maxLines: Int = 1) {
    val context = LocalContext.current
    val uri = LocationLinks.uri(value)
    val text = value?.takeIf { it.isNotBlank() } ?: "—"
    Text(
        text = if (showLabel) "الموقع: $text" else text,
        modifier = if (uri == null) modifier else modifier.clickable(role = Role.Button, onClickLabel = "فتح الموقع") {
            LocationLinks.open(context, uri)
        },
        color = if (uri == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        textDecoration = if (uri == null) null else TextDecoration.Underline,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}
