package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.LocalAppCopy
import com.example.ui.components.ProfPlusEmptyState
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.GoldAccent
import com.example.ui.viewmodel.SessionViewModel

@Composable
fun NotificationsScreen(
    sessionViewModel: SessionViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val copy = LocalAppCopy.current
    val notifications by sessionViewModel.notifications.collectAsStateWithLifecycle()

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
                Text(copy.notifications, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (notifications.any { !it.read }) {
                    TextButton(
                        onClick = { sessionViewModel.markAllNotificationsRead() },
                        modifier = Modifier.testTag("mark_all_read_btn")
                    ) {
                        Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (copy.lang == "en") "Read all" else "Tout lire")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                if (notifications.isEmpty()) {
                    ProfPlusEmptyState(
                        title = if (copy.lang == "en") "No notifications yet" else "Aucune notification",
                        subtitle = if (copy.lang == "en") {
                            "Comments, replies, mentions, messages, and official announcements appear here."
                        } else {
                            "Les commentaires, réponses, mentions, messages et annonces officielles apparaissent ici."
                        }
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(notifications, key = { it.id }) { notif ->
                            val containerColor = if (notif.read) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = containerColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!notif.read) sessionViewModel.markNotificationRead(notif.id)
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Icon(
                                        imageVector = if (notif.read) Icons.Filled.NotificationsNone else Icons.Filled.NotificationsActive,
                                        contentDescription = null,
                                        tint = if (notif.read) MaterialTheme.colorScheme.onSurfaceVariant else GoldAccent,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                notif.title.ifBlank { notif.type },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = if (notif.read) FontWeight.Medium else FontWeight.Bold
                                            )
                                            Text(
                                                formatTimestamp(notif.createdAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (notif.body.isNotBlank()) {
                                            Text(
                                                notif.body,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(top = 4.dp)
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
