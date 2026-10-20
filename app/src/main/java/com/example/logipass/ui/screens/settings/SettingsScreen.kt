package com.example.logipass.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.logipass.AppContainer
import com.example.logipass.BuildConfig
import com.example.logipass.data.DecryptResult
import com.example.logipass.data.ImportProbe
import com.example.logipass.model.Vault
import com.example.logipass.security.BiometricOutcome
import com.example.logipass.session.AuthManager
import com.example.logipass.session.BiometricAvailability
import com.example.logipass.session.BiometricCipher
import com.example.logipass.session.ChangePasswordResult
import com.example.logipass.ui.biometricErrorText
import com.example.logipass.ui.components.ActionSheetContent
import com.example.logipass.ui.components.AppIcon
import com.example.logipass.ui.components.AppScaffold
import com.example.logipass.ui.components.CardRow
import com.example.logipass.ui.components.GroupFooter
import com.example.logipass.ui.components.GroupLabel
import com.example.logipass.ui.components.IconBadge
import com.example.logipass.ui.components.Icons
import com.example.logipass.ui.components.LargeTitle
import com.example.logipass.ui.components.NavBar
import com.example.logipass.ui.components.PasswordInput
import com.example.logipass.ui.components.SecondaryButton
import com.example.logipass.ui.components.Separator
import com.example.logipass.ui.components.SheetAction
import com.example.logipass.ui.components.SheetHeader
import com.example.logipass.ui.components.SheetHost
import com.example.logipass.ui.components.SurfaceCard
import com.example.logipass.ui.components.ToastHost
import com.example.logipass.ui.components.ToastState
import com.example.logipass.ui.components.Toggle
import com.example.logipass.ui.components.shake
import com.example.logipass.ui.enrollBiometric
import com.example.logipass.ui.format.formatCountdown
import com.example.logipass.ui.format.servicesCount
import com.example.logipass.ui.launchWrite
import com.example.logipass.ui.rememberFragmentActivity
import com.example.logipass.ui.showEnrollOutcome
import com.example.logipass.ui.theme.AppType
import com.example.logipass.ui.theme.Style
import javax.crypto.Cipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface SettingsSheet {
    data object ChangePassword : SettingsSheet
    data object ExportPassword : SettingsSheet
    data class ImportPassword(val probe: ImportProbe.Encrypted) : SettingsSheet
    data class ConfirmImport(val vault: Vault, val plaintext: Boolean) : SettingsSheet
    data object ConfirmWipe : SettingsSheet
}

