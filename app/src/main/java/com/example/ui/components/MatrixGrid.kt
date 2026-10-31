package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CotisationStatus
import com.example.model.User
import com.example.ui.MemberCotisationStatus
import com.example.ui.theme.*

@Composable
fun MatrixGrid(
    matrix: List<MemberCotisationStatus>,
    currentUser: User,
    onUploadProofClick: () -> Unit,
    onViewProofClick: (String) -> Unit,
    isPendingLaunch: Boolean = false,
    modifier: Modifier = Modifier
) {
    val validatedCount = matrix.count { it.status == CotisationStatus.VALIDATED }
    val pendingCount = matrix.count { it.status == CotisationStatus.PENDING }
    val unpaidCount = matrix.count { it.status == CotisationStatus.UNPAID }

    Column(modifier = modifier.fillMaxWidth()) {
        // Section Title & Stats summary pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Matrice des Cotisations",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            if (isPendingLaunch) {
                Surface(
                    color = AmberGold.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Lancement en attente",
                        color = AmberGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatPill(count = validatedCount, color = StatusValidatedGreen, label = "Validé")
                    StatPill(count = pendingCount, color = StatusPendingYellow, label = "Attente")
                    StatPill(count = unpaidCount, color = StatusUnpaidRed, label = "Retard")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Member Cotisation Cards
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            matrix.forEach { item ->
                val isSelf = item.member.userId == currentUser.id

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("member_matrix_card_${item.member.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelf) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = if (isSelf) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Avatar + Info
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (item.status) {
                                            CotisationStatus.VALIDATED -> StatusValidatedGreen.copy(alpha = 0.15f)
                                            CotisationStatus.PENDING -> StatusPendingYellow.copy(alpha = 0.15f)
                                            CotisationStatus.UNPAID -> StatusUnpaidRed.copy(alpha = 0.15f)
                                            CotisationStatus.LATE_UNPAID -> StatusUnpaidRed.copy(alpha = 0.25f)
                                            CotisationStatus.BENEFICIARY_EXEMPT -> AmberGold.copy(alpha = 0.2f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.member.fullName.take(2).uppercase(),
                                    color = when (item.status) {
                                        CotisationStatus.VALIDATED -> StatusValidatedGreen
                                        CotisationStatus.PENDING -> StatusPendingYellow
                                        CotisationStatus.UNPAID -> StatusUnpaidRed
                                        CotisationStatus.LATE_UNPAID -> StatusUnpaidRed
                                        CotisationStatus.BENEFICIARY_EXEMPT -> AmberGold
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                val allMembers = matrix.map { it.member }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.member.getPartDisplayName(allMembers),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    if (isSelf) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "Moi",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = if (item.transaction != null) {
                                        "${item.transaction.extractedData.provider} • Réf: ${item.transaction.extractedData.transactionId.take(12)}..."
                                    } else {
                                        "Ordre de passage : #${item.member.payoutOrder}"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Right: Status badge or action button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isPendingLaunch) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "En attente",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            } else {
                                StatusBadge(status = item.status)

                                if (isSelf && (item.status == CotisationStatus.UNPAID || item.status == CotisationStatus.LATE_UNPAID)) {
                                    Button(
                                        onClick = onUploadProofClick,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        modifier = Modifier.testTag("upload_my_proof_button")
                                    ) {
                                        Icon(
                                            Icons.Default.FileUpload,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "Payer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
private fun StatPill(count: Int, color: Color, label: String) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$count",
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
