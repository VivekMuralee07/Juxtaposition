package com.juxtaposition.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juxtaposition.app.AppViewModel

@Composable
fun MenuScreen(vm: AppViewModel) {
    val count = vm.cart.values.sum()
    Column(Modifier.fillMaxSize()) {
        Text("Pick your meal", Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.menu, key = { it.id }) { item ->
                val qty = vm.cart[item.id] ?: 0
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(if (item.veg) Color(0xFF2E7D32) else Color(0xFFC62828), CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Medium)
                            Text("~₹${item.basePrice}", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (qty == 0) {
                            OutlinedButton(onClick = { vm.changeQty(item.id, 1) }) { Text("ADD") }
                        } else {
                            IconButton(onClick = { vm.changeQty(item.id, -1) }) { Icon(Icons.Default.Remove, "Remove one") }
                            Text("$qty", fontWeight = FontWeight.Bold)
                            IconButton(onClick = { vm.changeQty(item.id, 1) }) { Icon(Icons.Default.Add, "Add one") }
                        }
                    }
                }
            }
        }
        Surface(shadowElevation = 8.dp) {
            Button(
                onClick = vm::compare, enabled = count > 0 && !vm.loading,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            ) { Text(if (count == 0) "Select items to compare" else "Compare prices ($count item${if (count > 1) "s" else ""})") }
        }
    }
}
