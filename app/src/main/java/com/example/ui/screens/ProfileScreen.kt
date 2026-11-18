package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.MediaUtils
import com.example.data.model.ProfPlusConstants
import com.example.data.model.UserPublicProfile
import com.example.ui.auth.DropdownSelector
import com.example.ui.components.RoleChip
import com.example.ui.components.UserAvatar
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.ProfessionalBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.SessionViewModel

@Composable
fun ProfileScreen(
    currentUser: UserPublicProfile,
    email: String,
    isAdminOrMod: Boolean,
    unreadNotificationsCount: Int,
    sessionViewModel: SessionViewModel,
    onOpenMessages: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenEstablishments: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdmin: () -> Unit
) {
    val bookmarks by sessionViewModel.bookmarks.collectAsStateWithLifecycle()
    val myVerifications by sessionViewModel.myVerifications.collectAsStateWithLifecycle()
    val isBusy by sessionViewModel.isBusy.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var showVerifyDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(
                                displayName = currentUser.displayName,
                                photoData = currentUser.photoData,
                                verified = currentUser.verified,
                                size = 68.dp
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    currentUser.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                RoleChip(currentUser.role)
                                if (email.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "${currentUser.jobTitle.ifBlank { "Enseignant(e)" }} • ${currentUser.subject}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${currentUser.schoolLevel} • ${currentUser.city}, ${currentUser.region} (${currentUser.country})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (currentUser.establishmentName.isNotBlank()) {
                            Text(
                                "Établissement : ${currentUser.establishmentName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ProfessionalBlue
                            )
                        }
                        if (currentUser.bio.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(currentUser.bio, style = MaterialTheme.typography.bodyMedium)
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showEditDialog = true },
                                modifier = Modifier.weight(1f).testTag("edit_profile_btn")
                            ) {
                                Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Modifier")
                            }
                            if (!currentUser.verified) {
                                val hasPending = myVerifications.any { it.status == "PENDING" }
                                OutlinedButton(
                                    onClick = { showVerifyDialog = true },
                                    enabled = !hasPending,
                                    modifier = Modifier.weight(1f).testTag("request_verification_btn")
                                ) {
                                    Icon(Icons.Filled.VerifiedUser, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (hasPending) "En cours" else "Vérifier")
                                }
                            } else {
                                Surface(
                                    color = SuccessGreen.copy(alpha = 0.12f),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Icon(Icons.Filled.VerifiedUser, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Vérifié", color = SuccessGreen, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("Espaces et outils", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column {
                        ProfileMenuRow(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            title = "Messagerie privée",
                            subtitle = "Échanges directs et discussions d'équipe",
                            onClick = onOpenMessages,
                            testTag = "profile_menu_messages"
                        )
                        HorizontalDivider()
                        ProfileMenuRow(
                            icon = Icons.Filled.Notifications,
                            title = if (unreadNotificationsCount > 0) "Notifications ($unreadNotificationsCount non lues)" else "Notifications",
                            subtitle = "Commentaires, réponses, mentions et annonces",
                            onClick = onOpenNotifications,
                            testTag = "profile_menu_notifications"
                        )
                        HorizontalDivider()
                        ProfileMenuRow(
                            icon = Icons.Filled.AccountBalance,
                            title = "Établissements scolaires",
                            subtitle = "Pages officielles des écoles, lycées et universités",
                            onClick = onOpenEstablishments,
                            testTag = "profile_menu_establishments"
                        )
                        HorizontalDivider()
                        ProfileMenuRow(
                            icon = Icons.Filled.AutoAwesome,
                            title = "Assistant pédagogique IA (Gemini)",
                            subtitle = "Fiches de cours, résumés, explications et débats",
                            onClick = onOpenAiAssistant,
                            testTag = "profile_menu_ai"
                        )
                        HorizontalDivider()
                        ProfileMenuRow(
                            icon = Icons.Filled.Bookmark,
                            title = "Favoris enregistrés (${bookmarks.size})",
                            subtitle = "Publications et contenus sauvegardés",
                            onClick = { showBookmarksDialog = true },
                            testTag = "profile_menu_bookmarks"
                        )
                        HorizontalDivider()
                        ProfileMenuRow(
                            icon = Icons.Filled.Settings,
                            title = "Paramètres & Compte",
                            subtitle = "Langue FR/EN, notifications, sécurité, suppression",
                            onClick = onOpenSettings,
                            testTag = "profile_menu_settings"
                        )
                        if (isAdminOrMod) {
                            HorizontalDivider()
                            ProfileMenuRow(
                                icon = Icons.Filled.AdminPanelSettings,
                                title = "Tableau de bord Administration",
                                subtitle = "Vérifications, signalements, rôles et statistiques",
                                onClick = onOpenAdmin,
                                testTag = "profile_menu_admin"
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            currentUser = currentUser,
            isBusy = isBusy,
            onDismiss = { showEditDialog = false },
            onSave = { fn, ln, ctry, cty, reg, job, subj, lvl, est, bioText, photo ->
                sessionViewModel.updateProfile(fn, ln, ctry, cty, reg, job, subj, lvl, est, bioText, photo)
                showEditDialog = false
            }
        )
    }

    if (showVerifyDialog) {
        VerificationRequestDialog(
            title = "Demande de vérification enseignant",
            onDismiss = { showVerifyDialog = false },
            onSubmit = { docInfo ->
                sessionViewModel.submitVerification(
                    userName = currentUser.displayName,
                    targetType = "TEACHER",
                    targetId = currentUser.userId,
                    targetName = currentUser.displayName,
                    docInfo = docInfo
                )
                showVerifyDialog = false
            }
        )
    }

    if (showBookmarksDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarksDialog = false },
            title = { Text("Mes favoris (${bookmarks.size})") },
            text = {
                if (bookmarks.isEmpty()) {
                    Text("Aucun favori enregistré pour le moment.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(bookmarks, key = { it.id }) { bm ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(modifier = Modifier.padding(10.dp).fillMaxWidth()) {
                                    Text(bm.title, style = MaterialTheme.typography.labelLarge)
                                    Text(bm.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookmarksDialog = false }) { Text("Fermer") }
            }
        )
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = GoldAccent)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EditProfileDialog(
    currentUser: UserPublicProfile,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        firstName: String,
        lastName: String,
        country: String,
        city: String,
        region: String,
        jobTitle: String,
        subject: String,
        schoolLevel: String,
        establishmentName: String,
        bio: String,
        photoData: String
    ) -> Unit
) {
    val context = LocalContext.current
    var firstName by remember { mutableStateOf(currentUser.firstName) }
    var lastName by remember { mutableStateOf(currentUser.lastName) }
    var country by remember { mutableStateOf(currentUser.country.ifBlank { "Burkina Faso" }) }
    var city by remember { mutableStateOf(currentUser.city) }
    var region by remember { mutableStateOf(currentUser.region.ifBlank { "Centre" }) }
    var jobTitle by remember { mutableStateOf(currentUser.jobTitle) }
    var subject by remember { mutableStateOf(currentUser.subject.ifBlank { ProfPlusConstants.SUBJECTS.first() }) }
    var schoolLevel by remember { mutableStateOf(currentUser.schoolLevel.ifBlank { ProfPlusConstants.SCHOOL_LEVELS[2] }) }
    var establishmentName by remember { mutableStateOf(currentUser.establishmentName) }
    var bio by remember { mutableStateOf(currentUser.bio) }
    var photoData by remember { mutableStateOf(currentUser.photoData) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoData = MediaUtils.encodeImageUriToDataUri(context, uri).orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier mon profil") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AddAPhoto, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Changer la photo de profil")
                }
                OutlinedTextField(firstName, { firstName = it }, label = { Text("Prénom *") }, singleLine = true)
                OutlinedTextField(lastName, { lastName = it }, label = { Text("Nom *") }, singleLine = true)
                DropdownSelector("Pays", country, ProfPlusConstants.AFRICAN_COUNTRIES, { country = it })
                OutlinedTextField(city, { city = it }, label = { Text("Ville *") }, singleLine = true)
                DropdownSelector("Région", region, ProfPlusConstants.BURKINA_REGIONS, { region = it })
                OutlinedTextField(jobTitle, { jobTitle = it }, label = { Text("Fonction") }, singleLine = true)
                DropdownSelector("Matière", subject, ProfPlusConstants.SUBJECTS, { subject = it })
                DropdownSelector("Niveau scolaire", schoolLevel, ProfPlusConstants.SCHOOL_LEVELS, { schoolLevel = it })
                OutlinedTextField(establishmentName, { establishmentName = it }, label = { Text("Établissement") }, singleLine = true)
                OutlinedTextField(bio, { bio = it }, label = { Text("Présentation professionnelle") }, minLines = 3)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(firstName, lastName, country, city, region, jobTitle, subject, schoolLevel, establishmentName, bio, photoData)
                },
                enabled = !isBusy && firstName.isNotBlank() && lastName.isNotBlank()
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
fun VerificationRequestDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var docInfo by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Indiquez votre matricule, numéro d'arrêté ou référence d'établissement pour vérification par l'administration.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = docInfo,
                    onValueChange = { docInfo = it },
                    label = { Text("Référence / Justificatif *") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("verification_doc_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (docInfo.isNotBlank()) onSubmit(docInfo) },
                enabled = docInfo.trim().length >= 5,
                modifier = Modifier.testTag("verification_submit_btn")
            ) {
                Text("Envoyer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
