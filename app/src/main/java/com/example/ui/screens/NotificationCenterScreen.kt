package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppNotification
import com.example.model.NotificationType
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    notifications: List<AppNotification>,
    onBack: () -> Unit,
    onMarkAllAsRead: () -> Unit,
    onMarkAsRead: (String) -> Unit,
    onSimulateNotification: (NotificationType) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    var selectedFilter by remember { mutableStateOf<NotificationType?>(null) }

    val filteredList = remember(notifications, selectedFilter) {
        if (selectedFilter == null) notifications
        else notifications.filter { it.type == selectedFilter }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Notifications",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("notifications_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    if (notifications.any { !it.isRead }) {
                        TextButton(onClick = onMarkAllAsRead) {
                            Text(
                                "Tout marquer comme lu",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(2.dp))
                // Filter chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("Toutes (${notifications.size})", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == NotificationType.VALIDATION,
                        onClick = { selectedFilter = NotificationType.VALIDATION },
                        label = { Text("Paiements", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == NotificationType.DEADLINE,
                        onClick = { selectedFilter = NotificationType.DEADLINE },
                        label = { Text("Échéances", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == NotificationType.PAYOUT_TURN,
                        onClick = { selectedFilter = NotificationType.PAYOUT_TURN },
                        label = { Text("Cagnottes", fontSize = 11.sp) }
                    )
                }
            }

            if (filteredList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.NotificationsNone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Aucune notification pour le moment",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { notif ->
                    val (icon, iconColor, bgIconColor) = when (notif.type) {
                        NotificationType.VALIDATION -> Triple(
                            Icons.Default.CheckCircle,
                            StatusValidatedGreen,
                            StatusValidatedGreen.copy(alpha = 0.15f)
                        )
                        NotificationType.DEADLINE -> Triple(
                            Icons.Default.AccessTime,
                            StatusUnpaidRed,
                            StatusUnpaidRed.copy(alpha = 0.15f)
                        )
                        NotificationType.PAYOUT_TURN -> Triple(
                            Icons.Default.Star,
                            AmberGold,
                            AmberGold.copy(alpha = 0.15f)
                        )
                        NotificationType.INVITATION -> Triple(
                            Icons.Default.GroupAdd,
                            WaveCyan,
                            WaveCyan.copy(alpha = 0.15f)
                        )
                        NotificationType.SYSTEM -> Triple(
                            Icons.Default.Info,
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMarkAsRead(notif.id) }
                            .testTag("notif_item_${notif.id}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (notif.isRead) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = if (!notif.isRead) androidx.compose.foundation.BorderStroke(1.dp, iconColor.copy(alpha = 0.4f)) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(bgIconColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = notif.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = if (notif.isRead) FontWeight.SemiBold else FontWeight.Bold
                                    )
                                    if (!notif.isRead) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(iconColor)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = notif.message,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // End of notification feed

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