/** Security, backup and the wipe, grouped like iOS Settings. */
@Composable
fun SettingsScreen(
    container: AppContainer,
    toast: ToastState,
    onBack: () -> Unit,
    onLock: () -> Unit,
) {
    val style = Style
    val auth = container.auth
    val activity = rememberFragmentActivity()
    val biometricEnabled by auth.biometricEnabled.collectAsStateWithLifecycle()
    val availability = remember { auth.biometricAvailability() }
    val services by container.vault.services.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    var biometricBusy by remember { mutableStateOf(false) }

    // Held only in memory between the password sheet and the file picker. Lost if the
    // activity is recreated meanwhile; the user is then asked to start over.
    var exportPassword by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        auth.resumeAutoLock()
        val password = exportPassword
        exportPassword = null
        when {
            uri == null -> Unit
            password == null -> toast.error("Экспорт прерван. Попробуйте ещё раз")
            else -> container.scope.launch {
                toast.show("Шифруем копию…", Icons.Lock)
                if (container.backup.export(uri, password)) {
                    toast.show("Резервная копия сохранена", Icons.Check)
                } else {
                    toast.error("Не удалось сохранить файл")
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        auth.resumeAutoLock()
        if (uri != null) {
            container.scope.launch {
                when (val probe = container.backup.probe(uri)) {
                    is ImportProbe.Legacy -> sheet = SettingsSheet.ConfirmImport(probe.vault, plaintext = true)
                    is ImportProbe.Encrypted -> sheet = SettingsSheet.ImportPassword(probe)
                    ImportProbe.Invalid -> toast.error("Это не резервная копия LogiPass")
                }
            }
        }
    }

    fun setBiometric(enable: Boolean) {
        if (biometricBusy) return
        biometricBusy = true
        container.scope.launch {
            try {
                if (enable) {
                    toast.showEnrollOutcome(container.enrollBiometric(activity))
                } else {
                    auth.disableBiometric()
                    toast.show("Вход по отпечатку выключен", Icons.Fingerprint)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                toast.error("Не удалось сохранить изменения")
            } finally {
                biometricBusy = false
            }
        }
    }

    AppScaffold(
        topBar = { NavBar(onBack = onBack) },
        overlay = {
            SheetHost(sheet, onDismiss = { sheet = null }) { current ->
                key(current) {
                    when (current) {
                        SettingsSheet.ChangePassword -> ChangePasswordSheet(
                            container = container,
                            activity = activity,
                            biometricEnabled = biometricEnabled,
                            onCancel = { sheet = null },
                            onChanged = {
                                sheet = null
                                toast.show("Мастер-пароль изменён", Icons.Check)
                            },
                        )
                        SettingsSheet.ExportPassword -> ExportPasswordSheet(
                            onCancel = { sheet = null },
                            onContinue = { password ->
                                sheet = null
                                exportPassword = password
                                auth.suspendAutoLock()
                                exportLauncher.launch(container.backup.suggestedFileName())
                            },
                        )
                        is SettingsSheet.ImportPassword -> ImportPasswordSheet(
                            container = container,
                            probe = current.probe,
                            onCancel = { sheet = null },
                            onDecrypted = { sheet = SettingsSheet.ConfirmImport(it, plaintext = false) },
                            onInvalid = {
                                sheet = null
                                toast.error("Файл повреждён")
                            },
                        )
                        is SettingsSheet.ConfirmImport -> {
                            val incoming = current.vault.services.size
                            val message = buildString {
                                append("Сейчас: ${servicesCount(services.size)}. В файле: ${servicesCount(incoming)}. ")
                                append("Текущие данные будут заменены.")
                                if (current.plaintext) {
                                    append("\n\nФайл не зашифрован: после импорта удалите его с телефона.")
                                }
                            }
                            ActionSheetContent(
                                title = "Заменить данные?",
                                message = message,
                                onDismiss = { sheet = null },
                                actions = listOf(
                                    SheetAction("Заменить", Icons.Import, destructive = true) {
                                        container.launchWrite(toast) {
                                            container.vault.replaceAll(current.vault)
                                            toast.show("Импортировано: ${servicesCount(incoming)}", Icons.Check)
                                        }
                                    },
                                ),
                            )
                        }
                        SettingsSheet.ConfirmWipe -> ActionSheetContent(
                            title = "Стереть все данные?",
                            message = "Пароли, мастер-пароль и отпечаток будут удалены без возможности восстановления. " +
                                "Если данные ещё нужны, сначала сделайте экспорт.",
                            onDismiss = { sheet = null },
                            actions = listOf(
                                SheetAction("Стереть всё", Icons.Trash, destructive = true) {
                                    container.scope.launch {
                                        try {
                                            auth.wipeAll()
                                            toast.show("Хранилище стёрто", Icons.Trash)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            toast.error("Не удалось стереть данные")
                                        }
                                    }
                                },
                            ),
                        )
                    }
                }
            }
            ToastHost(toast)
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            LargeTitle("Настройки")

            GroupLabel("Безопасность")
            SettingsCard {
                val canToggle = availability == BiometricAvailability.Available || biometricEnabled
                CardRow(
                    title = "Вход по отпечатку",
                    subtitle = when {
                        biometricEnabled || availability == BiometricAvailability.Available -> null
                        availability == BiometricAvailability.NoneEnrolled -> "Добавьте отпечаток в настройках телефона"
                        else -> "Недоступно на этом устройстве"
                    },
                    leading = { IconBadge(Icons.Fingerprint, style.accent) },
                    trailing = {
                        Toggle(
                            checked = biometricEnabled,
                            onCheckedChange = ::setBiometric,
                            enabled = canToggle && !biometricBusy,
                        )
                    },
                    enabled = canToggle,
                    onClick = { setBiometric(!biometricEnabled) },
                )
                RowSeparator()
                CardRow(
                    title = "Сменить мастер-пароль",
                    leading = { IconBadge(Icons.Key, style.warning) },
                    showChevron = true,
                    onClick = { sheet = SettingsSheet.ChangePassword },
                )
                RowSeparator()
                CardRow(
                    title = "Заблокировать сейчас",
                    leading = { IconBadge(Icons.Lock, Color(0xFF8E8E93)) },
                    onClick = onLock,
                )
            }
            GroupFooter("Хранилище блокируется само через минуту после сворачивания приложения.")

            GroupLabel("Резервная копия")
            SettingsCard {
                CardRow(
                    title = "Экспорт",
                    subtitle = "Зашифрованный файл под отдельным паролем",
                    leading = { IconBadge(Icons.Export, style.positive) },
                    showChevron = true,
                    onClick = { sheet = SettingsSheet.ExportPassword },
                )
                RowSeparator()
                CardRow(
                    title = "Импорт",
                    subtitle = "Копия LogiPass или JSON из старой версии",
                    leading = { IconBadge(Icons.Import, style.accent) },
                    showChevron = true,
                    onClick = {
                        auth.suspendAutoLock()
                        importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                    },
                )
            }
            GroupFooter("Перед заменой данных приложение покажет, что именно будет импортировано.")

            GroupLabel("Опасная зона")
            SettingsCard {
                CardRow(
                    title = "Стереть все данные",
                    titleColor = style.destructive,
                    leading = { IconBadge(Icons.Trash, style.destructive) },
                    onClick = { sheet = SettingsSheet.ConfirmWipe },
                )
            }

            Text(
                "LogiPass ${BuildConfig.VERSION_NAME}",
                style = AppType.footnote,
                color = style.textGhost,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    SurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp), content = content)
}

/** Inset to start under the row titles, past the icon badge. */
@Composable
private fun RowSeparator() {
    Separator(Modifier.padding(start = 42.dp))
}

/**
 * Current password (or a fresh fingerprint), then the new one twice. The KDF runs in the
 * app scope so rotating mid-change can't leave the key file half-done.
 */
@Composable
private fun ColumnScope.ChangePasswordSheet(
    container: AppContainer,
    activity: FragmentActivity,
    biometricEnabled: Boolean,
    onCancel: () -> Unit,
    onChanged: () -> Unit,
) {
    val style = Style
    val auth = container.auth
    var current by remember { mutableStateOf("") }
    var verified by remember { mutableStateOf<Cipher?>(null) }
    var newPassword by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lockedUntil by remember { mutableStateOf<Long?>(null) }
    var shakes by remember { mutableIntStateOf(0) }
    val newFocus = remember { FocusRequester() }
    val repeatFocus = remember { FocusRequester() }
    val now by rememberTicker(lockedUntil)
    val lockedOut = lockedUntil?.let { it > now } == true

    val newOk = newPassword.length >= AuthManager.MIN_PASSWORD_LENGTH
    val valid = (verified != null || current.isNotEmpty()) && newOk && repeat == newPassword && !lockedOut && !busy

    fun confirmWithFingerprint() {
        container.scope.launch {
            when (val prepared = auth.biometricUnlockCipher()) {
                is BiometricCipher.Ready -> {
                    val outcome = container.biometric.authenticate(
                        activity, prepared.cipher, "Подтвердите личность", "Чтобы сменить мастер-пароль", "Отмена",
                    )
                    when (outcome) {
                        is BiometricOutcome.Success -> {
                            verified = outcome.cipher
                            error = null
                            newFocus.tryFocus()
                        }
                        BiometricOutcome.Cancelled -> Unit
                        is BiometricOutcome.Error -> error = biometricErrorText(outcome.code)
                    }
                }
                BiometricCipher.Invalidated -> error = "Отпечатки на телефоне изменились. Введите текущий пароль"
                BiometricCipher.NotEnabled -> error = "Вход по отпечатку выключен. Введите текущий пароль"
            }
        }
    }

    fun submit() {
        if (!valid) return
        busy = true
        error = null
        val cipher = verified
        val currentPassword = current
        val chosen = newPassword
        container.scope.launch {
            try {
                val result = if (cipher != null) {
                    auth.changePasswordWithBiometric(cipher, chosen)
                } else {
                    auth.changePassword(currentPassword, chosen)
                }
                when (result) {
                    ChangePasswordResult.Success -> onChanged()
                    ChangePasswordResult.TooShort -> error = "Не короче ${AuthManager.MIN_PASSWORD_LENGTH} символов"
                    is ChangePasswordResult.WrongCurrent -> {
                        current = ""
                        shakes++
                        lockedUntil = result.lockedOutUntil
                        error = "Неверный текущий пароль"
                    }
                    is ChangePasswordResult.LockedOut -> lockedUntil = result.until
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IllegalStateException) {
                // The fingerprint confirmation was used up or went stale.
                verified = null
                error = "Подтвердите отпечаток ещё раз"
            } catch (_: Exception) {
                error = "Не удалось сменить пароль"
            } finally {
                busy = false
            }
        }
    }

    SheetHeader(
        "Мастер-пароль",
        onCancel = onCancel,
        confirm = if (busy) "Шифруем…" else "Сменить",
        onConfirm = ::submit,
        confirmEnabled = valid,
    )
    Column(Modifier.shake(shakes), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedContent(
            targetState = verified != null,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "currentPassword",
        ) { isVerified ->
            if (isVerified) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(style.positive.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppIcon(Icons.Fingerprint, style.positive, size = 20.dp)
                    Text("Подтверждено отпечатком", style = AppType.body, color = style.textPrimary)
                }
            } else {
                PasswordInput(
                    current, { current = it }, "Текущий пароль",
                    fill = style.insetFill,
                    enabled = !busy && !lockedOut,
                    onImeAction = { newFocus.tryFocus() },
                )
            }
        }
        if (biometricEnabled && verified == null) {
            SecondaryButton(
                text = "Подтвердить отпечатком",
                icon = Icons.Fingerprint,
                onClick = ::confirmWithFingerprint,
                fill = style.insetFill,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        PasswordInput(
            newPassword, { newPassword = it }, "Новый пароль",
            fill = style.insetFill,
            enabled = !busy,
            focusRequester = newFocus,
            onImeAction = { repeatFocus.tryFocus() },
        )
        PasswordInput(
            repeat, { repeat = it }, "Повторите новый пароль",
            fill = style.insetFill,
            enabled = !busy,
            revealable = false,
            focusRequester = repeatFocus,
            imeAction = ImeAction.Done,
            onImeAction = ::submit,
        )
    }
    val currentError = error
    val hint = when {
        lockedOut -> "Слишком много попыток. Повторите через ${formatCountdown(lockedUntil!! - now)}"
        currentError != null -> currentError
        newPassword.isNotEmpty() && !newOk -> "Не короче ${AuthManager.MIN_PASSWORD_LENGTH} символов"
        repeat.isNotEmpty() && repeat != newPassword -> "Пароли не совпадают"
        else -> "Данные перешифровываются сразу. Старые экспортированные копии остаются под своими паролями."
    }
    SheetHint(hint, isError = lockedOut || error != null)
}

/** Backups get their own password: a file that leaves the phone shouldn't share the master one. */
@Composable
private fun ColumnScope.ExportPasswordSheet(onCancel: () -> Unit, onContinue: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val repeatFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(280)
        focus.tryFocus()
    }
    val longEnough = password.length >= AuthManager.MIN_PASSWORD_LENGTH
    val valid = longEnough && repeat == password
    fun submit() {
        if (valid) onContinue(password)
    }

    SheetHeader("Экспорт", onCancel = onCancel, confirm = "Далее", onConfirm = ::submit, confirmEnabled = valid)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PasswordInput(
            password, { password = it }, "Пароль для файла",
            fill = Style.insetFill,
            focusRequester = focus,
            onImeAction = { repeatFocus.tryFocus() },
        )
        PasswordInput(
            repeat, { repeat = it }, "Повторите пароль",
            fill = Style.insetFill,
            revealable = false,
            focusRequester = repeatFocus,
            imeAction = ImeAction.Done,
            onImeAction = ::submit,
        )
    }
    val hint = when {
        password.isNotEmpty() && !longEnough -> "Не короче ${AuthManager.MIN_PASSWORD_LENGTH} символов"
        repeat.isNotEmpty() && repeat != password -> "Пароли не совпадают"
        else -> "Им шифруется файл. Без этого пароля копию не открыть, даже зная мастер-пароль."
    }
    SheetHint(hint, isError = false)
}

@Composable
private fun ColumnScope.ImportPasswordSheet(
    container: AppContainer,
    probe: ImportProbe.Encrypted,
    onCancel: () -> Unit,
    onDecrypted: (Vault) -> Unit,
    onInvalid: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    var shakes by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(280)
        focus.tryFocus()
    }
    fun submit() {
        if (password.isEmpty() || busy) return
        busy = true
        val attempt = password
        container.scope.launch {
            val result = try {
                container.backup.decrypt(probe, attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                DecryptResult.Invalid
            }
            busy = false
            when (result) {
                is DecryptResult.Success -> onDecrypted(result.vault)
                DecryptResult.WrongPassword -> {
                    password = ""
                    wrong = true
                    shakes++
                }
                DecryptResult.Invalid -> onInvalid()
            }
        }
    }

    SheetHeader(
        "Импорт",
        onCancel = onCancel,
        confirm = if (busy) "Проверяем…" else "Открыть",
        onConfirm = ::submit,
        confirmEnabled = password.isNotEmpty() && !busy,
    )
    PasswordInput(
        password, { password = it; wrong = false }, "Пароль от файла",
        modifier = Modifier.shake(shakes),
        fill = Style.insetFill,
        enabled = !busy,
        focusRequester = focus,
        imeAction = ImeAction.Done,
        onImeAction = ::submit,
    )
    SheetHint(
        if (wrong) "Неверный пароль" else "Пароль, который вы задали при экспорте",
        isError = wrong,
    )
}

@Composable
private fun SheetHint(text: String, isError: Boolean) {
    val style = Style
    AnimatedContent(
        targetState = text to isError,
        contentKey = { it.first.substringBefore("через") },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "sheetHint",
    ) { (shown, error) ->
        Text(
            shown,
            style = AppType.footnote,
            color = if (error) style.destructive else style.textSecondary,
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 10.dp),
        )
    }
}

/** Wall-clock time, refreshed while a lockout is running. */
@Composable
private fun rememberTicker(until: Long?): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until) {
        now.longValue = System.currentTimeMillis()
        while (until != null && now.longValue < until) {
            delay(250)
            now.longValue = System.currentTimeMillis()
        }
    }
    return now
}

private fun FocusRequester.tryFocus() {
    try {
        requestFocus()
    } catch (_: IllegalStateException) {
        // Not attached (sheet already gone).
    }
}
