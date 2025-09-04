package com.example.logipass.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinAuthScreen(
    isSetup: Boolean = false,
    onPinEntered: (String) -> Unit,
    onPinConfirmed: (String) -> Unit = {},
    title: String = if (isSetup) "Установите PIN-код" else "Введите PIN-код",
    subtitle: String = if (isSetup) "Придумайте 4-значный PIN-код для защиты приложения" else "Введите ваш PIN-код для доступа к данным"
) {
    var enteredPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmingPin by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val haptic = LocalHapticFeedback.current
    
    LaunchedEffect(showError) {
        if (showError) {
            kotlinx.coroutines.delay(2000)
            showError = false
        }
    }
    
    fun onNumberClick(number: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        
        if (isSetup) {
            if (!isConfirmingPin) {
                if (enteredPin.length < 4) {
                    enteredPin += number
                    if (enteredPin.length == 4) {
                        isConfirmingPin = true
                    }
                }
            } else {
                if (confirmPin.length < 4) {
                    confirmPin += number
                    if (confirmPin.length == 4) {
                        if (enteredPin == confirmPin) {
                            onPinConfirmed(enteredPin)
                        } else {
                            errorMessage = "PIN-коды не совпадают"
                            showError = true
                            enteredPin = ""
                            confirmPin = ""
                            isConfirmingPin = false
                        }
                    }
                }
            }
        } else {
            if (enteredPin.length < 4) {
                enteredPin += number
                if (enteredPin.length == 4) {
                    onPinEntered(enteredPin)
                }
            }
        }
    }
    
    fun onBackspaceClick() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        
        if (isSetup) {
            if (!isConfirmingPin && enteredPin.isNotEmpty()) {
                enteredPin = enteredPin.dropLast(1)
            } else if (isConfirmingPin) {
                if (confirmPin.isNotEmpty()) {
                    confirmPin = confirmPin.dropLast(1)
                } else {
                    isConfirmingPin = false
                }
            }
        } else {
            if (enteredPin.isNotEmpty()) {
                enteredPin = enteredPin.dropLast(1)
            }
        }
    }
    
    fun resetPinEntry() {
        enteredPin = ""
        confirmPin = ""
        isConfirmingPin = false
        showError = false
    }
    
    LaunchedEffect(Unit) {
        resetPinEntry()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
            .padding(16.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                AnimatedContent(
                    targetState = if (isSetup && isConfirmingPin) "Подтвердите PIN-код" else subtitle,
                    transitionSpec = {
                        slideInVertically { it } + fadeIn() togetherWith
                        slideOutVertically { -it } + fadeOut()
                    }
                ) { currentSubtitle ->
                    Text(
                        text = currentSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                PinIndicators(
                    pinLength = if (isSetup && isConfirmingPin) confirmPin.length else enteredPin.length,
                    maxLength = 4,
                    isError = showError
                )

                AnimatedVisibility(
                    visible = showError,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                NumericKeypad(
                    onNumberClick = ::onNumberClick,
                    onBackspaceClick = ::onBackspaceClick
                )
            }
        }
    }
}

@Composable
private fun PinIndicators(
    pinLength: Int,
    maxLength: Int,
    isError: Boolean = false
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isError) 1.2f else 1f,
        animationSpec = tween(100)
    )
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.scale(animatedScale)
    ) {
        repeat(maxLength) { index ->
            val isFilled = index < pinLength
            val indicatorColor = when {
                isError -> MaterialTheme.colorScheme.error
                isFilled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (isFilled) indicatorColor else Color.Transparent
                    )
                    .border(
                        width = 2.dp,
                        color = indicatorColor,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun NumericKeypad(
    onNumberClick: (String) -> Unit,
    onBackspaceClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeypadButton("1") { onNumberClick("1") }
            Spacer(modifier = Modifier.width(24.dp))
            KeypadButton("2") { onNumberClick("2") }
            Spacer(modifier = Modifier.width(24.dp))
            KeypadButton("3") { onNumberClick("3") }
        }
        
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeypadButton("4") { onNumberClick("4") }
            Spacer(modifier = Modifier.width(24.dp))
            KeypadButton("5") { onNumberClick("5") }
            Spacer(modifier = Modifier.width(24.dp))
            KeypadButton("6") { onNumberClick("6") }
        }
        
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeypadButton("7") { onNumberClick("7") }
            Spacer(modifier = Modifier.width(24.dp))
            KeypadButton("8") { onNumberClick("8") }
            Spacer(modifier = Modifier.width(24.dp))
            KeypadButton("9") { onNumberClick("9") }
        }
        
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.size(72.dp))
            Spacer(modifier = Modifier.width(24.dp))
            KeypadButton("0") { onNumberClick("0") }
            Spacer(modifier = Modifier.width(24.dp))
            KeypadButton(
                content = {
                    Icon(
                        imageVector = Icons.Filled.Backspace,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            ) { onBackspaceClick() }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit
) {
    KeypadButton(
        content = {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        onClick = onClick
    )
}

@Composable
private fun KeypadButton(
    content: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        shape = CircleShape,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
