package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.data.model.UserPublicProfile
import com.example.data.model.UserRole
import com.example.ui.auth.DropdownSelector
import com.example.ui.components.ProfPlusEmptyState
import com.example.ui.components.RoleChip
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.GoldAccent
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.CommunityViewModel
import com.example.ui.viewmodel.DebateViewModel
import com.example.ui.viewmodel.FeedViewModel
import com.example.ui.viewmodel.SessionViewModel

@Composable
fun AdminDashboardScreen(
    currentUser: UserPublicProfile,
    isAdmin: Boolean,
    adminViewModel: AdminViewModel,
    sessionViewModel: SessionViewModel,
    feedViewModel: FeedViewModel,
    debateViewModel: DebateViewModel,
    communityViewModel: CommunityViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val reports by adminViewModel.reports.collectAsStateWithLifecycle()
    val verifications by adminViewModel.verificationRequests.collectAsStateWithLifecycle()
    val adminLogs by adminViewModel.adminLogs.collectAsStateWithLifecycle()
    val categories by adminViewModel.categories.collectAsStateWithLifecycle()

    val directory by sessionViewModel.directory.collectAsStateWithLifecycle()
    val postsState by feedViewModel.postsState.collectAsStateWithLifecycle()
    val debatesState by debateViewModel.debatesState.collectAsStateWithLifecycle()
    val groupsState by communityViewModel.publicGroupsState.collectAsStateWithLifecycle()
    val resourcesState by communityViewModel.resourcesState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Statistiques", "Vérifications", "Signalements", "Utilisateurs", "Catégories & Logs")

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
                Text("Administration prof+", style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                ScrollableTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { idx, label ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(label) },
                            modifier = Modifier.testTag("admin_tab_$idx")
                        )
                    }
                }

                when (selectedTab) {
                    0 -> {
                        val postsCount = (postsState as? UiState.Success)?.data?.size ?: 0
                        val debatesCount = (debatesState as? UiState.Success)?.data?.size ?: 0
                        val groupsCount = (groupsState as? UiState.Success)?.data?.size ?: 0
                        val resourcesCount = (resourcesState as? UiState.Success)?.data?.size ?: 0
                        val pendingVerif = verifications.count { it.status == "PENDING" }
                        val pendingReports = reports.count { it.status == "PENDING" }

                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Text("Données réelles Firestore", style = MaterialTheme.typography.titleSmall)
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    StatTile("Utilisateurs", "${directory.size}", Modifier.weight(1f))
                                    StatTile("Vérifiés", "${directory.count { it.verified }}", Modifier.weight(1f))
                                }
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    StatTile("Publications", "$postsCount", Modifier.weight(1f))
                                    StatTile("Débats", "$debatesCount", Modifier.weight(1f))
                                }
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    StatTile("Groupes", "$groupsCount", Modifier.weight(1f))
                                    StatTile("Ressources", "$resourcesCount", Modifier.weight(1f))
                                }
                            }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    StatTile("Vérifications en attente", "$pendingVerif", Modifier.weight(1f))
                                    StatTile("Signalements ouverts", "$pendingReports", Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    1 -> {
                        if (verifications.isEmpty()) {
                            ProfPlusEmptyState("Aucune demande de vérification", "Les demandes soumises apparaîtront ici.")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(verifications, key = { it.id }) { req ->
                                    var reviewNote by remember(req.id) { mutableStateOf("") }
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("${req.targetName} (${req.targetType})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("Demandeur : ${req.userName} • Statut : ${req.status}", style = MaterialTheme.typography.bodySmall)
                                            Text("Justificatif : ${req.documentInfo}", style = MaterialTheme.typography.bodyMedium)
                                            if (req.status == "PENDING") {
                                                OutlinedTextField(
                                                    value = reviewNote,
                                                    onValueChange = { reviewNote = it },
                                                    label = { Text("Note de décision") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = { adminViewModel.reviewVerification(req, true, reviewNote, currentUser.displayName) },
                                                        modifier = Modifier.weight(1f)
                                                    ) { Text("Approuver") }
                                                    OutlinedButton(
                                                        onClick = { adminViewModel.reviewVerification(req, false, reviewNote, currentUser.displayName) },
                                                        modifier = Modifier.weight(1f)
                                                    ) { Text("Refuser") }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        if (reports.isEmpty()) {
                            ProfPlusEmptyState("Aucun signalement", "Aucun contenu signalé pour le moment.")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(reports, key = { it.id }) { rep ->
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("${rep.targetType} • ${rep.reason}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("Par ${rep.reporterName} • Statut : ${rep.status}", style = MaterialTheme.typography.bodySmall)
                                            if (rep.details.isNotBlank()) Text(rep.details, style = MaterialTheme.typography.bodyMedium)
                                            if (rep.status == "PENDING") {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = { adminViewModel.resolveReport(rep, "REVIEWED", true, currentUser.displayName) },
                                                        modifier = Modifier.weight(1f)
                                                    ) { Text("Retirer contenu") }
                                                    OutlinedButton(
                                                        onClick = { adminViewModel.resolveReport(rep, "DISMISSED", false, currentUser.displayName) },
                                                        modifier = Modifier.weight(1f)
                                                    ) { Text("Rejeter") }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(directory, key = { it.userId }) { user ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(user.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                Text("${user.subject} • ${user.city}", style = MaterialTheme.typography.bodySmall)
                                            }
                                            RoleChip(user.role)
                                        }
                                        if (user.userId != currentUser.userId) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        adminViewModel.toggleSuspendUser(user.userId, !user.suspended, currentUser.displayName)
                                                    }
                                                ) {
                                                    Text(if (user.suspended) "Réactiver" else "Suspendre")
                                                }
                                                if (isAdmin) {
                                                    val nextRole = if (user.role == UserRole.MODERATEUR.name) {
                                                        UserRole.ENSEIGNANT
                                                    } else {
                                                        UserRole.MODERATEUR
                                                    }
                                                    OutlinedButton(
                                                        onClick = {
                                                            adminViewModel.assignRole(user.userId, nextRole, currentUser.displayName)
                                                        }
                                                    ) {
                                                        Text(
                                                            if (nextRole == UserRole.MODERATEUR) "Nommer Modérateur"
                                                            else "Retirer Modérateur"
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    4 -> {
                        var catName by remember { mutableStateOf("") }
                        var catKind by remember { mutableStateOf("DEBATE") }
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (isAdmin) {
                                item {
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("Ajouter une catégorie", style = MaterialTheme.typography.titleSmall)
                                            OutlinedTextField(catName, { catName = it }, label = { Text("Nom *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                            DropdownSelector("Type", catKind, listOf("DEBATE", "SUBJECT", "LEVEL"), { catKind = it })
                                            Button(
                                                onClick = {
                                                    adminViewModel.addCategory(catName, catKind, currentUser.displayName)
                                                    catName = ""
                                                },
                                                enabled = catName.trim().length >= 2
                                            ) { Text("Ajouter") }
                                        }
                                    }
                                }
                            }
                            if (categories.isNotEmpty()) {
                                item { Text("Catégories personnalisées (${categories.size})", style = MaterialTheme.typography.titleSmall) }
                                items(categories, key = { it.id }) { cat ->
                                    Text("• ${cat.name} (${cat.kind})", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            item {
                                Spacer(Modifier.height(6.dp))
                                Text("Journal d'administration (${adminLogs.size})", style = MaterialTheme.typography.titleSmall)
                            }
                            items(adminLogs, key = { it.id }) { log ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("${log.action} • ${log.adminName}", style = MaterialTheme.typography.labelLarge, color = GoldAccent)
                                        Text(log.details, style = MaterialTheme.typography.bodySmall)
                                        Text(formatTimestamp(log.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
