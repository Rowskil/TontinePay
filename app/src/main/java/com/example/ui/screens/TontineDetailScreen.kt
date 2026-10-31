package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.MemberCotisationStatus
import com.example.ui.components.AppDatePickerModal
import com.example.ui.components.BeneficiaryHeader
import com.example.ui.components.HapticHelper
import com.example.ui.components.MatrixGrid
import com.example.ui.components.QRCodeView
import com.example.ui.components.bounceClick
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TontineDetailScreen(
    tontine: Tontine,
    cycle: TontineCycle?,
    members: List<TontineMember>,
    matrix: List<MemberCotisationStatus>,
    activities: List<ActivityItem>,
    currentUser: User,
    pendingValidationsCount: Int,
    initialTab: Int = 0,
    activeSlotMemberId: String? = null,
    onBack: () -> Unit,
    onUploadProofClick: () -> Unit,
    onOpenReceiverValidation: () -> Unit,
    onAddMember: (name: String, phone: String) -> Unit,
    onInviteByPhone: (phone: String) -> Unit,
    onReorderMember: (memberId: String, newPosition: Int) -> Unit,
    onReorderAllMembers: (orderedMemberIds: List<String>) -> Unit = {},
    onRemoveMember: (memberId: String) -> Unit,
    onDeleteTontine: () -> Unit,
    onLeaveTontine: () -> Unit = {},
    onUpdateSettings: (
        name: String,
        desc: String,
        amount: Double,
        freq: TontineFrequency,
        curr: String,
        startDate: String,
        endDate: String,
        penalty: Double,
        isBeneficiaryExempt: Boolean,
        autoRotation: Boolean,
        isPendingLaunch: Boolean
    ) -> Unit,
    onSelectActiveSlot: (String) -> Unit = {},
    onAddAdditionalSlot: (aliasLabel: String) -> Unit = {},
    onApproveMember: (memberId: String) -> Unit = {},
    onRejectMember: (memberId: String) -> Unit = {},
    onSetCoAdmin: (memberId: String?) -> Unit = {},
    onLaunchTontine: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val mySlots = members.filter { it.userId == currentUser.id }
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) } // 0: Matrice, 1: Ordre de passage, 2: Fil Groupe
    var showInviteDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showAdminSettingsDialog by remember { mutableStateOf(false) }
    var showAddSlotDialog by remember { mutableStateOf(false) }
    var memberToDelete by remember { mutableStateOf<TontineMember?>(null) }
    var memberToReorder by remember { mutableStateOf<TontineMember?>(null) }
    var showDeleteTontineConfirmation by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    var draggingMemberId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var localOrderedMembers by remember(members) {
        mutableStateOf(members.sortedBy { it.payoutOrder })
    }
    LaunchedEffect(members) {
        if (draggingMemberId == null) {
            localOrderedMembers = members.sortedBy { it.payoutOrder }
        }
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val cardHeightPx = with(density) { 76.dp.toPx() }
    val clipboardManager = LocalClipboardManager.current
    val isMainAdmin = tontine.creatorId == currentUser.id
    val isCoAdmin = tontine.coAdminId == currentUser.id
    val isAdmin = isMainAdmin || isCoAdmin

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = tontine.name,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${"%,.0f".format(tontine.contributionAmount).replace(',', ' ')} ${tontine.currency} • ${tontine.frequency.labelFr}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // Admin settings button if admin
                    if (isAdmin) {
                        IconButton(
                            onClick = { showAdminSettingsDialog = true },
                            modifier = Modifier.testTag("action_admin_settings_button")
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Paramètres du groupe (Admin)",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // QR Code / Share Invite button (Feedback B.7: Admin only)
                    if (isAdmin) {
                        IconButton(
                            onClick = { showInviteDialog = true },
                            modifier = Modifier.testTag("action_invite_button")
                        ) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = "Inviter & QR Code",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    // Leave Tontine button (Feedback C.1)
                    IconButton(
                        onClick = { showLeaveDialog = true },
                        modifier = Modifier.testTag("action_leave_tontine_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Quitter la tontine",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // Docked bottom action bar (non-floating, seamlessly anchored with Android 17 edge-to-edge)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("detail_docked_action_bar"),
                tonalElevation = 2.dp,
                shadowElevation = 0.dp
            ) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        thickness = 0.6.dp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeSlot = members.find { it.id == activeSlotMemberId } ?: members.find { it.userId == currentUser.id }
                        val isReceiverForActiveCycle = if (cycle?.beneficiaryMemberId?.isNotBlank() == true) {
                            activeSlot?.id == cycle.beneficiaryMemberId
                        } else {
                            activeSlot?.id == cycle?.beneficiaryId || (currentUser.id == cycle?.beneficiaryId && mySlots.size <= 1)
                        }
                        val activeSlotCotisation = matrix.firstOrNull { it.member.id == activeSlot?.id } 
                            ?: matrix.firstOrNull { it.member.userId == currentUser.id }
                        val hasSubmittedProof = activeSlotCotisation?.status in listOf(CotisationStatus.VALIDATED, CotisationStatus.PENDING)

                        if (tontine.isEffectivelyPending) {
                            Surface(
                                color = AmberGold.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.HourglassTop,
                                        contentDescription = null,
                                        tint = AmberGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Lancement prévu : ${tontine.startDate}",
                                        color = AmberGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            if (isAdmin) {
                                Button(
                                    onClick = onLaunchTontine,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("action_launch_tontine_button")
                                ) {
                                    Text("Lancer 🚀", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        } else if (isReceiverForActiveCycle) {
                            // The receiver does not deposit a proof for their own pot payout
                            Surface(
                                color = AmberGold.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = AmberGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Bénéficiaire du tour (Receveur)",
                                        color = AmberGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else if (hasSubmittedProof) {
                            // Feedback B.1: Hide Déposer une preuve button when proof already submitted
                            val isValidated = activeSlotCotisation?.status == CotisationStatus.VALIDATED
                            Surface(
                                color = if (isValidated) StatusValidatedGreen.copy(alpha = 0.15f) else AmberGold.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        if (isValidated) Icons.Default.CheckCircle else Icons.Default.HourglassTop,
                                        contentDescription = null,
                                        tint = if (isValidated) StatusValidatedGreen else AmberGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isValidated) "Cotisation validée ✓" else "Preuve déposée (En attente)",
                                        color = if (isValidated) StatusValidatedGreen else AmberGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = onUploadProofClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("action_submit_payment_proof_button")
                            ) {
                                Icon(
                                    Icons.Default.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Déposer une preuve",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        if (!tontine.isEffectivelyPending && pendingValidationsCount > 0) {
                            Button(
                                onClick = onOpenReceiverValidation,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberGold,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("action_receiver_validation_button")
                            ) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Validation ($pendingValidationsCount)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 0. Multi-profils / Noms multiples switcher (Feedback B.8)
            val mySlots = members.filter { it.userId == currentUser.id }
            if (mySlots.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("slot_switcher_card"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (mySlots.size > 1) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Badge,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Noms (${mySlots.size}) :",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        mySlots.forEachIndexed { index, slot ->
                                            val isSelected = slot.id == (activeSlotMemberId ?: mySlots.first().id)
                                            Surface(
                                                onClick = { onSelectActiveSlot(slot.id) },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = slot.aliasLabel.ifBlank { "Nom #${index + 1}" },
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Badge,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Part active (1 part)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            TextButton(
                                onClick = { showAddSlotDialog = true },
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                modifier = Modifier.testTag("action_add_slot_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Ajouter un nom", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Pending Member Approvals Card for Admin (Feedback B.2 & E)
            val pendingMembers = members.filter { it.isPendingApproval }
            if (isAdmin && pendingMembers.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("pending_approvals_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = AmberGoldContainer.copy(alpha = 0.9f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AmberGold)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PendingActions, contentDescription = null, tint = AmberGold, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Demandes en attente d'approbation (${pendingMembers.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            pendingMembers.forEach { pending ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pending.displayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = pending.phoneNumber,
                                            fontSize = 11.sp,
                                            color = AmberGoldOnContainer
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FilledTonalButton(
                                            onClick = { onApproveMember(pending.id) },
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = StatusValidatedGreen,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Approuver", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { onRejectMember(pending.id) },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Refuser", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1. Beneficiary Header & Countdown
            item {
                Spacer(modifier = Modifier.height(2.dp))
                BeneficiaryHeader(
                    tontine = tontine,
                    cycle = cycle,
                    currentUser = currentUser,
                    onValidateClick = onOpenReceiverValidation,
                    pendingValidationCount = pendingValidationsCount
                )
            }

            // 2. Schedule & Penalty Notice Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Réception : ${tontine.startDate} au ${tontine.endDate}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (tontine.latePenaltyFee > 0) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Amende retard : +${"%,.0f".format(tontine.latePenaltyFee).replace(',', ' ')} ${tontine.currency}",
                                    color = StatusUnpaidRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (isAdmin) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.clickable { showAdminSettingsDialog = true }
                            ) {
                                Text(
                                    text = "Gérer",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Mobile Money Fee Reminder (Feedback B.9: ONLY if active and not pending launch)
            if (!tontine.isEffectivelyPending) {
                item {
                    Surface(
                        color = AmberGold.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AmberGold.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NB : Veillez ajouter les frais de retrait pour les dépôts Mobile Money 🫶",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 3. Navigation Tabs
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Matrice", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Ordre", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Fil Groupe", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // Matrice Grid
                    item {
                        MatrixGrid(
                            matrix = matrix,
                            currentUser = currentUser,
                            isPendingLaunch = tontine.isEffectivelyPending,
                            onUploadProofClick = onUploadProofClick,
                            onViewProofClick = { }
                        )
                    }
                }

                1 -> {
                    // Ordre officiel & Gestion Admin (User requirement A.1: Titre "Ordre")
                    item {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ordre",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (isAdmin) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AmberGold.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "Mode Admin",
                                            color = AmberGold,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val isPending = tontine.isEffectivelyPending
                    val minOrderAllowed = if (isPending) 1 else (cycle?.cycleNumber ?: 1)
                    val minIndexAllowed = minOrderAllowed - 1

                    items(localOrderedMembers, key = { it.id }) { member ->
                        val index = localOrderedMembers.indexOfFirst { it.id == member.id }
                        val isCurrentBeneficiary = cycle?.beneficiaryId == member.userId
                        val isPassed = (index + 1) < (cycle?.cycleNumber ?: 1)
                        val isLockedFromReordering = if (isPending) false else isPassed
                        val isBeingDragged = member.id == draggingMemberId

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isBeingDragged) 10f else 1f)
                                .graphicsLayer {
                                    translationY = if (isBeingDragged) dragOffsetY else 0f
                                    shadowElevation = if (isBeingDragged) 14.dp.toPx() else 0f
                                    scaleX = if (isBeingDragged) 1.02f else 1f
                                    scaleY = if (isBeingDragged) 1.02f else 1f
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isBeingDragged -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                                    isCurrentBeneficiary -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = when {
                                isBeingDragged -> androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                isCurrentBeneficiary -> androidx.compose.foundation.BorderStroke(1.5.dp, AmberGold)
                                else -> null
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Drag Handle on the FAR LEFT of the card (User requirement A.1)
                                if (isAdmin && !isLockedFromReordering) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clickable { memberToReorder = member }
                                            .pointerInput(member.id, minIndexAllowed) {
                                                detectVerticalDragGestures(
                                                    onDragStart = {
                                                        draggingMemberId = member.id
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragEnd = {
                                                        val finalOrderedIds = localOrderedMembers.map { it.id }
                                                        draggingMemberId = null
                                                        dragOffsetY = 0f
                                                        onReorderAllMembers(finalOrderedIds)
                                                    },
                                                    onDragCancel = {
                                                        val finalOrderedIds = localOrderedMembers.map { it.id }
                                                        draggingMemberId = null
                                                        dragOffsetY = 0f
                                                        onReorderAllMembers(finalOrderedIds)
                                                    },
                                                    onVerticalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffsetY += dragAmount
                                                        val currIdx = localOrderedMembers.indexOfFirst { it.id == draggingMemberId }
                                                        if (currIdx != -1) {
                                                            // Fluid continuous drag upward
                                                            if (dragOffsetY < -cardHeightPx * 0.5f && currIdx > minIndexAllowed) {
                                                                val updated = localOrderedMembers.toMutableList()
                                                                val item = updated.removeAt(currIdx)
                                                                updated.add(currIdx - 1, item)
                                                                localOrderedMembers = updated
                                                                dragOffsetY += cardHeightPx
                                                            }
                                                            // Fluid continuous drag downward
                                                            else if (dragOffsetY > cardHeightPx * 0.5f && currIdx < localOrderedMembers.lastIndex) {
                                                                val updated = localOrderedMembers.toMutableList()
                                                                val item = updated.removeAt(currIdx)
                                                                updated.add(currIdx + 1, item)
                                                                localOrderedMembers = updated
                                                                dragOffsetY -= cardHeightPx
                                                            }
                                                        }
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.DragHandle,
                                            contentDescription = "Glisser ou toucher pour réordonner",
                                            tint = if (isBeingDragged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                // 2. Position Badge (Rank #)
                                val currentOrder = index + 1
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isPassed -> StatusValidatedGreen.copy(alpha = 0.2f)
                                                isCurrentBeneficiary -> AmberGold.copy(alpha = 0.2f)
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "#$currentOrder",
                                        color = when {
                                            isPassed -> StatusValidatedGreen
                                            isCurrentBeneficiary -> AmberGold
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // 3. Member Name, Phone and Admin/Co-Admin Role BELOW phone (User requirement A.3)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = member.getPartDisplayName(members),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = member.phoneNumber,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                    if (member.role == MemberRole.ADMIN) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "🛡️ Admin",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else if (member.role == MemberRole.CO_ADMIN) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "👑 Co-Admin",
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // 4. Status badge
                                Surface(
                                    color = when {
                                        isPassed -> StatusValidatedGreen.copy(alpha = 0.15f)
                                        isCurrentBeneficiary -> AmberGold.copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = when {
                                            isPassed -> "✓ Encaissé"
                                            isCurrentBeneficiary -> "🌟 En cours"
                                            else -> "À venir"
                                        },
                                        color = when {
                                            isPassed -> StatusValidatedGreen
                                            isCurrentBeneficiary -> AmberGold
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                // 5. Secondary Admin actions (Co-Admin toggle and Delete part)
                                if (isAdmin) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Co-Admin toggle button for creator (Feedback B.5)
                                        if (currentUser.id == tontine.creatorId && member.userId != currentUser.id) {
                                            IconButton(
                                                onClick = {
                                                    val newTarget = if (member.role == MemberRole.CO_ADMIN) null else member.id
                                                    onSetCoAdmin(newTarget)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    if (member.role == MemberRole.CO_ADMIN) Icons.Default.Shield else Icons.Default.Security,
                                                    contentDescription = if (member.role == MemberRole.CO_ADMIN) "Retirer Co-Admin" else "Désigner Co-Admin",
                                                    tint = if (member.role == MemberRole.CO_ADMIN) AmberGold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        // Delete member button
                                        val canDeleteThisPart = (!isPassed) && (member.userId != currentUser.id || mySlots.size > 1)
                                        if (canDeleteThisPart) {
                                            IconButton(
                                                onClick = { memberToDelete = member },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Supprimer la part",
                                                    tint = StatusUnpaidRed,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Add member button (Restricted exclusively to Admins per Feedback B.7)
                    if (isAdmin) {
                        item {
                            OutlinedButton(
                                onClick = { showAddMemberDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ajouter un membre à la rotation", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                2 -> {
                    // Fil d'actualité en direct
                    item {
                        Text(
                            text = "Fil d'actualité en direct",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Mises à jour automatiques des cotisations, validations et modifications de calendrier.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }

                    if (activities.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(30.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Aucune activité pour le moment.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(activities) { activity ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val (badgeBg, iconColor, icon) = when (activity.type) {
                                        ActivityType.PAYMENT_VALIDATED -> Triple(
                                            StatusValidatedGreen.copy(alpha = 0.15f),
                                            StatusValidatedGreen,
                                            Icons.Default.CheckCircle
                                        )
                                        ActivityType.PAYMENT_SUBMITTED -> Triple(
                                            AmberGold.copy(alpha = 0.15f),
                                            AmberGold,
                                            Icons.Default.FileUpload
                                        )
                                        ActivityType.PAYMENT_REJECTED -> Triple(
                                            StatusUnpaidRed.copy(alpha = 0.15f),
                                            StatusUnpaidRed,
                                            Icons.Default.Cancel
                                        )
                                        ActivityType.MEMBER_JOINED -> Triple(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            MaterialTheme.colorScheme.primary,
                                            Icons.Default.PersonAdd
                                        )
                                        ActivityType.CYCLE_STARTED -> Triple(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            MaterialTheme.colorScheme.primary,
                                            Icons.Default.RestartAlt
                                        )
                                        ActivityType.SYSTEM_ALERT -> Triple(
                                            AmberGold.copy(alpha = 0.15f),
                                            AmberGold,
                                            Icons.Default.NotificationsActive
                                        )
                                        ActivityType.TONTINE_UPDATED -> Triple(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            MaterialTheme.colorScheme.primary,
                                            Icons.Default.EditNote
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(badgeBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = activity.title,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = activity.description,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }

                                    val diffMin = ((System.currentTimeMillis() - activity.timestamp) / 60000).coerceAtLeast(1)
                                    Text(
                                        text = "${diffMin}m",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Admin Settings Modal Bottom Sheet (Feedback B.1, B.3 & B.5)
    if (showAdminSettingsDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var editName by remember { mutableStateOf(tontine.name) }
        var editDescription by remember { mutableStateOf(tontine.description) }
        var editAmount by remember { mutableStateOf(tontine.contributionAmount.toInt().toString()) }
        var editStartDate by remember { mutableStateOf(tontine.startDate) }
        var editEndDate by remember { mutableStateOf(tontine.endDate) }
        var editPenalty by remember { mutableStateOf(tontine.latePenaltyFee.toInt().toString()) }
        var editFrequency by remember { mutableStateOf(tontine.frequency) }
        var editBeneficiaryExempt by remember { mutableStateOf(tontine.isBeneficiaryExempt) }
        var editAutoRotation by remember { mutableStateOf(tontine.autoRotation) }
        var showDatePickerModal by remember { mutableStateOf(false) }

        val isPending = tontine.isEffectivelyPending
        val haptic = LocalHapticFeedback.current

        if (showDatePickerModal) {
            AppDatePickerModal(
                onDateSelected = { formatted, _ ->
                    editStartDate = formatted
                    editEndDate = formatted
                    HapticHelper.triggerLightClick(context, haptic)
                },
                onDismiss = { showDatePickerModal = false },
                title = "Date de début / lancement"
            )
        }

        ModalBottomSheet(
            onDismissRequest = { showAdminSettingsDialog = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Paramètres de la Tontine",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { showAdminSettingsDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer")
                        }
                    }
                }

                // Nom
                item {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nom de la tontine") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Cotisation
                item {
                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { editAmount = it },
                        label = { Text("Cotisation par membre (${tontine.currency})") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Fréquence
                item {
                    Text(
                        text = "Fréquence des tours",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TontineFrequency.entries.forEach { freq ->
                            val isSel = editFrequency == freq
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    editFrequency = freq
                                    HapticHelper.triggerLightClick(context, haptic)
                                },
                                label = { Text(freq.labelFr, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Date de lancement (avec calendrier)
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDatePickerModal = true
                                HapticHelper.triggerLightClick(context, haptic)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Date de début / lancement",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = editStartDate.ifBlank { "Choisir une date" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "Calendrier 📅",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Exemption du bénéficiaire (Feedback B.5)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Exemption du bénéficiaire",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (editBeneficiaryExempt) "Le receveur ne cotise pas à son propre tour." else "Tous les membres cotisent obligatoirement.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            Switch(
                                checked = editBeneficiaryExempt,
                                onCheckedChange = {
                                    editBeneficiaryExempt = it
                                    HapticHelper.triggerLightClick(context, haptic)
                                }
                            )
                        }
                    }
                }

                // Tours automatiques (Feedback B.1)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Tours automatiques",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isPending) "Attribution automatique de l'ordre à chaque cycle." else "Configurable uniquement avant le lancement de la tontine.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            Switch(
                                checked = editAutoRotation,
                                onCheckedChange = {
                                    if (isPending) {
                                        editAutoRotation = it
                                        HapticHelper.triggerLightClick(context, haptic)
                                    }
                                },
                                enabled = isPending
                            )
                        }
                    }
                }

                // Amende de retard
                item {
                    OutlinedTextField(
                        value = editPenalty,
                        onValueChange = { editPenalty = it },
                        label = { Text("Amende de retard (${tontine.currency})") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Lancement immédiat si en attente
                if (tontine.isEffectivelyPending) {
                    item {
                        Button(
                            onClick = {
                                showAdminSettingsDialog = false
                                onLaunchTontine()
                                HapticHelper.triggerMediumClick(context, haptic)
                                Toast.makeText(context, "Tontine '${tontine.name}' lancée !", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .bounceClick()
                                .testTag("launch_tontine_now_button")
                        ) {
                            Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lancer la tontine immédiatement 🚀", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Bouton Enregistrer les modifications
                item {
                    Button(
                        onClick = {
                            val amt = editAmount.toDoubleOrNull() ?: tontine.contributionAmount
                            val pen = editPenalty.toDoubleOrNull() ?: tontine.latePenaltyFee
                            onUpdateSettings(
                                editName.trim(),
                                editDescription.trim(),
                                amt,
                                editFrequency,
                                tontine.currency,
                                editStartDate.trim(),
                                editEndDate.trim(),
                                pen,
                                editBeneficiaryExempt,
                                editAutoRotation,
                                tontine.isPendingLaunch
                            )
                            showAdminSettingsDialog = false
                            HapticHelper.triggerMediumClick(context, haptic)
                            Toast.makeText(context, "Paramètres de la tontine mis à jour !", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .bounceClick()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enregistrer les modifications", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                // Danger Zone
                if (isMainAdmin) {
                    item {
                        OutlinedButton(
                            onClick = {
                                showAdminSettingsDialog = false
                                showDeleteTontineConfirmation = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusUnpaidRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusUnpaidRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delete_tontine_button")
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Supprimer ce groupe de tontine", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }

    // Delete Member Confirmation Dialog
    if (memberToDelete != null) {
        val target = memberToDelete!!
        val isSelfPart = target.userId == currentUser.id
        val targetDisplayName = target.getPartDisplayName(members)
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            title = { Text(if (isSelfPart) "Supprimer votre part ?" else "Supprimer cette part ?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (isSelfPart) "Voulez-vous vraiment supprimer votre part '$targetDisplayName' de cette tontine ? L'ordre de passage et le montant de la cagnotte seront automatiquement recalculés."
                    else "Voulez-vous vraiment retirer la part '$targetDisplayName' de cette tontine ? L'ordre de passage et le montant de la cagnotte seront automatiquement recalculés."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveMember(target.id)
                        memberToDelete = null
                        Toast.makeText(context, "$targetDisplayName a été retiré(e)", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusUnpaidRed)
                ) {
                    Text("Supprimer la part", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Reorder Member Dialog (Tap-to-reorder alternative to drag gesture)
    if (memberToReorder != null) {
        val target = memberToReorder!!
        val isPending = tontine.isEffectivelyPending
        val minOrderAllowed = if (isPending) 1 else (cycle?.cycleNumber ?: 1)
        val maxOrderAllowed = members.size
        val targetDisplayName = target.getPartDisplayName(members)

        AlertDialog(
            onDismissRequest = { memberToReorder = null },
            title = {
                Text("Modifier l'ordre de passage", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Positionner '$targetDisplayName' dans l'ordre de rotation (actuellement au rang #${target.payoutOrder}) :",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (target.payoutOrder > minOrderAllowed) {
                                    val newPos = target.payoutOrder - 1
                                    onReorderMember(target.id, newPos)
                                    memberToReorder = null
                                    Toast.makeText(context, "$targetDisplayName déplacé(e) au rang #$newPos", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = target.payoutOrder > minOrderAllowed,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Monter")
                        }

                        Button(
                            onClick = {
                                if (target.payoutOrder < maxOrderAllowed) {
                                    val newPos = target.payoutOrder + 1
                                    onReorderMember(target.id, newPos)
                                    memberToReorder = null
                                    Toast.makeText(context, "$targetDisplayName déplacé(e) au rang #$newPos", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = target.payoutOrder < maxOrderAllowed,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Descendre")
                        }
                    }

                    Text(
                        text = "Ou choisir directement la position :",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items((minOrderAllowed..maxOrderAllowed).toList()) { pos ->
                            val isCurrent = pos == target.payoutOrder
                            FilterChip(
                                selected = isCurrent,
                                onClick = {
                                    if (pos != target.payoutOrder) {
                                        onReorderMember(target.id, pos)
                                        memberToReorder = null
                                        Toast.makeText(context, "$targetDisplayName déplacé(e) au rang #$pos", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text("#$pos", fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { memberToReorder = null }) {
                    Text("Fermer")
                }
            }
        )
    }

    // Delete Entire Tontine Confirmation Dialog
    if (showDeleteTontineConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteTontineConfirmation = false },
            title = { Text("Supprimer la tontine ?", fontWeight = FontWeight.Bold, color = StatusUnpaidRed) },
            text = {
                Text("Cette action est irréversible. L'ensemble des cycles, membres et transactions de '${tontine.name}' seront définitivement supprimés.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteTontineConfirmation = false
                        onDeleteTontine()
                        Toast.makeText(context, "Groupe de tontine supprimé", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusUnpaidRed)
                ) {
                    Text("Confirmer la suppression", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTontineConfirmation = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Share / QR Code Dialog
    if (showInviteDialog) {
        var inviteTab by remember { mutableIntStateOf(0) }
        var invitePhoneInput by remember { mutableStateOf("") }
        val directAppScheme = "tontinepay://join?code=${tontine.inviteCode}"
        val serverDownloadUrl = "https://ais-pre-3k5lkqv7ovqerb6mqswtsl-961012230089.europe-west2.run.app"
        val joinUrl = directAppScheme

        val contactPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickContact()
        ) { contactUri ->
            if (contactUri != null) {
                try {
                    val cursor = context.contentResolver.query(
                        contactUri,
                        arrayOf(ContactsContract.Contacts._ID),
                        null, null, null
                    )
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
                            if (idIdx != -1) {
                                val id = c.getString(idIdx)
                                val pCursor = context.contentResolver.query(
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                    arrayOf(id),
                                    null
                                )
                                pCursor?.use { pc ->
                                    if (pc.moveToFirst()) {
                                        val numIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                        if (numIdx != -1) {
                                            val phone = pc.getString(numIdx)
                                            invitePhoneInput = phone.replace(" ", "")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Impossible de lire le contact", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val contactsPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                contactPicker.launch(null)
            } else {
                Toast.makeText(context, "Autorisation d'accès aux contacts refusée", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("Inviter des membres", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TabRow(
                        selectedTabIndex = inviteTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    ) {
                        Tab(
                            selected = inviteTab == 0,
                            onClick = { inviteTab = 0 },
                            text = { Text("QR Code & Lien", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = inviteTab == 1,
                            onClick = { inviteTab = 1 },
                            text = { Text("Numéro Mobile", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                    }

                    if (inviteTab == 0) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QRCodeView(data = joinUrl, size = 180.dp)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = tontine.inviteCode,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(tontine.inviteCode))
                                            Toast.makeText(context, "Code copié !", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copier",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                        contactPicker.launch(null)
                                    } else {
                                        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sélectionner un contact", fontWeight = FontWeight.SemiBold)
                            }

                            Text(
                                text = "Ou saisir le numéro directement :",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                            OutlinedTextField(
                                value = invitePhoneInput,
                                onValueChange = { invitePhoneInput = it },
                                placeholder = { Text("Ex: 680445566 ou +237...") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("invite_phone_input")
                            )
                            Text(
                                text = "L'utilisateur recevra une alerte push et sera directement intégré au groupe.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (inviteTab == 0) {
                    Button(
                        onClick = {
                            val shareMessage = buildString {
                                appendLine("Rejoins notre tontine '${tontine.name}' sur TontinePay !")
                                appendLine("Code d'invitation : ${tontine.inviteCode}")
                                appendLine()
                                appendLine("📲 Si tu as déjà l'application TontinePay, clique ici pour ouvrir et rejoindre directement :")
                                appendLine(directAppScheme)
                                appendLine()
                                appendLine("🌐 Si l'application n'est pas encore installée sur ton téléphone, télécharge-la ici :")
                                appendLine(serverDownloadUrl)
                            }
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareMessage)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Partager l'invitation"))
                            showInviteDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Partager le lien", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            if (invitePhoneInput.isNotBlank()) {
                                onInviteByPhone(invitePhoneInput.trim())
                                showInviteDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("submit_phone_invite_button")
                    ) {
                        Text("Envoyer l'invitation", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) {
                    Text("Fermer", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Add Member Dialog
    if (showAddMemberDialog) {
        var memberName by remember { mutableStateOf("") }
        var memberPhone by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("Ajouter un membre à la rotation", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = memberName,
                        onValueChange = { memberName = it },
                        label = { Text("Nom complet") },
                        placeholder = { Text("Ex: Ousmane Camara") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = memberPhone,
                        onValueChange = { memberPhone = it },
                        label = { Text("Numéro Mobile Money") },
                        placeholder = { Text("Ex: +237 690 00 00 00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (memberName.isNotBlank() && memberPhone.isNotBlank()) {
                            onAddMember(memberName, memberPhone)
                            showAddMemberDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Ajouter", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemberDialog = false }) {
                    Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Leave Tontine Confirmation Dialog (Feedback C.1)
    if (showLeaveDialog) {
        val currentCycleNumber = cycle?.cycleNumber ?: 1
        val currentMember = members.find { it.userId == currentUser.id }
        val memberHasBenefited = (currentMember?.payoutOrder ?: 99) < currentCycleNumber || (cycle?.beneficiaryId == currentUser.id)
        val isSoleAdmin = isAdmin && members.size > 1

        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Quitter la tontine",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        isSoleAdmin -> {
                            Text(
                                text = "Action bloquée : Vous êtes l'administrateur unique de cette tontine. Vous devez désigner un autre administrateur ou supprimer la tontine avant de pouvoir quitter.",
                                color = StatusUnpaidRed,
                                fontSize = 13.sp
                            )
                        }
                        memberHasBenefited -> {
                            Text(
                                text = "Mesure de sécurité financière : Vous avez déjà bénéficié de la cagnotte pour ce cycle de rotation. Vous devez terminer le cycle actuel avant de pouvoir vous retirer.",
                                color = AmberGold,
                                fontSize = 13.sp
                            )
                        }
                        else -> {
                            Text(
                                text = "Êtes-vous sûr de vouloir quitter la tontine '${tontine.name}' ? Elle sera retirée de votre liste et vous ne recevrez plus de notifications pour ce groupe.",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (!isSoleAdmin && !memberHasBenefited) {
                    Button(
                        onClick = {
                            showLeaveDialog = false
                            onLeaveTontine()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.testTag("confirm_leave_tontine_button")
                    ) {
                        Text("Quitter définitivement", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Fermer", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Add Additional Profile Slot Dialog (Feedback B.8 & User Request 7)
    if (showAddSlotDialog) {
        val currentSlotCount = members.count { it.userId == currentUser.id }
        var aliasInput by remember { mutableStateOf("(Nom #${currentSlotCount + 1})") }

        AlertDialog(
            onDismissRequest = { showAddSlotDialog = false },
            icon = {
                Icon(
                    Icons.Default.Badge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Ajouter un nom",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Chaque nom cotise et reçoit de manière indépendante selon l'ordre de passage défini par l'Admin.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        label = { Text("Libellé ou suffixe du nom") },
                        placeholder = { Text("Ex: (Nom #2)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("slot_alias_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (!isAdmin) {
                        Text(
                            text = "ℹ️ Cette demande sera soumise à l'approbation de l'administrateur avant d'entrer dans la rotation.",
                            color = AmberGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (aliasInput.isNotBlank()) {
                            onAddAdditionalSlot(aliasInput.trim())
                            showAddSlotDialog = false
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_add_slot_button")
                ) {
                    Text(if (isAdmin) "Ajouter le nom" else "Envoyer la demande", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSlotDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}
