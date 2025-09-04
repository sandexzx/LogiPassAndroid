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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import android.widget.Toast
import com.example.logipass.data.VaultRepository
import com.example.logipass.model.Credential
import com.example.logipass.model.ServiceItem
import com.example.logipass.ui.screens.SettingsScreen
import com.example.logipass.ui.theme.LogiPassTheme
import com.example.logipass.ui.components.CredentialDialog
import com.example.logipass.ui.components.ServiceDialog
import com.example.logipass.ui.components.ConfirmDialog

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
    var searchInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog states
    var serviceDialog by remember { mutableStateOf<ServiceDialogState?>(null) }
    var credentialDialog by remember { mutableStateOf<CredentialDialogState?>(null) }
    var confirmDialog by remember { mutableStateOf<ConfirmDialogState?>(null) }

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

    fun persist(newItems: List<ServiceItem>, message: String? = null) {
        items = newItems
        repo.saveToAppStorage(newItems)
        message?.let {
            coroutineScope.launch { snackbarHostState.showSnackbar(it) }
        }
    }

    fun normalize(s: String?): String = s?.lowercase()?.trim() ?: ""

    fun tokenizeQuery(q: String): List<String> = q.lowercase().trim().split(Regex("\\s+")).filter { it.isNotBlank() }

    val queryTokens by remember(searchQuery) { derivedStateOf { tokenizeQuery(searchQuery) } }

    fun matchesQuery(item: ServiceItem, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return true

        val name = normalize(item.service)
        val desc = normalize(item.description)
        val usernames = item.credentials.orEmpty().map { normalize(it.username) }

        return tokens.all { token ->
            name.contains(token) ||
            desc.contains(token) ||
            usernames.any { it.contains(token) }
        }
    }

    // Debounce heavy filtering to keep typing snappy
    LaunchedEffect(searchInput) {
        // Small debounce keeps UI responsive on fast typing
        delay(150)
        searchQuery = searchInput
    }

    val visibleIndices = remember(items, queryTokens) {
        items.mapIndexedNotNull { index, si -> if (matchesQuery(si, queryTokens)) index else null }
    }

    val visibleItems = remember(items, visibleIndices) {
        visibleIndices.map { idx -> items[idx] }
    }

    // highlightMatches moved to top-level

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    when (currentScreen) {
                        Screen.MAIN -> {
                            val focusManager = LocalFocusManager.current
                            TextField(
                                value = searchInput,
                                onValueChange = { searchInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Поиск по сервисам и логинам") },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchInput.isNotBlank()) {
                                        IconButton(onClick = { searchInput = ""; focusManager.clearFocus() }) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Очистить поиск")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(24.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                            )
                        }
                        Screen.SETTINGS -> {
                            Text("Настройки")
                        }
                    }
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
        },
        floatingActionButton = {
            if (currentScreen == Screen.MAIN) {
                FloatingActionButton(onClick = {
                    serviceDialog = ServiceDialogState(
                        mode = EditMode.Add,
                        index = null,
                        initialService = "",
                        initialDescription = ""
                    )
                }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Добавить сервис")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when (currentScreen) {
            Screen.MAIN -> {
                val expandedFiltered = remember(expandedItems, visibleIndices, queryTokens) {
                    if (queryTokens.isNotEmpty()) {
                        visibleItems.indices.toSet()
                    } else {
                        expandedItems.mapNotNull { original -> visibleIndices.indexOf(original).takeIf { it >= 0 } }.toSet()
                    }
                }
                ServiceList(
                    items = visibleItems,
                    expandedItems = expandedFiltered,
                    searchQuery = searchQuery,
                    onItemExpanded = { index, expanded ->
                        val originalIndex = visibleIndices[index]
                        expandedItems = if (expanded) expandedItems + originalIndex else expandedItems - originalIndex
                    },
                    onAddCredential = { filteredIndex ->
                        val serviceIndex = visibleIndices[filteredIndex]
                        val service = items[serviceIndex]
                        credentialDialog = CredentialDialogState(
                            mode = EditMode.Add,
                            serviceIndex = serviceIndex,
                            credentialIndex = null,
                            initialUsername = "",
                            initialPassword = "",
                            initialInfo = ""
                        )
                    },
                    onEditService = { filteredIndex ->
                        val serviceIndex = visibleIndices[filteredIndex]
                        val service = items[serviceIndex]
                        serviceDialog = ServiceDialogState(
                            mode = EditMode.Edit,
                            index = serviceIndex,
                            initialService = service.service ?: "",
                            initialDescription = service.description ?: ""
                        )
                    },
                    onDeleteService = { filteredIndex ->
                        val serviceIndex = visibleIndices[filteredIndex]
                        val title = items[serviceIndex].service ?: "Без названия"
                        confirmDialog = ConfirmDialogState(
                            title = "Удалить сервис",
                            message = "Удалить сервис \"$title\" со всеми учетными записями?",
                            onConfirm = {
                                val newItems = items.toMutableList().also { it.removeAt(serviceIndex) }
                                persist(newItems, "Сервис удален")
                                // пересчитаем индексы раскрытых карточек после удаления
                                expandedItems = expandedItems
                                    .filterNot { it == serviceIndex }
                                    .map { if (it > serviceIndex) it - 1 else it }
                                    .toSet()
                            }
                        )
                    },
                    onEditCredential = { filteredIndex, credentialIndex ->
                        val serviceIndex = visibleIndices[filteredIndex]
                        val creds = items[serviceIndex].credentials.orEmpty()
                        val cred = creds.getOrNull(credentialIndex)
                        if (cred == null) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Учетная запись не найдена") }
                        } else {
                            credentialDialog = CredentialDialogState(
                                mode = EditMode.Edit,
                                serviceIndex = serviceIndex,
                                credentialIndex = credentialIndex,
                                initialUsername = cred.username ?: "",
                                initialPassword = cred.password ?: "",
                                initialInfo = cred.additional_info ?: ""
                            )
                        }
                    },
                    onDeleteCredential = { filteredIndex, credentialIndex ->
                        val serviceIndex = visibleIndices[filteredIndex]
                        val title = items[serviceIndex].service ?: "Без названия"
                        confirmDialog = ConfirmDialogState(
                            title = "Удалить учетную запись",
                            message = "Удалить учетную запись из \"$title\"?",
                            onConfirm = {
                                val newCredentials = items[serviceIndex].credentials.orEmpty().toMutableList().also {
                                    if (credentialIndex in it.indices) it.removeAt(credentialIndex)
                                }
                                val newItems = items.toMutableList().also {
                                    it[serviceIndex] = it[serviceIndex].copy(credentials = newCredentials)
                                }
                                persist(newItems, "Учетная запись удалена")
                            }
                        )
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

    // Dialogs
    serviceDialog?.let { state ->
        ServiceDialog(
            title = if (state.mode == EditMode.Add) "Новый сервис" else "Редактировать сервис",
            initialService = state.initialService,
            initialDescription = state.initialDescription,
            onDismiss = { serviceDialog = null },
            onConfirm = { service, description ->
                if (service.isBlank()) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Введите название сервиса") }
                } else {
                    val newItems = items.toMutableList()
                    if (state.mode == EditMode.Add) {
                        newItems.add(ServiceItem(service = service.trim(), description = description.trim(), credentials = emptyList()))
                        persist(newItems, "Сервис добавлен")
                    } else {
                        val idx = state.index!!
                        val old = newItems[idx]
                        newItems[idx] = old.copy(service = service.trim(), description = description.trim())
                        persist(newItems, "Сервис обновлен")
                    }
                    serviceDialog = null
                }
            }
        )
    }

    credentialDialog?.let { state ->
        CredentialDialog(
            title = if (state.mode == EditMode.Add) "Новая учетная запись" else "Редактировать учетную запись",
            initialUsername = state.initialUsername,
            initialPassword = state.initialPassword,
            initialInfo = state.initialInfo,
            onDismiss = { credentialDialog = null },
            onConfirm = { username, password, info ->
                if (username.isBlank() || password.isBlank()) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Логин и пароль обязательны") }
                } else {
                    val sIdx = state.serviceIndex
                    val newItems = items.toMutableList()
                    val oldService = newItems[sIdx]
                    val newCreds = (oldService.credentials ?: emptyList()).toMutableList()
                    if (state.mode == EditMode.Add) {
                        newCreds.add(Credential(username.trim(), password, info.ifBlank { null }))
                    } else {
                        val cIdx = state.credentialIndex!!
                        newCreds[cIdx] = Credential(username.trim(), password, info.ifBlank { null })
                    }
                    newItems[sIdx] = oldService.copy(credentials = newCreds)
                    persist(newItems, if (state.mode == EditMode.Add) "Учетная запись добавлена" else "Учетная запись обновлена")
                    credentialDialog = null
                }
            }
        )
    }

    confirmDialog?.let { state ->
        ConfirmDialog(
            title = state.title,
            message = state.message,
            onDismiss = { confirmDialog = null },
            onConfirm = {
                state.onConfirm()
                confirmDialog = null
            }
        )
    }
}

@Composable
fun ServiceList(
    items: List<ServiceItem>,
    expandedItems: Set<Int>,
    searchQuery: String,
    onItemExpanded: (Int, Boolean) -> Unit,
    onAddCredential: (serviceIndex: Int) -> Unit,
    onEditService: (serviceIndex: Int) -> Unit,
    onDeleteService: (serviceIndex: Int) -> Unit,
    onEditCredential: (serviceIndex: Int, credentialIndex: Int) -> Unit,
    onDeleteCredential: (serviceIndex: Int, credentialIndex: Int) -> Unit,
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
                searchQuery = searchQuery,
                onExpandedChange = { expanded -> onItemExpanded(index, expanded) },
                onAddCredential = { onAddCredential(index) },
                onEditService = { onEditService(index) },
                onDeleteService = { onDeleteService(index) },
                onEditCredential = { credIndex -> onEditCredential(index, credIndex) },
                onDeleteCredential = { credIndex -> onDeleteCredential(index, credIndex) }
            )
        }
    }
}

