package com.jclg.cryptowallettracker.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.jclg.cryptowallettracker.BuildConfig
import com.jclg.cryptowallettracker.data.Currency
import com.jclg.cryptowallettracker.data.Wallet
import com.jclg.cryptowallettracker.data.WalletPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val qrJson = Json { encodeDefaults = false }

@Composable
fun CurrencyDialog(current: Currency, onPick: (Currency) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Devise d'affichage") },
        text = {
            Column {
                Currency.entries.forEach { currency ->
                    Surface(
                        onClick = { onPick(currency) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (currency == current) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(currency.label, style = MaterialTheme.typography.bodyLarge)
                            Text(currency.code, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

@Composable
fun WalletQrDialog(wallet: Wallet, onDismiss: () -> Unit) {
    val payload = remember(wallet) {
        runCatching { qrJson.encodeToString(WalletPayload.from(wallet)) }.getOrNull()
    }
    val bitmap = remember(payload) { payload?.let { qrBitmap(it) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(wallet.name.ifBlank { wallet.coin?.name ?: "Portefeuille" }) },
        text = {
            if (bitmap != null) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "QR code du portefeuille",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                    Text(
                        "Scannez ce code depuis l'application pour importer le portefeuille.",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text("Contenu trop long pour tenir dans un QR code. Raccourcissez le commentaire.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}

/** Code optionnel avant l'export : vide = fichier JSON lisible, sinon fichier chiffré. */
@Composable
fun ExportCodeDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Protéger l'export") },
        text = {
            Column {
                Text(
                    "Laissez vide pour un fichier lisible. Avec un code, le fichier est chiffré " +
                        "et le même code sera demandé à l'import.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Code (optionnel)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(code) }) { Text("Exporter") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Demande le code d'un fichier d'import chiffré ; reste ouvert si le code est faux. */
@Composable
fun ImportCodeDialog(error: String?, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fichier protégé") },
        text = {
            Column {
                Text("Ce fichier a été exporté avec un code.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Code") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(code) }) { Text("Importer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun ChangelogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val changelog = remember {
        runCatching {
            context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Version ${BuildConfig.VERSION_NAME}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    changelog.replace(Regex("(?m)^#{1,6} "), ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}

private fun qrBitmap(text: String, size: Int = 640): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix[x, y]) black else white
            }
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    }.getOrNull()
