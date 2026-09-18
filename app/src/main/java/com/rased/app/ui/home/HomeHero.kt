package com.rased.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rased.core.ui.RasedTheme

@Composable
internal fun HomeHero() {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF142D40), Color(0xFF164F55))))
    ) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width * 0.08f, size.height * 0.22f)
            listOf(48.dp, 88.dp, 128.dp).forEach {
                drawCircle(Color.White.copy(alpha = 0.07f), it.toPx(), center, style = Stroke(1.dp.toPx()))
            }
            drawCircle(Color(0xFF74D9C7).copy(alpha = 0.5f), 4.dp.toPx(), center + Offset(45.dp.toPx(), 25.dp.toPx()))
        }
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("أهلاً بك في راصد", color = Color(0xFF9BDCD1), style = MaterialTheme.typography.labelLarge)
            Text("كل لوحة،\nفي مكانها الصحيح.", color = Color.White,
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("نظّم بياناتك وطابق اللوحات\nبخطوات بسيطة وواضحة.",
                color = Color(0xFFD2E3E7), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true, locale = "ar", widthDp = 360)
@Composable
private fun HomeHeroPreview() {
    RasedTheme { HomeHero() }
}
