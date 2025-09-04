package com.example.logipass

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.example.logipass.data.VaultRepository
import com.example.logipass.model.Credential
import com.example.logipass.model.ServiceItem
import com.example.logipass.ui.screens.SettingsScreen
import com.example.logipass.ui.theme.LogiPassTheme

enum class Screen {
    MAIN, SETTINGS
}

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
    var currentScreen by remember { mutableStateOf(Screen.MAIN) }
    var expandedItems by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var backPressedTime by remember { mutableStateOf(0L) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        items = repo.loadFromAppStorage()
    }

    BackHandler {
        when (currentScreen) {
            Screen.SETTINGS -> {
                currentScreen = Screen.MAIN
            }
            Screen.MAIN -> {
                if (expandedItems.isNotEmpty()) {
                    expandedItems = emptySet()
                } else {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - backPressedTime < 2000) {
                        (context as? Activity)?.finishAffinity()
                        exitProcess(0)
                    } else {
                        backPressedTime = currentTime
                        Toast.makeText(context, "Нажмите назад еще раз для выхода", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        when (currentScreen) {
                            Screen.MAIN -> "LogiPass"
                            Screen.SETTINGS -> "Настройки"
                        }
                    ) 
                },
                navigationIcon = {
                    if (currentScreen == Screen.SETTINGS) {
                        IconButton(onClick = { currentScreen = Screen.MAIN }) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                actions = {
                    if (currentScreen == Screen.MAIN) {
                        IconButton(onClick = { currentScreen = Screen.SETTINGS }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when (currentScreen) {
            Screen.MAIN -> {
                ServiceList(
                    items = items,
                    expandedItems = expandedItems,
                    onItemExpanded = { index, expanded ->
                        expandedItems = if (expanded) {
                            expandedItems + index
                        } else {
                            expandedItems - index
                        }
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            Screen.SETTINGS -> {
                SettingsScreen(
                    repo = repo,
                    items = items,
                    onItemsUpdated = { newItems -> items = newItems },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
fun ServiceList(
    items: List<ServiceItem>, 
    expandedItems: Set<Int>,
    onItemExpanded: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items.size) { index ->
            ServiceCard(
                item = items[index],
                expanded = expandedItems.contains(index),
                onExpandedChange = { expanded -> onItemExpanded(index, expanded) }
            )
        }
    }
}

@Composable
fun ServiceCard(
    item: ServiceItem, 
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandedChange(!expanded) },
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
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Логин: ${cred.username}", style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(cred.username))
                    Toast.makeText(context, "Логин скопирован", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Скопировать логин")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val passwordText = if (showPassword) cred.password else "••••••"
                Text("Пароль: $passwordText", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        val icon = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility
                        val desc = if (showPassword) "Скрыть пароль" else "Показать пароль"
                        Icon(icon, contentDescription = desc)
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(cred.password))
                        Toast.makeText(context, "Пароль скопирован", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Скопировать пароль")
                    }
                }
            }

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
            ),
            expandedItems = setOf(0),
            onItemExpanded = { _, _ -> }
        )
    }
}
