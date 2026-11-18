package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.MediaUtils
import com.example.core.UiState
import com.example.data.model.Bookmark
import com.example.data.model.Comment
import com.example.data.model.Post
import com.example.data.model.ProfPlusConstants
import com.example.data.model.UserPublicProfile
import com.example.ui.auth.DropdownSelector
import com.example.ui.components.ProfPlusEmptyState
import com.example.ui.components.RoleChip
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.ProfessionalBlue
import com.example.ui.viewmodel.FeedSortMode
import com.example.ui.viewmodel.FeedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    currentUser: UserPublicProfile,
    blockedUserIds: List<String>,
    bookmarks: List<Bookmark>,
    feedViewModel: FeedViewModel,
    showCreateDialog: Boolean,
    onDismissCreateDialog: () -> Unit,
    onToggleBookmark: (Post, Boolean) -> Unit
) {
    val context = LocalContext.current
    val postsState by feedViewModel.postsState.collectAsStateWithLifecycle()
    val sortMode by feedViewModel.sortMode.collectAsStateWithLifecycle()
    val selectedCategory by feedViewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by feedViewModel.searchQuery.collectAsStateWithLifecycle()
    val comments by feedViewModel.activeComments.collectAsStateWithLifecycle()

    var activePostForComments by remember { mutableStateOf<Post?>(null) }
    var editingPost by remember { mutableStateOf<Post?>(null) }
    var reportingPost by remember { mutableStateOf<Post?>(null) }

    val categories = remember { listOf("Toutes") + ProfPlusConstants.POST_CATEGORIES }
    val bookmarkedIds = remember(bookmarks) { bookmarks.map { it.targetId }.toSet() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { feedViewModel.searchQuery.value = it },
                placeholder = { Text("Rechercher une publication, un enseignant...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("feed_search_input")
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                FilterChip(
                    selected = sortMode == FeedSortMode.RECENT,
                    onClick = { feedViewModel.sortMode.value = FeedSortMode.RECENT },
                    label = { Text("Récents") },
                    modifier = Modifier.testTag("sort_recent_chip")
                )
                FilterChip(
                    selected = sortMode == FeedSortMode.PERTINENCE,
                    onClick = { feedViewModel.sortMode.value = FeedSortMode.PERTINENCE },
                    label = { Text("Pertinence") },
                    modifier = Modifier.testTag("sort_relevance_chip")
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { feedViewModel.selectedCategory.value = category },
                        label = { Text(category) }
                    )
                }
            }

            when (val state = postsState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    ProfPlusEmptyState(
                        title = "Impossible de charger le fil",
                        subtitle = state.message
                    )
                }
                is UiState.Success -> {
                    val filtered = remember(state.data, selectedCategory, searchQuery, sortMode, blockedUserIds) {
                        val base = state.data
                            .filter { it.authorId !in blockedUserIds }
                            .filter { selectedCategory == "Toutes" || it.category == selectedCategory }
                            .filter {
                                searchQuery.isBlank() ||
                                    it.content.contains(searchQuery, ignoreCase = true) ||
                                    it.authorName.contains(searchQuery, ignoreCase = true)
                            }
                        if (sortMode == FeedSortMode.PERTINENCE) {
                            base.sortedByDescending { (it.likesCount * 2) + (it.commentsCount * 3) + it.sharesCount }
                        } else {
                            base.sortedByDescending { it.createdAt?.seconds ?: 0L }
                        }
                    }

                    if (filtered.isEmpty()) {
                        ProfPlusEmptyState(
                            title = "Aucune publication pour le moment",
                            subtitle = "Partagez une expérience de classe, une fiche ou une réflexion avec vos collègues."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filtered, key = { it.id }) { post ->
                                val isBookmarked = post.id in bookmarkedIds
                                PostCard(
                                    post = post,
                                    isAuthor = post.authorId == currentUser.userId,
                                    isBookmarked = isBookmarked,
                                    onLike = { feedViewModel.toggleReaction(post, currentUser.displayName) },
                                    onOpenComments = {
                                        feedViewModel.selectPostForComments(post.id)
                                        activePostForComments = post
                                    },
                                    onShare = {
                                        feedViewModel.sharePost(post)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "${post.authorName} sur prof+ :\n\n${post.content}")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Partager via"))
                                    },
                                    onToggleBookmark = { onToggleBookmark(post, isBookmarked) },
                                    onEdit = { editingPost = post },
                                    onDelete = { feedViewModel.deletePost(post.id) },
                                    onReport = { reportingPost = post }
                                )
                            }
                            item {
                                OutlinedButton(
                                    onClick = { feedViewModel.loadMore() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .testTag("feed_load_more_btn")
                                ) {
                                    Text("Charger plus de publications")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePostDialog(
            onDismiss = onDismissCreateDialog,
            onPublish = { content, category, imageData, docName, docType ->
                feedViewModel.createPost(currentUser, content, category, imageData, docName, docType)
                onDismissCreateDialog()
            }
        )
    }

    editingPost?.let { post ->
        EditPostDialog(
            post = post,
            onDismiss = { editingPost = null },
            onSave = { content, category ->
                feedViewModel.updatePost(post.id, content, category)
                editingPost = null
            }
        )
    }

    reportingPost?.let { post ->
        ReportDialog(
            onDismiss = { reportingPost = null },
            onSubmit = { reason, details ->
                feedViewModel.reportContent(currentUser.displayName, "POST", post.id, reason, details)
                reportingPost = null
            }
        )
    }

    activePostForComments?.let { post ->
        ModalBottomSheet(
            onDismissRequest = {
                activePostForComments = null
                feedViewModel.selectPostForComments(null)
            }
        ) {
            CommentsSheetContent(
                title = "Commentaires (${comments.size})",
                comments = comments,
                onSendComment = { text, parentId ->
                    feedViewModel.addComment(post, currentUser, text, parentId)
                }
            )
        }
    }
}

@Composable
fun PostCard(
    post: Post,
    isAuthor: Boolean,
    isBookmarked: Boolean,
    onLike: () -> Unit,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onToggleBookmark: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val imageBitmap = remember(post.imageData) { MediaUtils.decodeDataUriToBitmap(post.imageData) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                UserAvatar(displayName = post.authorName, verified = post.authorVerified, size = 42.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(post.authorName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RoleChip(post.authorRole)
                        Text(
                            text = formatTimestamp(post.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (isAuthor) {
                            DropdownMenuItem(
                                text = { Text("Modifier") },
                                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                onClick = { menuExpanded = false; onEdit() }
                            )
                            DropdownMenuItem(
                                text = { Text("Supprimer") },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = { menuExpanded = false; onDelete() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Signaler") },
                            leadingIcon = { Icon(Icons.Filled.Flag, null) },
                            onClick = { menuExpanded = false; onReport() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = post.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(post.content, style = MaterialTheme.typography.bodyLarge)

            if (imageBitmap != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Image(
                    bitmap = imageBitmap.asImageBitmap(),
                    contentDescription = "Image de la publication",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            if (post.attachmentName.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, tint = ProfessionalBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(post.attachmentName, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onLike) {
                    Icon(Icons.Filled.ThumbUp, contentDescription = "Réagir", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${post.likesCount}")
                }
                TextButton(onClick = onOpenComments) {
                    Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = "Commenter", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${post.commentsCount}")
                }
                TextButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = "Partager", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${post.sharesCount}")
                }
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "Favori",
                        tint = if (isBookmarked) GoldAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CommentsSheetContent(
    title: String,
    comments: List<Comment>,
    onSendComment: (content: String, parentId: String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var replyToId by remember { mutableStateOf("") }
    var replyToAuthor by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            if (comments.isEmpty()) {
                item {
                    Text(
                        "Soyez le premier à commenter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(comments, key = { it.id }) { comment ->
                    val isReply = comment.parentCommentId.isNotBlank()
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = if (isReply) 20.dp else 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(comment.authorName, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    formatTimestamp(comment.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(comment.content, style = MaterialTheme.typography.bodyMedium)
                            TextButton(
                                onClick = {
                                    replyToId = comment.id
                                    replyToAuthor = comment.authorName
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Répondre", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        if (replyToAuthor.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Réponse à $replyToAuthor", style = MaterialTheme.typography.labelSmall, color = ProfessionalBlue)
                TextButton(onClick = { replyToId = ""; replyToAuthor = "" }) {
                    Text("Annuler", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Votre commentaire...") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        onSendComment(input, replyToId)
                        input = ""
                        replyToId = ""
                        replyToAuthor = ""
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer")
            }
        }
    }
}

@Composable
private fun CreatePostDialog(
    onDismiss: () -> Unit,
    onPublish: (content: String, category: String, imageData: String, docName: String, docType: String) -> Unit
) {
    val context = LocalContext.current
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ProfPlusConstants.POST_CATEGORIES.first()) }
    var imageData by remember { mutableStateOf("") }
    var docName by remember { mutableStateOf("") }
    var docType by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) imageData = MediaUtils.encodeImageUriToDataUri(context, uri).orEmpty()
    }
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val doc = MediaUtils.readDocumentFromUri(context, uri)
            if (doc != null) {
                docName = doc.fileName
                docType = doc.mimeType
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle publication") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DropdownSelector(
                    label = "Catégorie",
                    selected = category,
                    options = ProfPlusConstants.POST_CATEGORIES,
                    onSelect = { category = it }
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Partagez avec vos collègues...") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_post_content_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Icon(Icons.Filled.Image, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (imageData.isBlank()) "Image" else "Image prête")
                    }
                    OutlinedButton(
                        onClick = { docPicker.launch(arrayOf("application/pdf", "text/*")) }
                    ) {
                        Icon(Icons.Filled.AttachFile, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (docName.isBlank()) "Document" else docName.take(12))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (content.isNotBlank()) onPublish(content, category, imageData, docName, docType) },
                enabled = content.isNotBlank(),
                modifier = Modifier.testTag("publish_post_confirm_btn")
            ) {
                Text("Publier")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun EditPostDialog(
    post: Post,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var content by remember { mutableStateOf(post.content) }
    var category by remember { mutableStateOf(post.category) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier la publication") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DropdownSelector("Catégorie", category, ProfPlusConstants.POST_CATEGORIES, { category = it })
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (content.isNotBlank()) onSave(content, category) }) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
fun ReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String) -> Unit
) {
    val reasons = listOf("Contenu inapproprié", "Spam / Publicité", "Atteinte à la vie privée", "Harcèlement", "Fausse information")
    var reason by remember { mutableStateOf(reasons.first()) }
    var details by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Signaler ce contenu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DropdownSelector("Motif", reason, reasons, { reason = it })
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Précisions (facultatif)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(reason, details) }) { Text("Envoyer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