@Composable
fun ServiceCard(
    item: ServiceItem,
    expanded: Boolean,
    searchQuery: String,
    onExpandedChange: (Boolean) -> Unit,
    onAddCredential: () -> Unit,
    onEditService: () -> Unit,
    onDeleteService: () -> Unit,
    onEditCredential: (Int) -> Unit,
    onDeleteCredential: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)
            )
            .clickable { onExpandedChange(!expanded) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val title = (item.service ?: "Без названия").trim()
            val titleAnnotated = highlightMatches(title, searchQuery)
            Text(titleAnnotated, style = MaterialTheme.typography.titleMedium)
            item.description?.takeIf { it.isNotBlank() }?.let { desc ->
                val descAnn = highlightMatches(desc.trim(), searchQuery)
                Text(descAnn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                        expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                        shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy))
            ) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconActionButton(icon = Icons.Outlined.Add, contentDescription = "Добавить учетную запись", onClick = onAddCredential)
                        IconActionButton(icon = Icons.Outlined.Edit, contentDescription = "Редактировать сервис", onClick = onEditService)
                        IconActionButton(icon = Icons.Outlined.Delete, contentDescription = "Удалить сервис", onClick = onDeleteService)
                    }
                    item.credentials.orEmpty().forEachIndexed { idx, cred ->
                        CredentialRow(
                            cred = cred,
                            searchQuery = searchQuery,
                            onEdit = { onEditCredential(idx) },
                            onDelete = { onDeleteCredential(idx) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CredentialRow(cred: Credential, searchQuery: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val usernameRaw = cred.username ?: ""
                val loginAnnotated = buildAnnotatedString {
                    append("Логин: ")
                    append(highlightMatches(usernameRaw, searchQuery))
                }
                Text(loginAnnotated, style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(cred.username ?: ""))
                    Toast.makeText(context, "Логин скопирован", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Скопировать логин")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val passwordRaw = cred.password ?: ""
                val passwordText = if (showPassword) passwordRaw else "••••••"
                Text("Пароль: $passwordText", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        val icon = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility
                        val desc = if (showPassword) "Скрыть пароль" else "Показать пароль"
                        Icon(icon, contentDescription = desc)
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(passwordRaw))
                        Toast.makeText(context, "Пароль скопирован", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Скопировать пароль")
                    }
                }
            }

            cred.additional_info?.takeIf { it.isNotBlank() }?.let {
                val infoAnn = highlightMatches(it, searchQuery)
                Text(infoAnn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconActionButton(icon = Icons.Outlined.Edit, contentDescription = "Редактировать учетную запись", onClick = onEdit)
                IconActionButton(icon = Icons.Outlined.Delete, contentDescription = "Удалить учетную запись", onClick = onDelete)
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
            searchQuery = "",
            onItemExpanded = { _, _ -> },
            onAddCredential = {},
            onEditService = {},
            onDeleteService = {},
            onEditCredential = { _, _ -> },
            onDeleteCredential = { _, _ -> }
        )
    }
}

// Helper UI bits and local state models
enum class EditMode { Add, Edit }

data class ServiceDialogState(
    val mode: EditMode,
    val index: Int?,
    val initialService: String,
    val initialDescription: String
)

data class CredentialDialogState(
    val mode: EditMode,
    val serviceIndex: Int,
    val credentialIndex: Int?,
    val initialUsername: String,
    val initialPassword: String,
    val initialInfo: String
)

data class ConfirmDialogState(
    val title: String,
    val message: String,
    val onConfirm: () -> Unit
)

@Composable
private fun OutlinedActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(label)
    }
}

// Top-level helper to highlight query matches within text
@Composable
fun highlightMatches(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val src = text
    val lower = src.lowercase()
    val tokens = query.lowercase().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return AnnotatedString(text)

    val styles = mutableListOf<Pair<Int, Int>>()
    for (t in tokens) {
        var idx = lower.indexOf(t)
        while (idx >= 0) {
            styles.add(idx to (idx + t.length))
            idx = lower.indexOf(t, idx + 1)
        }
    }
    if (styles.isEmpty()) return AnnotatedString(text)

    // Merge overlapping ranges
    val merged = styles.sortedBy { it.first }.fold(mutableListOf<Pair<Int, Int>>()) { acc, r ->
        if (acc.isEmpty()) acc.add(r) else {
            val last = acc.last()
            if (r.first <= last.second) {
                acc[acc.lastIndex] = last.first to maxOf(last.second, r.second)
            } else acc.add(r)
        }
        acc
    }

    return buildAnnotatedString {
        append(src)
        merged.forEach { (s, e) ->
            addStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold), s, e)
        }
    }
}

@Composable
private fun IconActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}
