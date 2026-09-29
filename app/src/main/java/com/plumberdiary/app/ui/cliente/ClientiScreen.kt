// ClientiScreen.kt — v1.8.0 — 2026-09-29
//
// Elenco dell'anagrafica clienti condivisa dalla squadra (requisiti 8/11),
// raggiungibile dalle Opzioni. Aggiunta perché fino a v1.7.0 la scheda
// Cliente (anagrafica, foto, listino articoli, mandatino ore) esisteva ma
// nessuna schermata ci portava.
package com.plumberdiary.app.ui.cliente

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.plumberdiary.app.data.model.ClientRecord
import com.plumberdiary.app.data.repository.ClientRepository
import com.plumberdiary.app.ui.Routes
import com.plumberdiary.app.ui.common.rememberActiveSession
import kotlinx.coroutines.launch

@Composable
fun ClientiScreen(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val session = rememberActiveSession()
    val clientRepository = remember { ClientRepository() }

    var clients by remember { mutableStateOf<List<ClientRecord>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val s = session ?: return@LaunchedEffect
        try { clients = clientRepository.getAll(s.teamId).sortedBy { it.name.lowercase() } }
        catch (e: Exception) { message = e.message }
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) }
            Text("Anagrafica clienti", modifier = Modifier.weight(1f))
        }
        Text("Il listino articoli condiviso si gestisce dalla scheda di un qualsiasi cliente.")

        OutlinedTextField(
            value = filter, onValueChange = { filter = it },
            label = { Text("Cerca") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        val visible = clients.filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) }
        if (visible.isEmpty()) Text("Nessun cliente.")
        visible.forEach { c ->
            ElevatedCard(
                onClick = { navController.navigate(Routes.cliente(c.id)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(c.name)
                    val details = listOf(c.defaultAddressLabel, c.phone).filter { it.isNotBlank() }.joinToString(" · ")
                    if (details.isNotBlank()) Text(details)
                }
            }
        }

        OutlinedTextField(
            value = newName, onValueChange = { newName = it },
            label = { Text("Nome nuovo cliente") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        Button(
            enabled = newName.isNotBlank(),
            onClick = {
                val s = session ?: return@Button
                scope.launch {
                    try {
                        val id = clientRepository.create(
                            s.teamId,
                            ClientRecord(name = newName.trim(), createdBy = s.uid, createdAt = System.currentTimeMillis()),
                        )
                        newName = ""
                        navController.navigate(Routes.cliente(id))
                    } catch (e: Exception) { message = e.message }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Crea e apri la scheda") }

        message?.let { Text(it) }
    }
}
