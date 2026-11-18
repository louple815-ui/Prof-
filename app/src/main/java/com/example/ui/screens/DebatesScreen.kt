package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.data.model.Debate
import com.example.data.model.Poll
import com.example.data.model.ProfPlusConstants
import com.example.data.model.UserPublicProfile
import com.example.ui.auth.DropdownSelector
import com.example.ui.components.ProfPlusEmptyState
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.ProfessionalBlue
import com.example.ui.viewmodel.DebateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebatesScreen(
    currentUser: UserPublicProfile,
    debateViewModel: DebateViewModel,
    showCreateDialog: Boolean,
    onDismissCreateDialog: () -> Unit
) {
    val context = LocalContext.current
    val debatesState by debateViewModel.debatesState.collectAsStateWithLifecycle()
    val polls by debateViewModel.polls.collectAsStateWithLifecycle()
    val userVotes by debateViewModel.userVotes.collectAsStateWithLifecycle()
    val selectedCategory by debateViewModel.selectedCategory.collectAsStateWithLifecycle()
    val debateArguments by debateViewModel.debateArguments.collectAsStateWithLifecycle()

    var activeDebate by remember { mutableStateOf<Debate?>(null) }

    val categories = remember { listOf("Toutes") + ProfPlusConstants.DEBATE_CATEGORIES }
    val pollByDebateId = remember(polls) { polls.associateBy { it.debateId } }
    val votedPollMap = remember(userVotes) { userVotes.associateBy { it.pollId } }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { debateViewModel.selectedCategory.value = cat },
                        label = { Text(cat) }
                    )
                }
            }

            when (val state = debatesState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    ProfPlusEmptyState("Erreur chargement des débats", state.message)
                }
                is UiState.Success -> {
                    val filtered = remember(state.data, selectedCategory) {
                        state.data.filter { selectedCategory == "Toutes" || it.category == selectedCategory }
                    }
                    if (filtered.isEmpty()) {
                        ProfPlusEmptyState(
                            title = "Aucun débat dans cette catégorie",
                            subtitle = "Lancez un débat constructif ou un sondage pour recueillir l'avis des enseignants."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filtered, key = { it.id }) { debate ->
                                val poll = pollByDebateId[debate.id]
                                val userVote = poll?.let { votedPollMap[it.id] }
                                DebateCard(
                                    debate = debate,
                                    poll = poll,
                                    userVotedIndex = userVote?.optionIndex?.toInt(),
                                    onVote = { optionIdx ->
                                        if (poll != null) debateViewModel.voteInPoll(poll, optionIdx)
                                    },
                                    onOpenArguments = {
                                        debateViewModel.selectDebate(debate.id)
                                        activeDebate = debate
                                    },
                                    onShare = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "Débat prof+ : ${debate.title}\n${debate.question}")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Partager le débat"))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateDebateDialog(
            onDismiss = onDismissCreateDialog,
            onCreate = { title, question, category, pollQuestion, options ->
                debateViewModel.createDebate(currentUser, title, question, category, pollQuestion, options)
                onDismissCreateDialog()
            }
        )
    }

    activeDebate?.let { debate ->
        ModalBottomSheet(
            onDismissRequest = {
                activeDebate = null
                debateViewModel.selectDebate(null)
            }
        ) {
            CommentsSheetContent(
                title = "Arguments & Points de vue (${debateArguments.size})",
                comments = debateArguments,
                onSendComment = { text, parentId ->
                    debateViewModel.addArgument(debate, currentUser, text, parentId)
                }
            )
        }
    }
}

@Composable
private fun DebateCard(
    debate: Debate,
    poll: Poll?,
    userVotedIndex: Int?,
    onVote: (Int) -> Unit,
    onOpenArguments: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(displayName = debate.authorName, verified = debate.authorVerified, size = 36.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(debate.authorName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        formatTimestamp(debate.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        debate.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(debate.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(debate.question, style = MaterialTheme.typography.bodyMedium)

            if (poll != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Poll, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(poll.question, style = MaterialTheme.typography.labelLarge)
                        }
                        poll.options.forEachIndexed { index, optionText ->
                            val count = poll.optionCounts.getOrElse(index) { 0L }
                            val ratio = if (poll.totalVotes > 0L) count.toFloat() / poll.totalVotes.toFloat() else 0f
                            val isSelected = userVotedIndex == index

                            Surface(
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = userVotedIndex == null) { onVote(index) }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Filled.CheckCircle,
                                                    contentDescription = "Votre vote",
                                                    tint = ProfessionalBlue,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                            }
                                            Text(optionText, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        if (userVotedIndex != null) {
                                            Text(
                                                "${(ratio * 100).toInt()}% ($count)",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    if (userVotedIndex != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { ratio },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(50))
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = "${poll.totalVotes} vote(s) • ${if (userVotedIndex == null) "Touchez une option pour voter" else "Résultats en direct"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onOpenArguments) {
                    Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Participer (${debate.argumentsCount})")
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = "Partager")
                }
            }
        }
    }
}

@Composable
private fun CreateDebateDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, question: String, category: String, pollQuestion: String?, pollOptions: List<String>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ProfPlusConstants.DEBATE_CATEGORIES.first()) }
    var includePoll by remember { mutableStateOf(false) }
    var pollQuestion by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lancer un débat") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                DropdownSelector("Catégorie", category, ProfPlusConstants.DEBATE_CATEGORIES, { category = it })
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre du débat *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("debate_title_input")
                )
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Question / Problématique *") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("debate_question_input")
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includePoll, onCheckedChange = { includePoll = it })
                    Text("Ajouter un sondage")
                }
                if (includePoll) {
                    OutlinedTextField(
                        value = pollQuestion,
                        onValueChange = { pollQuestion = it },
                        label = { Text("Question du sondage") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    options.forEachIndexed { idx, value ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { options[idx] = it },
                            label = { Text("Option ${idx + 1}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (options.size < 6) {
                        OutlinedButton(onClick = { options.add("") }) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ajouter une option")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        title, question, category,
                        if (includePoll) pollQuestion else null,
                        if (includePoll) options.toList() else emptyList()
                    )
                },
                enabled = title.trim().length >= 3 && question.trim().length >= 5,
                modifier = Modifier.testTag("debate_submit_btn")
            ) {
                Text("Publier")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
