package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.LocalAppCopy
import com.example.ui.viewmodel.SessionViewModel

@Composable
fun SettingsScreen(
    currentLanguage: String,
    darkMode: Boolean,
    localAlertsEnabled: Boolean,
    email: String,
    isEmailVerified: Boolean,
    sessionViewModel: SessionViewModel,
    onLanguageChange: (String) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onLocalAlertsChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onAccountDeleted: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val copy = LocalAppCopy.current
    val privateInfo by sessionViewModel.privateInfo.collectAsStateWithLifecycle()
    val isBusy by sessionViewModel.isBusy.collectAsStateWithLifecycle()

    var showDeleteConfirm by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onLocalAlertsChange(granted)
    }

    Scaffold(
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
                Text(copy.settings, style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                if (copy.lang == "en") "Language & Appearance" else "Langue et affichage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = currentLanguage == "fr",
                                    onClick = { onLanguageChange("fr") },
                                    label = { Text("Français") },
                                    modifier = Modifier.testTag("settings_lang_fr")
                                )
                                FilterChip(
                                    selected = currentLanguage == "en",
                                    onClick = { onLanguageChange("en") },
                                    label = { Text("English") },
                                    modifier = Modifier.testTag("settings_lang_en")
                                )
                            }
                            HorizontalDivider()
                            SwitchSettingRow(
                                label = if (copy.lang == "en") "Dark mode" else "Thème sombre",
                                checked = darkMode,
                                onCheckedChange = onDarkModeChange
                            )
                        }
                    }
                }

                item {
                    val info = privateInfo
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (copy.lang == "en") "Notifications" else "Préférences de notifications",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            SwitchSettingRow(
                                label = if (copy.lang == "en") "Device alert banners" else "Alertes locales Android",
                                checked = localAlertsEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        onLocalAlertsChange(enabled)
                                    }
                                }
                            )
                            if (info != null) {
                                HorizontalDivider()
                                SwitchSettingRow(
                                    label = if (copy.lang == "en") "Comments" else "Commentaires",
                                    checked = info.notifyComments,
                                    onCheckedChange = {
                                        sessionViewModel.updateNotificationPrefs(
                                            it, info.notifyReplies, info.notifyMentions, info.notifyMessages,
                                            info.notifyInvitations, info.notifyAnnouncements, info.notifyGroups, currentLanguage
                                        )
                                    }
                                )
                                SwitchSettingRow(
                                    label = if (copy.lang == "en") "Replies" else "Réponses",
                                    checked = info.notifyReplies,
                                    onCheckedChange = {
                                        sessionViewModel.updateNotificationPrefs(
                                            info.notifyComments, it, info.notifyMentions, info.notifyMessages,
                                            info.notifyInvitations, info.notifyAnnouncements, info.notifyGroups, currentLanguage
                                        )
                                    }
                                )
                                SwitchSettingRow(
                                    label = if (copy.lang == "en") "Mentions" else "Mentions",
                                    checked = info.notifyMentions,
                                    onCheckedChange = {
                                        sessionViewModel.updateNotificationPrefs(
                                            info.notifyComments, info.notifyReplies, it, info.notifyMessages,
                                            info.notifyInvitations, info.notifyAnnouncements, info.notifyGroups, currentLanguage
                                        )
                                    }
                                )
                                SwitchSettingRow(
                                    label = if (copy.lang == "en") "Private messages" else "Messages privés",
                                    checked = info.notifyMessages,
                                    onCheckedChange = {
                                        sessionViewModel.updateNotificationPrefs(
                                            info.notifyComments, info.notifyReplies, info.notifyMentions, it,
                                            info.notifyInvitations, info.notifyAnnouncements, info.notifyGroups, currentLanguage
                                        )
                                    }
                                )
                                SwitchSettingRow(
                                    label = if (copy.lang == "en") "Invitations" else "Invitations",
                                    checked = info.notifyInvitations,
                                    onCheckedChange = {
                                        sessionViewModel.updateNotificationPrefs(
                                            info.notifyComments, info.notifyReplies, info.notifyMentions, info.notifyMessages,
                                            it, info.notifyAnnouncements, info.notifyGroups, currentLanguage
                                        )
                                    }
                                )
                                SwitchSettingRow(
                                    label = if (copy.lang == "en") "School announcements" else "Annonces d'établissement",
                                    checked = info.notifyAnnouncements,
                                    onCheckedChange = {
                                        sessionViewModel.updateNotificationPrefs(
                                            info.notifyComments, info.notifyReplies, info.notifyMentions, info.notifyMessages,
                                            info.notifyInvitations, it, info.notifyGroups, currentLanguage
                                        )
                                    }
                                )
                                SwitchSettingRow(
                                    label = if (copy.lang == "en") "Groups" else "Activité des groupes",
                                    checked = info.notifyGroups,
                                    onCheckedChange = {
                                        sessionViewModel.updateNotificationPrefs(
                                            info.notifyComments, info.notifyReplies, info.notifyMentions, info.notifyMessages,
                                            info.notifyInvitations, info.notifyAnnouncements, it, currentLanguage
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (copy.lang == "en") "Account & Security" else "Compte et sécurité",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("E-mail : ${email.ifBlank { "Compte Google" }}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (isEmailVerified) "Adresse e-mail vérifiée" else "Vérification e-mail gérée via Google",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Mot de passe et récupération de compte sécurisés via votre compte Google (Credential Manager).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = onSignOut,
                                modifier = Modifier.fillMaxWidth().testTag("settings_sign_out_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, null)
                                Spacer(Modifier.widthIn(min = 8.dp))
                                Text(copy.signOutBtn)
                            }
                            Button(
                                onClick = { showDeleteConfirm = true },
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("settings_delete_account_btn")
                            ) {
                                Icon(Icons.Filled.DeleteForever, null)
                                Spacer(Modifier.widthIn(min = 8.dp))
                                Text(if (copy.lang == "en") "Delete my account" else "Supprimer mon compte")
                            }
                        }
                    }
                }

                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text("prof+ • ONADJA PRODUCTION", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Le réseau professionnel des enseignants • Burkina Faso & Afrique",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Supprimer définitivement votre compte ?") },
            text = {
                Text("Votre profil prof+ et vos données privées seront supprimés. Cette action est irréversible.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        sessionViewModel.deleteAccount(onAccountDeleted)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun SwitchSettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
