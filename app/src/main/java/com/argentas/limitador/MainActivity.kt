package com.argentas.limitador

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

private data class DemoSession(val id: Long, val label: String, val startedAt: Long)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LimitadorScreen() }
    }
}

@Composable
private fun LimitadorScreen() {
    val sessions = remember { mutableStateListOf<DemoSession>() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var shizukuStatus by remember { mutableStateOf("Comprobando Shizuku…") }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            shizukuStatus = try {
                if (Shizuku.pingBinder()) {
                    if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED)
                        "Shizuku conectado y permiso concedido"
                    else "Shizuku conectado; falta conceder permiso"
                } else "Shizuku no está iniciado"
            } catch (_: Throwable) { "Shizuku no disponible" }
            delay(1000)
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("LIMITADOR", style = MaterialTheme.typography.headlineMedium)
                Text("Hotspot objetivo: SISTA")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Estado de integración", style = MaterialTheme.typography.titleMedium)
                        Text(shizukuStatus)
                        Text("Esta versión todavía no modifica el hotspot ni desconecta dispositivos.")
                        Button(onClick = {
                            try {
                                if (Shizuku.pingBinder() &&
                                    Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) Shizuku.requestPermission(1001)
                            } catch (_: Throwable) { }
                        }) { Text("Pedir permiso") }
                    }
                }
                Text("Sesiones de prueba", style = MaterialTheme.typography.titleLarge)
                Text("El contador es local; no representa clientes reales conectados.")
                Button(
                    onClick = {
                        sessions.add(DemoSession(System.nanoTime(), "Cliente de prueba ${sessions.size + 1}", System.currentTimeMillis()))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Iniciar sesión de prueba (10 min)") }
                if (sessions.isEmpty()) Text("Todavía no hay sesiones.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sessions, key = { it.id }) { session ->
                        val remaining = (600_000L - (now - session.startedAt)).coerceAtLeast(0L)
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(session.label, style = MaterialTheme.typography.titleMedium)
                                    Text(if (remaining > 0) {
                                        "Restan %02d:%02d".format(remaining / 60_000, (remaining / 1000) % 60)
                                    } else "Tiempo agotado (sin expulsión real)")
                                }
                                Button(onClick = { sessions.remove(session) }) { Text("Quitar") }
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("Sin root · Prototipo 0.1.0", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
