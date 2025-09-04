package com.example.logipass

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.logipass.data.VaultRepository
import com.example.logipass.model.Credential
import com.example.logipass.model.ServiceItem
import com.example.logipass.ui.theme.LogiPassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = VaultRepository(this)
        setContent {
            LogiPassTheme {
                AppScreen(repo)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(repo: VaultRepository) {
    var items by remember { mutableStateOf<List<ServiceItem>>(emptyList()) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        items = repo.loadFromAppStorage()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selected ->
            val imported = repo.importFromUri(context.contentResolver, selected)
            if (imported.isNotEmpty()) items = imported
        }
    }

    // CreateDocument contract requires a mimeType up-front
    val exportLauncher = rememberLauncherForActivityResult(
        object : ActivityResultContracts.CreateDocument("application/json") {}
    ) { uri: Uri? ->
        uri?.let { selected ->
            repo.exportToUri(context.contentResolver, selected, items)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("LogiPass") },
                actions = {
                    IconButton(onClick = {
                        // Import JSON
                        importLauncher.launch(arrayOf("application/json"))
                    }) {
                        Icon(Icons.Outlined.FileOpen, contentDescription = "Импорт")
                    }
                    IconButton(onClick = {
                        // Export JSON
                        exportLauncher.launch("logipass.json")
                    }) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "Экспорт")
                    }
                }
            )
        }
    ) { innerPadding ->
        ServiceList(
            items = items,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun ServiceList(items: List<ServiceItem>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items) { item ->
            ServiceCard(item)
        }
    }
}

@Composable
fun ServiceCard(item: ServiceItem) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.service, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expanded) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.credentials.forEach { cred ->
                        CredentialRow(cred)
                    }
                }
            }
        }
    }
}

@Composable
fun CredentialRow(cred: Credential) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Логин: ${cred.username}", style = MaterialTheme.typography.bodyMedium)
            Text("Пароль: ${cred.password}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            cred.additional_info?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewServiceList() {
    LogiPassTheme {
        ServiceList(
            items = listOf(
                ServiceItem(
                    service = "ChatGPT",
                    description = "Генеративная нейросеть",
                    credentials = listOf(
                        Credential("user@example.com", "password1", "Запасная учетная запись"),
                        Credential("another@example.com", "password2", "Вход через учётку Google")
                    )
                ),
                ServiceItem(
                    service = "Сбербанк",
                    description = "Банк",
                    credentials = listOf(
                        Credential("login", "pass", "Мой основной аккаунт")
                    )
                )
            )
        )
    }
}
