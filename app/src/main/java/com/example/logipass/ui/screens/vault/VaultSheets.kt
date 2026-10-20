package com.example.logipass.ui.screens.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.logipass.model.Credential
import com.example.logipass.model.Service
import com.example.logipass.ui.components.AppIcon
import com.example.logipass.ui.components.Icons
import com.example.logipass.ui.components.PasswordInput
import com.example.logipass.ui.components.SheetHeader
import com.example.logipass.ui.components.TextInput
import com.example.logipass.ui.components.tappable
import com.example.logipass.ui.format.accountsCount
import com.example.logipass.ui.theme.AppType
import com.example.logipass.ui.theme.Style
import kotlinx.coroutines.delay

/** What the vault's bottom sheet is showing. */
sealed interface VaultSheet {
    data object AddService : VaultSheet
    data class EditService(val serviceId: String) : VaultSheet
    data class AddCredential(val serviceId: String) : VaultSheet
    data class EditCredential(val serviceId: String, val credentialId: String) : VaultSheet
}

/** Account fields entered in a sheet. Blank when the user skipped them. */
data class CredentialDraft(val username: String, val password: String, val note: String) {
    val isEmpty: Boolean get() = username.isBlank() && password.isEmpty() && note.isBlank()
}

/** New service, with an optional first account so the common case is one sheet. */
@Composable
fun ColumnScope.AddServiceSheet(onCancel: () -> Unit, onAdd: (name: String, description: String, CredentialDraft) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val nameFocus = remember { FocusRequester() }
    AutoFocus(nameFocus)

    val valid = name.isNotBlank()
    fun submit() {
        if (valid) onAdd(name.trim(), description.trim(), CredentialDraft(username.trim(), password, note.trim()))
    }

    SheetHeader("Новый сервис", onCancel = onCancel, confirm = "Добавить", onConfirm = ::submit, confirmEnabled = valid)
    FieldGroup {
        TextInput(
            name, { name = it }, "Название, например «Почта»",
            capitalization = KeyboardCapitalization.Sentences,
            focusRequester = nameFocus,
            fill = Style.insetFill,
        )
        TextInput(description, { description = it }, "Описание (необязательно)", fill = Style.insetFill)
    }
    SheetCaption("Аккаунт · можно добавить позже")
    FieldGroup {
        UsernameInput(username) { username = it }
        PasswordInput(password, { password = it }, "Пароль", fill = Style.insetFill)
        TextInput(
            note, { note = it }, "Заметка",
            fill = Style.insetFill,
            imeAction = ImeAction.Done,
            onImeAction = ::submit,
        )
    }
}

@Composable
fun ColumnScope.EditServiceSheet(
    service: Service,
    onCancel: () -> Unit,
    onSave: (name: String, description: String) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(service.name) }
    var description by remember { mutableStateOf(service.description) }
    val valid = name.isNotBlank()
    val changed = name.trim() != service.name || description.trim() != service.description
    fun submit() {
        if (!valid) return
        if (changed) onSave(name.trim(), description.trim()) else onCancel()
    }

    SheetHeader("Сервис", onCancel = onCancel, confirm = "Готово", onConfirm = ::submit, confirmEnabled = valid)
    FieldGroup {
        TextInput(name, { name = it }, "Название", fill = Style.insetFill)
        TextInput(
            description, { description = it }, "Описание (необязательно)",
            fill = Style.insetFill,
            imeAction = ImeAction.Done,
            onImeAction = ::submit,
        )
    }
    val count = service.credentials.size
    DestructiveButton(
        text = if (count == 0) "Удалить сервис" else "Удалить сервис и ${accountsCount(count)}",
        onClick = onDelete,
    )
}

/** Add ([credential] = null) or edit one account of [serviceName]. */
@Composable
fun ColumnScope.CredentialSheet(
    serviceName: String,
    credential: Credential?,
    onCancel: () -> Unit,
    onSave: (CredentialDraft) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var username by remember { mutableStateOf(credential?.username ?: "") }
    var password by remember { mutableStateOf(credential?.password ?: "") }
    var note by remember { mutableStateOf(credential?.note ?: "") }
    val usernameFocus = remember { FocusRequester() }
    if (credential == null) AutoFocus(usernameFocus)

    val draft = CredentialDraft(username.trim(), password, note.trim())
    val valid = draft.username.isNotEmpty() || draft.password.isNotEmpty()
    fun submit() {
        if (!valid) return
        val unchanged = credential != null && draft.username == credential.username &&
            draft.password == credential.password && draft.note == credential.note
        if (unchanged) onCancel() else onSave(draft)
    }

    SheetHeader(
        serviceName,
        onCancel = onCancel,
        confirm = if (credential == null) "Добавить" else "Готово",
        onConfirm = ::submit,
        confirmEnabled = valid,
    )
    FieldGroup {
        UsernameInput(username, focusRequester = usernameFocus) { username = it }
        PasswordInput(password, { password = it }, "Пароль", fill = Style.insetFill)
        TextInput(
            note, { note = it }, "Заметка",
            fill = Style.insetFill,
            singleLine = false,
            imeAction = ImeAction.Default,
            modifier = Modifier.heightIn(min = 50.dp),
        )
    }
    if (onDelete != null) DestructiveButton("Удалить аккаунт", onDelete)
}

@Composable
private fun UsernameInput(value: String, focusRequester: FocusRequester? = null, onValueChange: (String) -> Unit) {
    TextInput(
        value, onValueChange, "Логин или почта",
        keyboardType = KeyboardType.Email,
        capitalization = KeyboardCapitalization.None,
        autoCorrect = false,
        fill = Style.insetFill,
        focusRequester = focusRequester,
    )
}

@Composable
private fun FieldGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun SheetCaption(text: String) {
    Text(
        text.uppercase(),
        style = AppType.footnote.copy(fontWeight = FontWeight.Medium),
        color = Style.textSecondary,
        modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun DestructiveButton(text: String, onClick: () -> Unit) {
    val style = Style
    Row(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(style.destructive.copy(alpha = 0.1f))
            .tappable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(Icons.Trash, style.destructive, size = 18.dp)
        Text(text, style = AppType.body.copy(fontWeight = FontWeight.Medium), color = style.destructive)
    }
}

/** Raises the keyboard on the first field once the sheet has slid in. */
@Composable
private fun AutoFocus(focusRequester: FocusRequester) {
    LaunchedEffect(focusRequester) {
        delay(280)
        try {
            focusRequester.requestFocus()
        } catch (_: IllegalStateException) {
            // Sheet dismissed before it settled.
        }
    }
}
