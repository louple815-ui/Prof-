package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.data.model.Establishment
import com.example.data.model.ProfPlusConstants
import com.example.data.model.UserPublicProfile
import com.example.ui.auth.DropdownSelector
import com.example.ui.components.ProfPlusEmptyState
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.ProfessionalBlue
import com.example.ui.viewmodel.CommunicationViewModel
import com.example.ui.viewmodel.SessionViewModel

@Composable
fun EstablishmentsScreen(
    currentUser: UserPublicProfile,
    communicationViewModel: CommunicationViewModel,
    sessionViewModel: SessionViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val establishmentsState by communicationViewModel.establishmentsState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var activeEstablishment by remember { mutableStateOf<Establishment?>(null) }
    var verifyEstablishment by remember { mutableStateOf<Establishment?>(null) }
    var editAnnouncementEst by remember { mutableStateOf<Establishment?>(null) }

    if (activeEstablishment != null) {
        val est = activeEstablishment!!
        BackHandler { activeEstablishment = null }
        Scaffold(
            topBar = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    IconButton(onClick = { activeEstablishment = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                    Text(est.officialName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (est.verified) {
                        Icon(Icons.Filled.Verified, contentDescription = "Établissement vérifié", tint = GoldAccent)
                    }
                }
            }
        ) { innerPadding ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(est.officialName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${est.establishmentType} • ${est.city}, ${est.region} (${est.country})", style = MaterialTheme.typography.bodyMedium)
                            Text("Responsable : ${est.directorName}", style = MaterialTheme.typography.bodySmall, color = ProfessionalBlue)
                            if (est.description.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(est.description, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Campaign, contentDescription = null, tint = GoldAccent)
                                Spacer(Modifier.width(8.dp))
                                Text("Annonces officielles", style = MaterialTheme.typography.titleSmall)
                            }
                            HorizontalDivider()
                            Text(
                                est.announcement.ifBlank { "Aucune annonce publiée pour le moment." },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (est.directorId == currentUser.userId) {
                                OutlinedButton(onClick = { editAnnouncementEst = est }) {
                                    Text("Mettre à jour l'annonce")
                                }
                            }
                        }
                    }

                    if (!est.verified && est.directorId == currentUser.userId) {
                        Button(
                            onClick = { verifyEstablishment = est },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Demander la vérification de l'établissement")
                        }
                    }
                }
            }
        }
        editAnnouncementEst?.let { target ->
            var text by remember { mutableStateOf(target.announcement) }
            AlertDialog(
                onDismissRequest = { editAnnouncementEst = null },
                title = { Text("Annonce officielle") },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            communicationViewModel.updateAnnouncement(target.id, text)
                            activeEstablishment = target.copy(announcement = text)
                            editAnnouncementEst = null
                        }
                    ) { Text("Publier") }
                },
                dismissButton = {
                    TextButton(onClick = { editAnnouncementEst = null }) { Text("Annuler") }
                }
            )
        }
        verifyEstablishment?.let { target ->
            VerificationRequestDialog(
                title = "Vérification de ${target.officialName}",
                onDismiss = { verifyEstablishment = null },
                onSubmit = { docInfo ->
                    sessionViewModel.submitVerification(
                        userName = currentUser.displayName,
                        targetType = "ESTABLISHMENT",
                        targetId = target.id,
                        targetName = target.officialName,
                        docInfo = docInfo
                    )
                    verifyEstablishment = null
                }
            )
        }
        return
    }

    Scaffold(
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
                Text("Établissements", style = MaterialTheme.typography.titleLarge)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = NavyPrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("create_establishment_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Créer une page établissement")
            }
        }
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                when (val state = establishmentsState) {
                    is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    is UiState.Error -> ProfPlusEmptyState("Erreur établissements", state.message)
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            ProfPlusEmptyState(
                                title = "Aucun établissement enregistré",
                                subtitle = "Créez la page officielle de votre école, lycée, collège ou université."
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(state.data, key = { it.id }) { est ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { activeEstablishment = est }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(16.dp)
                                        ) {
                                            Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(32.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(est.officialName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                    if (est.verified) {
                                                        Spacer(Modifier.width(6.dp))
                                                        Icon(Icons.Filled.Verified, contentDescription = "Vérifié", tint = GoldAccent, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                                Text(
                                                    "${est.establishmentType} • ${est.city}, ${est.region}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text("Responsable : ${est.directorName}", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var officialName by remember { mutableStateOf("") }
        var country by remember { mutableStateOf("Burkina Faso") }
        var city by remember { mutableStateOf("Ouagadougou") }
        var region by remember { mutableStateOf("Centre") }
        var type by remember { mutableStateOf(ProfPlusConstants.ESTABLISHMENT_TYPES[2]) }
        var desc by remember { mutableStateOf("") }
        var announcement by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Créer une page établissement") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = officialName,
                        onValueChange = { officialName = it },
                        label = { Text("Nom officiel *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("establishment_name_input")
                    )
                    DropdownSelector("Type", type, ProfPlusConstants.ESTABLISHMENT_TYPES, { type = it })
                    DropdownSelector("Pays", country, ProfPlusConstants.AFRICAN_COUNTRIES, { country = it })
                    OutlinedTextField(city, { city = it }, label = { Text("Ville *") }, singleLine = true)
                    DropdownSelector("Région", region, ProfPlusConstants.BURKINA_REGIONS, { region = it })
                    OutlinedTextField(desc, { desc = it }, label = { Text("Présentation") }, minLines = 2)
                    OutlinedTextField(announcement, { announcement = it }, label = { Text("Première annonce officielle") }, minLines = 2)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        communicationViewModel.createEstablishment(
                            currentUser, officialName, country, city, region, type, desc, announcement
                        )
                        showCreateDialog = false
                    },
                    enabled = officialName.trim().length >= 2 && city.isNotBlank(),
                    modifier = Modifier.testTag("establishment_submit_btn")
                ) { Text("Créer") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Annuler") }
            }
        )
    }
}
