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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
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
import com.example.core.UiState
import com.example.data.model.Group
import com.example.data.model.ProfPlusConstants
import com.example.data.model.UserPublicProfile
import com.example.ui.auth.DropdownSelector
import com.example.ui.components.ProfPlusEmptyState
import com.example.ui.components.RoleChip
import com.example.ui.components.UserAvatar
import com.example.ui.viewmodel.CommunityViewModel

@Composable
fun GroupsScreen(
    currentUser: UserPublicProfile,
    communityViewModel: CommunityViewModel,
    showCreateDialog: Boolean,
    onDismissCreateDialog: () -> Unit
) {
    val groupsState by communityViewModel.publicGroupsState.collectAsStateWithLifecycle()
    val memberships by communityViewModel.myMemberships.collectAsStateWithLifecycle()
    val groupPosts by communityViewModel.selectedGroupPosts.collectAsStateWithLifecycle()
    val groupMembers by communityViewModel.selectedGroupMembers.collectAsStateWithLifecycle()

    var selectedDomain by remember { mutableStateOf("Tous") }
    var activeGroup by remember { mutableStateOf<Group?>(null) }

    val domains = remember { listOf("Tous") + ProfPlusConstants.GROUP_DOMAINS }
    val memberGroupIds = remember(memberships) { memberships.associateBy { it.groupId } }

    if (activeGroup != null) {
        BackHandler {
            activeGroup = null
            communityViewModel.selectGroup(null)
        }
        val group = activeGroup!!
        val membership = memberGroupIds[group.id]
        GroupDetailPane(
            group = group,
            isMember = membership != null && membership.status == "ACTIVE",
            isPending = membership?.status == "PENDING",
            isOwner = group.ownerId == currentUser.userId,
            posts = groupPosts,
            members = groupMembers,
            onBack = {
                activeGroup = null
                communityViewModel.selectGroup(null)
            },
            onJoin = { communityViewModel.joinGroup(group, currentUser) },
            onLeave = { communityViewModel.leaveGroup(group) },
            onPublishPost = { text -> communityViewModel.publishGroupPost(currentUser, group.id, text) }
        )
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(domains) { dom ->
                    FilterChip(
                        selected = selectedDomain == dom,
                        onClick = { selectedDomain = dom },
                        label = { Text(dom) }
                    )
                }
            }

            when (val state = groupsState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> ProfPlusEmptyState("Erreur chargement des groupes", state.message)
                is UiState.Success -> {
                    val filtered = remember(state.data, selectedDomain) {
                        state.data.filter { selectedDomain == "Tous" || it.domain == selectedDomain }
                    }
                    if (filtered.isEmpty()) {
                        ProfPlusEmptyState(
                            title = "Aucun groupe professionnel",
                            subtitle = "Créez un groupe par matière (Mathématiques, Français, Anglais...), niveau ou région."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filtered, key = { it.id }) { group ->
                                val membership = memberGroupIds[group.id]
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            communityViewModel.selectGroup(group.id)
                                            activeGroup = group
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                Text(
                                                    "${group.domain} • ${group.subject.ifBlank { group.level.ifBlank { group.region } }}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                imageVector = if (group.privacy == "PUBLIC") Icons.Filled.Public else Icons.Filled.Lock,
                                                contentDescription = group.privacy,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (group.description.isNotBlank()) {
                                            Spacer(Modifier.height(8.dp))
                                            Text(group.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("${group.membersCount} membre(s)", style = MaterialTheme.typography.labelMedium)
                                            if (membership == null) {
                                                OutlinedButton(onClick = { communityViewModel.joinGroup(group, currentUser) }) {
                                                    Text(if (group.privacy == "PUBLIC") "Rejoindre" else "Demander")
                                                }
                                            } else {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(50)
                                                ) {
                                                    Text(
                                                        if (membership.status == "ACTIVE") "Membre" else "En attente",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = onDismissCreateDialog,
            onCreate = { name, desc, domain, subject, level, region, privacy, rules ->
                communityViewModel.createGroup(currentUser, name, desc, domain, subject, level, region, privacy, rules)
                onDismissCreateDialog()
            }
        )
    }
}

@Composable
private fun GroupDetailPane(
    group: Group,
    isMember: Boolean,
    isPending: Boolean,
    isOwner: Boolean,
    posts: List<com.example.data.model.Post>,
    members: List<com.example.data.model.GroupMember>,
    onBack: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onPublishPost: (String) -> Unit
) {
    var postDraft by remember { mutableStateOf("") }

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
                    Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${group.membersCount} membre(s) • ${group.privacy}", style = MaterialTheme.typography.bodySmall)
                }
                if (!isOwner) {
                    if (isMember) {
                        TextButton(onClick = onLeave) { Text("Quitter") }
                    } else if (!isPending) {
                        Button(onClick = onJoin) { Text("Rejoindre") }
                    }
                }
            }
            HorizontalDivider()

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Responsable : ${group.ownerName}", style = MaterialTheme.typography.labelLarge)
                            if (group.description.isNotBlank()) Text(group.description, style = MaterialTheme.typography.bodyMedium)
                            if (group.rules.isNotBlank()) {
                                Text("Règles du groupe : ${group.rules}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (isMember || isOwner) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = postDraft,
                                onValueChange = { postDraft = it },
                                placeholder = { Text("Publier dans ${group.name}...") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (postDraft.isNotBlank()) {
                                        onPublishPost(postDraft)
                                        postDraft = ""
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Publier")
                            }
                        }
                    }
                }

                item {
                    Text("Membres (${members.size})", style = MaterialTheme.typography.titleSmall)
                }
                items(members.take(8), key = { it.id }) { member ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        UserAvatar(displayName = member.userName, size = 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(member.userName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        RoleChip(member.userRole)
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Publications du groupe (${posts.size})", style = MaterialTheme.typography.titleSmall)
                }
                items(posts, key = { it.id }) { post ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(post.authorName, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(post.content, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String, domain: String, subject: String, level: String, region: String, privacy: String, rules: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf(ProfPlusConstants.GROUP_DOMAINS.first()) }
    var subject by remember { mutableStateOf(ProfPlusConstants.SUBJECTS.first()) }
    var level by remember { mutableStateOf(ProfPlusConstants.SCHOOL_LEVELS[2]) }
    var region by remember { mutableStateOf(ProfPlusConstants.BURKINA_REGIONS.first()) }
    var privacy by remember { mutableStateOf("PUBLIC") }
    var rules by remember { mutableStateOf("Respect mutuel et partage pédagogique constructif.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Créer un groupe professionnel") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du groupe *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("group_name_input")
                )
                DropdownSelector("Type de groupe", domain, ProfPlusConstants.GROUP_DOMAINS, { domain = it })
                DropdownSelector("Matière", subject, ProfPlusConstants.SUBJECTS, { subject = it })
                DropdownSelector("Niveau", level, ProfPlusConstants.SCHOOL_LEVELS, { level = it })
                DropdownSelector("Région", region, ProfPlusConstants.BURKINA_REGIONS, { region = it })
                DropdownSelector("Confidentialité", privacy, listOf("PUBLIC", "PRIVATE", "INVITE_ONLY"), { privacy = it })
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rules,
                    onValueChange = { rules = it },
                    label = { Text("Règles du groupe") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description, domain, subject, level, region, privacy, rules) },
                enabled = name.trim().length >= 2,
                modifier = Modifier.testTag("group_submit_btn")
            ) {
                Text("Créer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
