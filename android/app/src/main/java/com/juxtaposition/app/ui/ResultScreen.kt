package com.juxtaposition.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juxtaposition.app.AppViewModel
import com.juxtaposition.app.Comparison
import com.juxtaposition.app.Quote

private fun parseColor(hex: String) = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Muted)

@Composable
fun ResultScreen(vm: AppViewModel) {
    val c = vm.comparison ?: return
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Summary(c) }
        if (c.simulated) item {
            Text("Demo mode: prices and times are simulated by the server. Connect a live data source to see real prices.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        items(c.results, key = { it.platform }) { QuoteCard(it, isBest = it.platform == c.bestPlatform) }
        item { Text("Estimates only. Final price and delivery time are confirmed in each app at checkout.",
            style = MaterialTheme.typography.labelSmall, color = Muted) }
    }
}

@Composable
private fun Summary(c: Comparison) {
    val best = c.results.firstOrNull { it.platform == c.bestPlatform }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (best == null) {
                Text("Not deliverable right now", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("None of the apps can deliver this order to your location.", color = Muted)
            } else {
                Text("Best deal: ${best.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Green)
                Text("₹${best.total} • ${best.etaMinutes} min", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                if (c.maxSavings > 0) Text("Save up to ₹${c.maxSavings} vs the priciest app", color = Green)
            }
            Text(c.restaurant.name, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun QuoteCard(q: Quote, isBest: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val accent = parseColor(q.colorHex)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isBest) BorderStroke(2.dp, Green) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(accent, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(q.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (q.available) Text("₹${q.total}", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            }
            if (!q.available) {
                Text(q.reason ?: "Unavailable", color = Muted, modifier = Modifier.padding(top = 4.dp))
                return@Column
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (q.isCheapest) Tag("Cheapest", Green)
                if (q.isFastest) Tag("Fastest", Color(0xFF2563EB))
                if (q.isBestValue) Tag("Best value", Amber)
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, Modifier.size(18.dp), tint = Muted)
                Text(" ${q.etaMinutes} min", fontWeight = FontWeight.SemiBold)
                Text("  •  ${q.distanceKm} km", color = Muted)
                if (q.couponCode != null) Text("  •  ${q.couponCode} -₹${q.discount}", color = Green)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 10.dp)) {
                    q.lines.forEach { Row2("${it.qty} × ${it.name}", "₹${it.lineTotal}") }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row2("Delivery fee", "₹${q.deliveryFee}")
                    Row2("Platform fee", "₹${q.platformFee}")
                    Row2("Taxes & charges", "₹${q.taxes}")
                    if (q.discount > 0) Row2("Discount (${q.couponCode})", "-₹${q.discount}", Green)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row2("Total", "₹${q.total}", bold = true)
                }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide breakdown" else "Price breakdown") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { openApp(ctx, q) }, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("Open app") }
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(10.dp, 3.dp), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Row2(left: String, right: String, color: Color = Color.Unspecified, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(left, Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else null, color = color)
        Text(right, fontWeight = if (bold) FontWeight.Bold else null, color = color)
    }
}

/** Launch the delivery app if installed; otherwise open its Play Store page. */
private fun openApp(ctx: Context, q: Quote) {
    val pkg = q.androidPackage
    val launch = pkg?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
    val intent = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${pkg ?: ""}"))
    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure {
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${pkg ?: ""}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
}
