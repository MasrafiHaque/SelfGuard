package com.selfguard.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfguard.data.local.prefs.ProtectionSnapshot
import com.selfguard.ui.MainViewModel

const val DEVICE_OWNER_COMMAND =
    "adb shell dpm set-device-owner com.selfguard/com.selfguard.service.deviceadmin.SelfGuardDeviceAdminReceiver"

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val serviceEnabled by viewModel.serviceEnabled.collectAsStateWithLifecycle()
    val protection by viewModel.protection.collectAsStateWithLifecycle()
    val deviceAdminActive by viewModel.deviceAdminActive.collectAsStateWithLifecycle()
    val deviceOwner by viewModel.deviceOwner.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showReleaseDialog by remember { mutableStateOf(false) }
    var showPassphraseDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshAdminStatus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Blocking",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                ToggleRow(
                    title = "Block apps, websites and keywords",
                    subtitle = "Master switch for everything SelfGuard blocks",
                    checked = settings?.blockingEnabled ?: true,
                    onCheckedChange = viewModel::setBlockingEnabled,
                )
                ToggleRow(
                    title = "Match keywords in page text",
                    subtitle = "Also checks the words shown on the page, not only the address bar",
                    checked = settings?.matchKeywordsInPageText ?: true,
                    onCheckedChange = viewModel::setMatchKeywordsInPageText,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Service",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (serviceEnabled) {
                        "The accessibility service is on. Protection is active."
                    } else {
                        "The accessibility service is off. Nothing is blocked."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!serviceEnabled) {
                    Button(
                        onClick = { MainViewModel.openAccessibilitySettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Open accessibility settings")
                    }
                }
            }
        }

        ProtectionCard(
            snapshot = protection,
            deviceAdminActive = deviceAdminActive,
            deviceOwner = deviceOwner,
            onArmChange = { checked ->
                if (checked) viewModel.armProtection() else showReleaseDialog = true
            },
            onRelease = { viewModel.beginRelease() },
            onReleaseWithPassphrase = { phrase ->
                viewModel.releaseWithPassphrase(phrase)
                showReleaseDialog = false
            },
            onSetPassphrase = { phrase ->
                viewModel.setPassphrase(phrase)
                showPassphraseDialog = false
            },
            onShowPassphraseDialog = { showPassphraseDialog = true },
            showReleaseDialog = showReleaseDialog,
            onDismissReleaseDialog = { showReleaseDialog = false },
            showPassphraseDialog = showPassphraseDialog,
            onDismissPassphraseDialog = { showPassphraseDialog = false },
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Blocklist",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Restoring resets websites and keywords to the list that ships with " +
                        "the app and removes everything you added. Apps you blocked stay on the list.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { viewModel.restoreDefaults() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Restore websites and keywords")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        text = "Works offline",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "SelfGuard has no internet permission. Your lists and activity " +
                            "never leave this phone.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProtectionCard(
    snapshot: ProtectionSnapshot?,
    deviceAdminActive: Boolean,
    deviceOwner: Boolean,
    onArmChange: (Boolean) -> Unit,
    onRelease: () -> Unit,
    onReleaseWithPassphrase: (String) -> Unit,
    onSetPassphrase: (String) -> Unit,
    onShowPassphraseDialog: () -> Unit,
    showReleaseDialog: Boolean,
    onDismissReleaseDialog: () -> Unit,
    showPassphraseDialog: Boolean,
    onDismissPassphraseDialog: () -> Unit,
) {
    val context = LocalContext.current
    val armed = snapshot?.armed == true
    val now = System.currentTimeMillis()
    val cooldownActive = snapshot != null && snapshot.cooldownUntil > now
    val cooldownOver = snapshot != null && snapshot.cooldownUntil in 1 until now

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Protection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            ToggleRow(
                title = "Settings shield armed",
                subtitle = if (armed) {
                    "Accessibility, device admin and SelfGuard's own app info are blocked"
                } else {
                    "Blocks the settings screens that could disable SelfGuard"
                },
                checked = armed,
                onCheckedChange = onArmChange,
            )

            if (cooldownActive) {
                Text(
                    text = "Release requested — available in " +
                        MainViewModel.formatRemaining(snapshot.cooldownUntil - now),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Device admin: " +
                            if (deviceAdminActive) "Active" else "Inactive",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Device Owner: " + if (deviceOwner) "Yes" else "No",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!deviceAdminActive) {
                    OutlinedButton(
                        onClick = { MainViewModel.openDeviceAdminSettings(context) },
                    ) {
                        Text("Enable")
                    }
                }
            }

            if (!deviceOwner) {
                Text(
                    text = "Uninstall blocking requires the Device Owner role. With no Google " +
                        "account on the phone it can be granted over USB with:",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = DEVICE_OWNER_COMMAND,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                OutlinedButton(
                    onClick = { copyToClipboard(context, DEVICE_OWNER_COMMAND) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Copy the command")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Partner passphrase",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = if (snapshot?.hasPassphrase == true) {
                            "Set — can release immediately"
                        } else {
                            "Not set — release waits out the cooldown"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = onShowPassphraseDialog) {
                    Text(if (snapshot?.hasPassphrase == true) "Change" else "Set")
                }
            }
        }
    }

    if (showReleaseDialog) {
        ReleaseDialog(
            snapshot = snapshot,
            onReleaseWithPassphrase = onReleaseWithPassphrase,
            onRelease = onRelease,
            onDismiss = onDismissReleaseDialog,
        )
    }
    if (showPassphraseDialog) {
        PassphraseDialog(
            onChange = onSetPassphrase,
            onDismiss = onDismissPassphraseDialog,
        )
    }
}

@Composable
private fun ReleaseDialog(
    snapshot: ProtectionSnapshot?,
    onReleaseWithPassphrase: (String) -> Unit,
    onRelease: () -> Unit,
    onDismiss: () -> Unit,
) {
    var passphraseInput by remember { mutableStateOf("") }
    val hasPassphrase = snapshot?.hasPassphrase == true
    val now = System.currentTimeMillis()
    val cooldownActive = snapshot != null && snapshot.cooldownUntil > now
    val cooldownOver = snapshot != null && snapshot.cooldownUntil in 1 until now

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Release protection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (hasPassphrase) {
                        "Releasing starts a 24-hour cooldown, or enter your partner passphrase " +
                            "to release immediately. The shield stays on until release completes."
                    } else {
                        "No passphrase is set. Releasing starts a 24-hour cooldown — the " +
                            "settings shield stays on until it ends."
                    },
                )
                if (cooldownActive && !cooldownOver) {
                    Text(
                        text = "Cooldown running: " +
                            MainViewModel.formatRemaining(snapshot.cooldownUntil - now) + " left.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (hasPassphrase) {
                    OutlinedTextField(
                        value = passphraseInput,
                        onValueChange = { passphraseInput = it },
                        label = { Text("Partner passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onReleaseWithPassphrase(passphraseInput) },
                enabled = hasPassphrase,
            ) {
                Text("Release with passphrase")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onRelease(); onDismiss() },
                enabled = !cooldownActive || cooldownOver,
            ) {
                Text(
                    text = if (cooldownOver) {
                        "Complete release"
                    } else {
                        "Start 24-hour cooldown"
                    },
                )
            }
        },
    )
}

@Composable
private fun PassphraseDialog(
    onChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphraseInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set passphrase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Only a hash is stored. The passphrase can release protection " +
                        "before the cooldown ends — share it with your accountability partner, " +
                        "not with yourself.",
                )
                OutlinedTextField(
                    value = passphraseInput,
                    onValueChange = { passphraseInput = it },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onChange(passphraseInput) },
                enabled = passphraseInput.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("SelfGuard", text))
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
