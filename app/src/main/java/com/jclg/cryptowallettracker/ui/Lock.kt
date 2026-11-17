package com.jclg.cryptowallettracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jclg.cryptowallettracker.data.LockType
import kotlin.math.min

/** Grille 3 × 3 : le tracé renvoie la suite des points (0 à 8). */
@Composable
fun PatternInput(modifier: Modifier = Modifier, onComplete: (String) -> Unit) {
    var selected by remember { mutableStateOf<List<Int>>(emptyList()) }
    var pointer by remember { mutableStateOf<Offset?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                selected = emptyList()
                pointer = down.position
                dotAt(down.position, size.width.toFloat(), size.height.toFloat())?.let { selected = listOf(it) }
                drag(down.id) { change ->
                    pointer = change.position
                    val hit = dotAt(change.position, size.width.toFloat(), size.height.toFloat())
                    if (hit != null && hit !in selected) selected = selected + hit
                    change.consume()
                }
                val pattern = selected.joinToString("")
                selected = emptyList()
                pointer = null
                if (pattern.isNotEmpty()) onComplete(pattern)
            }
        },
    ) {
        val centers = dotCenters(size.width, size.height)
        val radius = min(size.width, size.height) / 20f
        if (selected.size > 1) {
            for (i in 1 until selected.size) {
                drawLine(
                    color = primary,
                    start = centers[selected[i - 1]],
                    end = centers[selected[i]],
                    strokeWidth = radius * 0.8f,
                    cap = StrokeCap.Round,
                )
            }
        }
        pointer?.let { position ->
            if (selected.isNotEmpty()) {
                drawLine(
                    color = primary.copy(alpha = 0.4f),
                    start = centers[selected.last()],
                    end = position,
                    strokeWidth = radius * 0.8f,
                    cap = StrokeCap.Round,
                )
            }
        }
        centers.forEachIndexed { index, center ->
            if (index in selected) {
                drawCircle(primary.copy(alpha = 0.25f), radius = radius * 2f, center = center)
            }
            drawCircle(if (index in selected) primary else idle, radius = radius, center = center)
        }
    }
}

private fun dotCenters(width: Float, height: Float): List<Offset> {
    val step = min(width, height) / 4f
    val left = width / 2f - step
    val top = height / 2f - step
    return List(9) { index ->
        Offset(left + (index % 3) * step, top + (index / 3) * step)
    }
}

private fun dotAt(position: Offset, width: Float, height: Float): Int? {
    val tolerance = min(width, height) / 4f * 0.6f
    return dotCenters(width, height).indexOfFirst { (position - it).getDistance() <= tolerance }
        .takeIf { it >= 0 }
}

@Composable
fun LockScreen(type: LockType, verify: (String) -> Boolean, onUnlocked: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppLogo(72.dp)
            Text("Application verrouillée", style = MaterialTheme.typography.titleLarge)
            when (type) {
                LockType.PIN -> PinUnlock(verify, onUnlocked)
                LockType.PATTERN -> PatternUnlock(verify, onUnlocked)
                LockType.NONE -> Unit
            }
        }
    }
}

@Composable
private fun PinUnlock(verify: (String) -> Boolean, onUnlocked: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun submit() {
        if (verify(code)) onUnlocked() else {
            error = true
            code = ""
        }
    }

    OutlinedTextField(
        value = code,
        onValueChange = {
            code = it.filter(Char::isDigit).take(8)
            error = false
        },
        label = { Text("Code PIN") },
        singleLine = true,
        isError = error,
        supportingText = if (error) {
            { Text("Code incorrect") }
        } else {
            null
        },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { submit() }, enabled = code.isNotEmpty()) { Text("Déverrouiller") }
}

@Composable
private fun PatternUnlock(verify: (String) -> Boolean, onUnlocked: () -> Unit) {
    var error by remember { mutableStateOf(false) }

    Text(
        if (error) "Schéma incorrect" else "Dessinez votre schéma",
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PatternInput(Modifier.fillMaxWidth().aspectRatio(1f)) { pattern ->
        error = !verify(pattern)
        if (!error) onUnlocked()
    }
}

/** Choix du code : PIN saisi deux fois, ou schéma dessiné deux fois. */
@Composable
fun LockSetupDialog(type: LockType, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == LockType.PIN) "Choisir un PIN" else "Choisir un schéma") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (type == LockType.PIN) {
                    OutlinedTextField(
                        value = first,
                        onValueChange = {
                            first = it.filter(Char::isDigit).take(8)
                            error = null
                        },
                        label = { Text("PIN (4 à 8 chiffres)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = second,
                        onValueChange = {
                            second = it.filter(Char::isDigit).take(8)
                            error = null
                        },
                        label = { Text("Confirmez le PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        if (first.isBlank()) {
                            "Dessinez le schéma (4 points minimum)."
                        } else {
                            "Confirmez le schéma."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    PatternInput(Modifier.fillMaxWidth().aspectRatio(1f)) { pattern ->
                        when {
                            pattern.length < 4 -> error = "Au moins 4 points"
                            first.isBlank() -> {
                                first = pattern
                                error = null
                            }
                            first == pattern -> onConfirm(pattern)
                            else -> {
                                first = ""
                                error = "Les schémas ne correspondent pas"
                            }
                        }
                    }
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (type == LockType.PIN) {
                TextButton(
                    onClick = {
                        when {
                            first.length !in 4..8 -> error = "Le PIN doit contenir 4 à 8 chiffres"
                            first != second -> error = "Les PIN ne correspondent pas"
                            else -> onConfirm(first)
                        }
                    },
                ) { Text("Enregistrer") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
