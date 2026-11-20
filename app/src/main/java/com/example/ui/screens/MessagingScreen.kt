package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.MediaUtils
import com.example.core.UiState
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.UserPublicProfile
import com.example.ui.components.ProfPlusEmptyState
import com.example.ui.components.RoleChip
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.NavyPrimary
import com.example.ui.viewmodel.CommunicationViewModel
import com.example.ui.viewmodel.FeedViewModel
import com.example.ui.viewmodel.SessionViewModel

@Composable
fun MessagingScreen(
    currentUser: UserPublicProfile,
    blockedUserIds: List<String>,
    directory: List<UserPublicProfile>,
    communicationViewModel: CommunicationViewModel,
    sessionViewModel: SessionViewModel,
    feedViewModel: FeedViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val conversationsState by communicationViewModel.conversationsState.collectAsStateWithLifecycle()
    val activeConvId by communicationViewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by communicationViewModel.activeMessages.collectAsStateWithLifecycle()

    var showNewChatDialog by remember { mutableStateOf(false) }
    var showGroupChatDialog by remember { mutableStateOf(false) }
    var reportingUserId by remember { mutableStateOf<String?>(null) }

    val conversations = (conversationsState as? UiState.Success)?.data.orEmpty()
    val activeConversation = conversations.firstOrNull { it.id == activeConvId }

    if (activeConversation != null) {
        BackHandler { communicationViewModel.openConversation(null) }
        val otherIds = activeConversation.participantIds.filter { it != currentUser.userId }
        ChatThreadPane(
            conversation = activeConversation,
            currentUser = currentUser,
            messages = messages,
            otherParticipantId = otherIds.firstOrNull(),
            isOtherBlocked = otherIds.firstOrNull()?.let { it in blockedUserIds } == true,
            onBack = { communicationViewModel.openConversation(null) },
            onSendMessage = { text, attName, attData ->
                communicationViewModel.sendMessage(activeConversation, currentUser.displayName, text, attName, attData)
            },
            onBlockOther = { targetId -> sessionViewModel.toggleBlockUser(targetId) },
            onReportConversation = { reportingUserId = activeConversation.id }
        )
        reportingUserId?.let { targetId ->
            ReportDialog(
                onDismiss = { reportingUserId = null },
                onSubmit = { reason, details ->
                    feedViewModel.reportContent(currentUser.displayName, "MESSAGE", targetId, reason, details)
                    reportingUserId = null
                }
            )
        }
        return
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
                Text("Messagerie", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { showGroupChatDialog = true }, modifier = Modifier.testTag("new_group_chat_btn")) {
                    Icon(Icons.Filled.GroupAdd, contentDescription = "Discussion de groupe")
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChatDialog = true },
                containerColor = NavyPrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("new_direct_chat_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nouveau message")
            }
        }
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                when (val state = conversationsState) {
                    is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    is UiState.Error -> ProfPlusEmptyState("Erreur messagerie", state.message)
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            ProfPlusEmptyState(
                                title = "Aucune discussion",
                                subtitle = "Démarrez une conversation directe ou en groupe avec des collègues."
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(state.data, key = { it.id }) { conv ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { communicationViewModel.openConversation(conv.id) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(14.dp)
                                        ) {
                                            val displayTitle = conv.title.ifBlank { conv.participantSummary }
                                            UserAvatar(displayName = displayTitle, size = 44.dp)
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    displayTitle,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    conv.lastMessage.ifBlank { "Nouvelle discussion" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                formatTimestamp(conv.lastMessageAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    }

    if (showNewChatDialog) {
        val candidates = remember(directory, currentUser.userId, blockedUserIds) {
            directory.filter { it.userId != currentUser.userId && it.userId !in blockedUserIds }
        }
        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            title = { Text("Contacter un collègue") },
            text = {
                if (candidates.isEmpty()) {
                    Text("Aucun autre enseignant disponible pour le moment.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(candidates, key = { it.userId }) { target ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showNewChatDialog = false
                                        communicationViewModel.startDirectConversation(currentUser, target) {}
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                UserAvatar(
                                    displayName = target.displayName,
                                    photoData = target.resolvedPhotoSource(),
                                    verified = target.verified,
                                    size = 38.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(target.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text("${target.subject} • ${target.city}", style = MaterialTheme.typography.bodySmall)
                                }
                                RoleChip(target.role)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNewChatDialog = false }) { Text("Fermer") }
            }
        )
    }

    if (showGroupChatDialog) {
        val candidates = remember(directory, currentUser.userId, blockedUserIds) {
            directory.filter { it.userId != currentUser.userId && it.userId !in blockedUserIds }
        }
        var title by remember { mutableStateOf("") }
        val selected = remember { mutableStateListOf<UserPublicProfile>() }

        AlertDialog(
            onDismissRequest = { showGroupChatDialog = false },
            title = { Text("Nouvelle discussion de groupe") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Nom de la discussion *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(modifier = Modifier.height(220.dp)) {
                        items(candidates, key = { it.userId }) { target ->
                            val isChecked = selected.any { it.userId == target.userId }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) selected.removeAll { it.userId == target.userId }
                                        else selected.add(target)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) selected.add(target)
                                        else selected.removeAll { it.userId == target.userId }
                                    }
                                )
                                Text(target.displayName, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        communicationViewModel.startGroupConversation(currentUser, title, selected.toList()) {}
                        showGroupChatDialog = false
                    },
                    enabled = title.isNotBlank() && selected.isNotEmpty()
                ) {
                    Text("Créer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGroupChatDialog = false }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun ChatThreadPane(
    conversation: Conversation,
    currentUser: UserPublicProfile,
    messages: List<ChatMessage>,
    otherParticipantId: String?,
    isOtherBlocked: Boolean,
    onBack: () -> Unit,
    onSendMessage: (text: String, attName: String, attData: String) -> Unit,
    onBlockOther: (String) -> Unit,
    onReportConversation: () -> Unit
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var attName by remember { mutableStateOf("") }
    var attData by remember { mutableStateOf("") }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val doc = MediaUtils.inspectUriMeta(context, uri, MediaUtils.MAX_ATTACHMENT_UPLOAD_BYTES)
            if (doc == null) {
                attachmentError = "Impossible de lire ce fichier."
            } else if (doc.isTooLarge) {
                attachmentError = "Fichier trop volumineux (max 10 Mo)."
            } else {
                attachmentError = null
                attName = doc.fileName
                attData = uri.toString()
            }
        }
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        conversation.title.ifBlank { conversation.participantSummary },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(conversation.type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (conversation.type == "DIRECT" && otherParticipantId != null) {
                            DropdownMenuItem(
                                text = { Text(if (isOtherBlocked) "Débloquer" else "Bloquer cet utilisateur") },
                                leadingIcon = { Icon(Icons.Filled.Block, null) },
                                onClick = { menuOpen = false; onBlockOther(otherParticipantId) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Signaler la conversation") },
                            leadingIcon = { Icon(Icons.Filled.Flag, null) },
                            onClick = { menuOpen = false; onReportConversation() }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                HorizontalDivider()
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isMe = msg.senderId == currentUser.userId
                        Row(
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = if (isMe) NavyPrimary else MaterialTheme.colorScheme.surface,
                                contentColor = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface,
                                shape = RoundedCornerShape(14.dp),
                                tonalElevation = 1.dp,
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    if (!isMe) {
                                        Text(
                                            msg.senderName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(Modifier.height(2.dp))
                                    }
                                    if (msg.text.isNotBlank()) {
                                        Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (msg.attachmentUrl.startsWith("https://") && msg.attachmentMimeType.startsWith("image/")) {
                                        Spacer(Modifier.height(6.dp))
                                        AsyncImage(
                                            model = msg.attachmentUrl,
                                            contentDescription = msg.attachmentName.ifBlank { "Image jointe" },
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(160.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                    }
                                    if (msg.attachmentName.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "📎 ${msg.attachmentName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = if (msg.attachmentUrl.startsWith("https://")) {
                                                Modifier.clickable {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(msg.attachmentUrl)))
                                                }
                                            } else Modifier
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!attachmentError.isNullOrBlank()) {
                    Text(
                        text = attachmentError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                if (attName.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text("Pièce jointe : $attName", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { attName = ""; attData = "" }) { Text("Retirer") }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    IconButton(onClick = { docPicker.launch(arrayOf("application/pdf", "text/*", "image/*")) }) {
                        Icon(Icons.Filled.AttachFile, contentDescription = "Joindre un fichier")
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Écrire un message...") },
                        modifier = Modifier.weight(1f).testTag("chat_message_input")
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (draft.isNotBlank() || attName.isNotBlank()) {
                                onSendMessage(draft, attName, attData)
                                draft = ""
                                attName = ""
                                attData = ""
                            }
                        },
                        modifier = Modifier.testTag("chat_send_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer")
                    }
                }
            }
        }
    }
}
