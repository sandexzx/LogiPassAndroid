package com.example.logipass.ui.screens.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.logipass.AppContainer
import com.example.logipass.model.Credential
import com.example.logipass.model.Service
import com.example.logipass.ui.components.AppScaffold
import com.example.logipass.ui.components.EmptyState
import com.example.logipass.ui.components.Glass
import com.example.logipass.ui.components.GlassIconButton
import com.example.logipass.ui.components.Icons
import com.example.logipass.ui.components.InlineIconButton
import com.example.logipass.ui.components.LargeTitle
import com.example.logipass.ui.components.LocalBarBackdrop
import com.example.logipass.ui.components.NavBar
import com.example.logipass.ui.components.SheetHost
import com.example.logipass.ui.components.TextInput
import com.example.logipass.ui.components.ToastHost
import com.example.logipass.ui.components.ToastState
import com.example.logipass.ui.components.withGutters
import com.example.logipass.ui.format.servicesCount
import com.example.logipass.ui.launchWrite
import com.example.logipass.ui.theme.Style
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter

/** Height of the search bar row; the toast floats just above it. */
private val BottomBarHeight = 66.dp

/** How long a revealed password stays visible. */
private const val RevealMillis = 20_000L

/** Main screen: searchable list of service cards, add / edit sheets, copy to clipboard. */
@Composable
fun VaultScreen(
    container: AppContainer,
    toast: ToastState,
    onOpenSettings: () -> Unit,
    onLock: () -> Unit,
) {
    val style = Style
    val vault = container.vault
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val services by vault.services.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var terms by remember { mutableStateOf(searchTerms(query)) }
    LaunchedEffect(query) {
        // Clearing is instant; typing settles first so the list doesn't jump on every key.
        if (query.isNotBlank()) delay(150)
        terms = searchTerms(query)
    }
    val matches = remember(services, terms) { filterServices(services, terms) }

    var expanded by rememberSaveable(saver = idSetSaver()) { mutableStateOf<Set<String>>(emptySet()) }
    // Cards the search opened but the user closed; forgotten when the query changes.
    var collapsed by remember(terms) { mutableStateOf(emptySet<String>()) }
    // Never saved: a revealed password must not survive the screen.
    var revealed by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(revealed) {
        if (revealed.isEmpty()) return@LaunchedEffect
        delay(RevealMillis)
        revealed = emptySet()
    }

    var sheet by remember { mutableStateOf<VaultSheet?>(null) }
    var scrollTo by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(scrollTo, matches) {
        val id = scrollTo ?: return@LaunchedEffect
        val index = matches.indexOfFirst { it.service.id == id }
        if (index >= 0) {
            listState.animateScrollToItem(index + 1) // +1: the title item.
            // Cleared after the scroll: changing a key earlier would cancel this effect.
            scrollTo = null
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { focus.clearFocus() }
    }

    fun isOpen(match: ServiceMatch): Boolean {
        val id = match.service.id
        return if (match.revealCredentials) id !in collapsed else id in expanded
    }

    fun cardActions(match: ServiceMatch): ServiceCardActions {
        val service = match.service
        return ServiceCardActions(
            onToggle = {
                val open = isOpen(match)
                if (match.revealCredentials) {
                    collapsed = if (open) collapsed + service.id else collapsed - service.id
                }
                expanded = if (open) expanded - service.id else expanded + service.id
            },
            onEditService = { sheet = VaultSheet.EditService(service.id) },
            onAddCredential = { sheet = VaultSheet.AddCredential(service.id) },
            onEditCredential = { sheet = VaultSheet.EditCredential(service.id, it.id) },
            onCopyUsername = {
                container.clipboard.copy(it.username, sensitive = false)
                toast.show("Логин скопирован", Icons.Copy)
            },
            onCopyPassword = {
                container.clipboard.copy(it.password, sensitive = true)
                toast.show("Пароль скопирован · очистится через 45 с", Icons.Copy)
            },
            onToggleReveal = {
                revealed = if (it.id in revealed) revealed - it.id else revealed + it.id
            },
        )
    }

    fun addService(name: String, description: String, draft: CredentialDraft) {
        sheet = null
        container.launchWrite(toast) {
            val id = vault.addService(name, description)
            // A query that hides the new card would make the add look lost.
            query = ""
            expanded = expanded + id
            scrollTo = id
            if (!draft.isEmpty) vault.addCredential(id, draft.username, draft.password, draft.note)
        }
    }

    fun deleteService(service: Service) {
        sheet = null
        container.launchWrite(toast) {
            val deleted = vault.deleteService(service.id) ?: return@launchWrite
            toast.undo("«${service.name}» удалён") {
                container.launchWrite(toast) { vault.restoreService(deleted) }
            }
        }
    }

    fun deleteCredential(serviceId: String, credential: Credential) {
        sheet = null
        container.launchWrite(toast) {
            val deleted = vault.deleteCredential(serviceId, credential.id) ?: return@launchWrite
            toast.undo("Аккаунт удалён") {
                container.launchWrite(toast) { vault.restoreCredential(deleted) }
            }
        }
    }

    AppScaffold(
        topBar = {
            NavBar(onBack = null) {
                GlassIconButton(Icons.Lock, onLock)
                GlassIconButton(Icons.Settings, onOpenSettings)
            }
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Glass(
                    backdrop = LocalBarBackdrop.current,
                    style = style.barGlass,
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.weight(1f).height(50.dp),
                ) {
                    TextInput(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Поиск",
                        leadingIcon = Icons.Search,
                        fill = Color.Transparent,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        imeAction = ImeAction.Search,
                        onImeAction = { focus.clearFocus() },
                        trailing = if (query.isNotEmpty()) {
                            { InlineIconButton(Icons.Close, onClick = { query = "" }, contentDescription = "Очистить") }
                        } else {
                            null
                        },
                    )
                }
                GlassIconButton(
                    Icons.Plus,
                    onClick = { sheet = VaultSheet.AddService },
                    tint = Color.White,
                    fill = style.accent,
                    size = 50.dp,
                )
            }
        },
        overlay = {
            SheetHost(sheet, onDismiss = { sheet = null }) { current ->
                key(current) {
                    VaultSheetContent(
                        sheet = current,
                        services = services,
                        onCancel = { sheet = null },
                        onAddService = ::addService,
                        onSaveService = { id, name, description ->
                            sheet = null
                            container.launchWrite(toast) { vault.updateService(id, name, description) }
                        },
                        onDeleteService = ::deleteService,
                        onSaveCredential = { serviceId, credentialId, draft ->
                            sheet = null
                            container.launchWrite(toast) {
                                if (credentialId == null) {
                                    vault.addCredential(serviceId, draft.username, draft.password, draft.note)
                                } else {
                                    vault.updateCredential(serviceId, credentialId, draft.username, draft.password, draft.note)
                                }
                            }
                        },
                        onDeleteCredential = ::deleteCredential,
                    )
                }
            }
            ToastHost(toast, bottomInset = BottomBarHeight)
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = padding.withGutters(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title") {
                val subtitle = when {
                    terms.isNotEmpty() -> "Найдено: ${matches.size}"
                    services.isNotEmpty() -> servicesCount(services.size)
                    else -> null
                }
                LargeTitle("Пароли", subtitle = subtitle, modifier = Modifier.padding(bottom = 4.dp))
            }
            if (matches.isEmpty()) {
                item(key = "empty") {
                    if (services.isEmpty()) {
                        EmptyState(
                            Icons.Key,
                            "Пока пусто",
                            "Нажмите «+», чтобы добавить первый сервис и его пароль",
                            modifier = Modifier.animateItem(),
                        )
                    } else {
                        EmptyState(
                            Icons.Search,
                            "Ничего не найдено",
                            "Поиск идёт по названиям, описаниям, логинам и заметкам",
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
            items(matches, key = { it.service.id }) { match ->
                ServiceCard(
                    service = match.service,
                    expanded = isOpen(match),
                    terms = terms,
                    revealed = revealed,
                    actions = cardActions(match),
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * Sheet body for [sheet]. Looks the service up in the live list but keeps the last match,
 * so a sheet whose service was just deleted still has something to draw while it slides out.
 */
@Composable
private fun ColumnScope.VaultSheetContent(
    sheet: VaultSheet,
    services: List<Service>,
    onCancel: () -> Unit,
    onAddService: (String, String, CredentialDraft) -> Unit,
    onSaveService: (id: String, name: String, description: String) -> Unit,
    onDeleteService: (Service) -> Unit,
    onSaveCredential: (serviceId: String, credentialId: String?, CredentialDraft) -> Unit,
    onDeleteCredential: (serviceId: String, Credential) -> Unit,
) {
    val serviceId = when (sheet) {
        VaultSheet.AddService -> null
        is VaultSheet.EditService -> sheet.serviceId
        is VaultSheet.AddCredential -> sheet.serviceId
        is VaultSheet.EditCredential -> sheet.serviceId
    }
    val last = remember { arrayOfNulls<Service>(1) }
    services.firstOrNull { it.id == serviceId }?.let { last[0] = it }
    val service = last[0]

    when (sheet) {
        VaultSheet.AddService -> AddServiceSheet(onCancel = onCancel, onAdd = onAddService)
        is VaultSheet.EditService -> if (service != null) {
            EditServiceSheet(
                service = service,
                onCancel = onCancel,
                onSave = { name, description -> onSaveService(service.id, name, description) },
                onDelete = { onDeleteService(service) },
            )
        }
        is VaultSheet.AddCredential -> if (service != null) {
            CredentialSheet(
                serviceName = service.name,
                credential = null,
                onCancel = onCancel,
                onSave = { onSaveCredential(service.id, null, it) },
                onDelete = null,
            )
        }
        is VaultSheet.EditCredential -> {
            val credential = remember { arrayOfNulls<Credential>(1) }
            service?.credentials?.firstOrNull { it.id == sheet.credentialId }?.let { credential[0] = it }
            val shown = credential[0]
            if (service != null && shown != null) {
                CredentialSheet(
                    serviceName = service.name,
                    credential = shown,
                    onCancel = onCancel,
                    onSave = { onSaveCredential(service.id, shown.id, it) },
                    onDelete = { onDeleteCredential(service.id, shown) },
                )
            }
        }
    }
}

private fun idSetSaver() = listSaver<MutableState<Set<String>>, String>(
    save = { it.value.toList() },
    restore = { mutableStateOf(it.toSet()) },
)
