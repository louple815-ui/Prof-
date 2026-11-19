package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.LocalAppCopy
import com.example.core.MediaUtils
import com.example.core.UiState
import com.example.data.local.DownloadedResourceEntity
import com.example.data.model.ProfPlusConstants
import com.example.data.model.ResourceItem
import com.example.data.model.UserPublicProfile
import com.example.ui.auth.DropdownSelector
import com.example.ui.components.ProfPlusEmptyState
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.CommunityViewModel

@Composable
fun ResourcesScreen(
    currentUser: UserPublicProfile,
    communityViewModel: CommunityViewModel,
    showCreateDialog: Boolean,
    onDismissCreateDialog: () -> Unit
) {
    val copy = LocalAppCopy.current
    val context = LocalContext.current
    val resourcesState by communityViewModel.resourcesState.collectAsStateWithLifecycle()
    val offlineResources by communityViewModel.offlineResources.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Library, 1 = Offline Room
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("TOUS") }
    var selectedSubject by remember { mutableStateOf("Toutes") }
    var previewCloudResource by remember { mutableStateOf<ResourceItem?>(null) }
    var previewOfflineResource by remember { mutableStateOf<DownloadedResourceEntity?>(null) }

    val offlineIds = remember(offlineResources) { offlineResources.map { it.resourceId }.toSet() }
    val typeFilters = remember { listOf("TOUS" to "Tous") + ProfPlusConstants.RESOURCE_TYPES }
    val subjectFilters = remember { listOf("Toutes") + ProfPlusConstants.SUBJECTS }

    if (previewCloudResource != null) {
        val item = previewCloudResource!!
        BackHandler { previewCloudResource = null }
        ResourceDetailPane(
            title = item.title,
            typeLabel = ProfPlusConstants.RESOURCE_TYPES.firstOrNull { it.first == item.resourceType }?.second ?: item.resourceType,
            subject = item.subject,
            level = item.level,
            authorName = item.authorName,
            fileName = item.fileName,
            description = item.description,
            contentBody = item.contentBody,
            isOfflineSaved = item.id in offlineIds,
            onBack = { previewCloudResource = null },
            onDownloadOffline = { communityViewModel.downloadResourceOffline(item) },
            onShare = {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${item.title} (${item.subject} - ${item.level})\n\n${item.description}\n\n${item.contentBody.take(1000)}")
                }
                context.startActivity(Intent.createChooser(share, "Partager la ressource"))
            }
        )
        return
    }

    if (previewOfflineResource != null) {
        val item = previewOfflineResource!!
        BackHandler { previewOfflineResource = null }
        ResourceDetailPane(
            title = item.title,
            typeLabel = ProfPlusConstants.RESOURCE_TYPES.firstOrNull { it.first == item.resourceType }?.second ?: item.resourceType,
            subject = item.subject,
            level = item.level,
            authorName = item.authorName,
            fileName = item.fileName,
            description = item.description,
            contentBody = item.contentBody,
            isOfflineSaved = true,
            onBack = { previewOfflineResource = null },
            onDownloadOffline = {},
            onShare = {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${item.title} (${item.subject} - ${item.level})\n\n${item.description}\n\n${item.contentBody.take(1000)}")
                }
                context.startActivity(Intent.createChooser(share, "Partager la ressource"))
            }
        )
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(if (copy.lang == "en") "Library" else "Bibliothèque") },
                    modifier = Modifier.testTag("resources_tab_cloud")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            if (copy.lang == "en") "Offline (${offlineResources.size})"
                            else "Hors connexion (${offlineResources.size})"
                        )
                    },
                    modifier = Modifier.testTag("resources_tab_offline")
                )
            }

            if (selectedTab == 0) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher un cours, PDF, corrigé, fiche...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("resource_search_input")
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(typeFilters, key = { it.first }) { (code, label) ->
                        FilterChip(
                            selected = selectedType == code,
                            onClick = { selectedType = code },
                            label = { Text(label) }
                        )
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(subjectFilters) { subject ->
                        FilterChip(
                            selected = selectedSubject == subject,
                            onClick = { selectedSubject = subject },
                            label = { Text(subject) }
                        )
                    }
                }

                when (val state = resourcesState) {
                    is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    is UiState.Error -> ProfPlusEmptyState("Erreur bibliothèque", state.message)
                    is UiState.Success -> {
                        val filtered = remember(state.data, searchQuery, selectedType, selectedSubject) {
                            state.data.filter { item ->
                                (selectedType == "TOUS" || item.resourceType == selectedType) &&
                                    (selectedSubject == "Toutes" || item.subject == selectedSubject) &&
                                    (searchQuery.isBlank() ||
                                        item.title.contains(searchQuery, ignoreCase = true) ||
                                        item.description.contains(searchQuery, ignoreCase = true) ||
                                        item.subject.contains(searchQuery, ignoreCase = true))
                            }
                        }
                        if (filtered.isEmpty()) {
                            ProfPlusEmptyState(
                                title = "Aucune ressource trouvée",
                                subtitle = "Partagez des fiches pédagogiques, exercices, corrigés ou supports de cours."
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filtered, key = { it.id }) { resource ->
                                    val saved = resource.id in offlineIds
                                    ResourceCard(
                                        resource = resource,
                                        isOfflineSaved = saved,
                                        onClick = { previewCloudResource = resource },
                                        onDownload = { communityViewModel.downloadResourceOffline(resource) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                if (offlineResources.isEmpty()) {
                    ProfPlusEmptyState(
                        title = "Aucune ressource enregistrée",
                        subtitle = "Téléchargez des ressources depuis la bibliothèque pour y accéder sans connexion."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(offlineResources, key = { it.resourceId }) { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { previewOfflineResource = item }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(Icons.Filled.OfflinePin, contentDescription = null, tint = SuccessGreen)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${item.subject} • ${item.level} • ${item.fileName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { communityViewModel.removeOfflineResource(item.resourceId) }
                                    ) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Supprimer copie locale")
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
        CreateResourceDialog(
            onDismiss = onDismissCreateDialog,
            onPublish = { title, desc, type, subject, level, fileName, mime, size, body ->
                communityViewModel.publishResource(
                    profile = currentUser,
                    title = title,
                    description = desc,
                    resourceType = type,
                    subject = subject,
                    level = level,
                    fileName = fileName,
                    mimeType = mime,
                    fileSizeBytes = size,
                    contentBody = body
                )
                onDismissCreateDialog()
            }
        )
    }
}

@Composable
private fun ResourceCard(
    resource: ResourceItem,
    isOfflineSaved: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    val typeLabel = ProfPlusConstants.RESOURCE_TYPES.firstOrNull { it.first == resource.resourceType }?.second ?: resource.resourceType
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = formatTimestamp(resource.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(resource.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "${resource.subject} • ${resource.level} • Par ${resource.authorName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (resource.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(resource.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${resource.downloadsCount} téléchargement(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onDownload,
                    enabled = !isOfflineSaved
                ) {
                    Icon(
                        imageVector = if (isOfflineSaved) Icons.Filled.CloudDone else Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isOfflineSaved) "Hors ligne prêt" else "Télécharger")
                }
            }
        }
    }
}

@Composable
private fun ResourceDetailPane(
    title: String,
    typeLabel: String,
    subject: String,
    level: String,
    authorName: String,
    fileName: String,
    description: String,
    contentBody: String,
    isOfflineSaved: Boolean,
    onBack: () -> Unit,
    onDownloadOffline: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$subject • $level", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = "Partager")
                }
            }
            HorizontalDivider()

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = GoldAccent)
                            Spacer(Modifier.width(8.dp))
                            Text(typeLabel, style = MaterialTheme.typography.labelLarge)
                        }
                        Text("Auteur : $authorName", style = MaterialTheme.typography.bodySmall)
                        if (fileName.isNotBlank()) {
                            Text("Fichier : $fileName", style = MaterialTheme.typography.bodySmall)
                        }
                        if (description.isNotBlank()) {
                            Text(description, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (!isOfflineSaved) {
                            Button(onClick = onDownloadOffline, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Enregistrer pour usage hors connexion")
                            }
                        }
                    }
                }

                if (contentBody.isNotBlank() && !contentBody.startsWith("base64:")) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Contenu pédagogique", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            Text(contentBody, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if (contentBody.startsWith("base64:")) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(Icons.Filled.AttachFile, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Fichier joint stocké ($fileName). Disponible hors connexion dans votre espace local.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateResourceDialog(
    onDismiss: () -> Unit,
    onPublish: (
        title: String,
        desc: String,
        type: String,
        subject: String,
        level: String,
        fileName: String,
        mime: String,
        size: Long,
        body: String
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var typePair by remember { mutableStateOf(ProfPlusConstants.RESOURCE_TYPES[4]) }
    var subject by remember { mutableStateOf(ProfPlusConstants.SUBJECTS.first()) }
    var level by remember { mutableStateOf(ProfPlusConstants.SCHOOL_LEVELS[2]) }
    var contentBody by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var mimeType by remember { mutableStateOf("text/plain") }
    var fileSize by remember { mutableStateOf(0L) }
    var fileError by remember { mutableStateOf<String?>(null) }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val doc = MediaUtils.readDocumentFromUri(context, uri)
            if (doc == null) {
                fileError = "Impossible de lire ce document."
            } else if (doc.isTooLarge) {
                fileError = "Fichier trop volumineux (max 280 Ko)."
            } else {
                fileError = null
                fileName = doc.fileName
                mimeType = doc.mimeType
                fileSize = doc.sizeBytes
                if (doc.contentBody.isNotBlank()) contentBody = doc.contentBody
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Partager une ressource pédagogique") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre de la ressource *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("resource_title_input")
                )
                DropdownSelector(
                    label = "Type",
                    selected = typePair.second,
                    options = ProfPlusConstants.RESOURCE_TYPES.map { it.second },
                    onSelect = { label ->
                        typePair = ProfPlusConstants.RESOURCE_TYPES.first { it.second == label }
                    }
                )
                DropdownSelector("Matière", subject, ProfPlusConstants.SUBJECTS, { subject = it })
                DropdownSelector("Niveau", level, ProfPlusConstants.SCHOOL_LEVELS, { level = it })
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Résumé / objectifs") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = if (contentBody.startsWith("base64:")) "Document joint ($fileName)" else contentBody,
                    onValueChange = { if (!it.startsWith("Document joint")) contentBody = it },
                    label = { Text("Contenu du cours / fiche / exercices") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("resource_body_input")
                )
                OutlinedButton(
                    onClick = { docPicker.launch(arrayOf("application/pdf", "text/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (fileName.isBlank()) "Joindre un fichier (PDF/Texte ≤ 280 Ko)" else fileName)
                }
                if (!fileError.isNullOrBlank()) {
                    Text(fileError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val resolvedName = fileName.ifBlank { "${title.trim().take(24).replace(" ", "_")}.txt" }
                    val resolvedBody = contentBody.ifBlank { description }
                    onPublish(
                        title,
                        description,
                        typePair.first,
                        subject,
                        level,
                        resolvedName,
                        mimeType,
                        if (fileSize > 0) fileSize else resolvedBody.toByteArray().size.toLong(),
                        resolvedBody
                    )
                },
                enabled = title.trim().length >= 2 && (description.isNotBlank() || contentBody.isNotBlank()),
                modifier = Modifier.testTag("resource_publish_btn")
            ) {
                Text("Publier")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
