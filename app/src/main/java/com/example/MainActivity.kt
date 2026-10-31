package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.NetworkMonitor
import com.example.ui.TontineViewModel
import com.example.ui.components.TelegramBottomNavBar
import com.example.ui.components.TelegramTab
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

enum class AppScreen {
    TONTINE_LIST,
    CREATE_TONTINE,
    PROFILE_SETTINGS,
    TONTINE_DETAIL,
    SUBMIT_PROOF,
    RECEIVER_VALIDATION,
    NOTIFICATIONS
}

class MainActivity : ComponentActivity() {

    private val viewModel: TontineViewModel by viewModels()
    private lateinit var networkMonitor: NetworkMonitor
    private var pendingDeepLinkCode by mutableStateOf<String?>(null)

    private fun extractInviteCode(intent: android.content.Intent?): String? {
        val uri = intent?.data ?: return null
        return uri.getQueryParameter("code")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        networkMonitor = NetworkMonitor(applicationContext)

        // Handle push notification intent clicks (Feedback C.1: open Fil Groupe Tab 2)
        intent?.getStringExtra("notification_doc_id")?.let { docId ->
            if (docId.isNotBlank()) {
                viewModel.markNotificationAsRead(docId)
            }
        }
        val notifTontineId = intent?.getStringExtra("selected_tontine_id")
        if (!notifTontineId.isNullOrBlank()) {
            viewModel.selectTontine(notifTontineId)
        }
        extractInviteCode(intent)?.let { code ->
            if (code.isNotBlank()) {
                pendingDeepLinkCode = code
            }
        }

        setContent {
            val displayMode by viewModel.displayMode.collectAsStateWithLifecycle()
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val accentTheme by viewModel.accentTheme.collectAsStateWithLifecycle()
            val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
            val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

            // Request Notification Permission on Android 13+ (API 33+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (!isGranted) {
                        // User chose not to grant now, will rely on in-app notifications
                    }
                }
                LaunchedEffect(Unit) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            MyApplicationTheme(
                displayMode = displayMode,
                darkTheme = isDarkMode,
                accentTheme = accentTheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Offline banner (Feedback E.1)
                        AnimatedVisibility(visible = !isOnline) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .statusBarsPadding()
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.WifiOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Mode hors ligne • Les données locales restent accessibles",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

                        if (currentUser == null) {
                            OnboardingScreen(
                                initialInviteCode = pendingDeepLinkCode,
                                onRegister = { name, phone, password, avatarUrl, inviteCode ->
                                    val codeToJoin = inviteCode ?: pendingDeepLinkCode
                                    val result = viewModel.registerUser(name, phone, password, avatarUrl)
                                    result.onSuccess {
                                        Toast.makeText(this@MainActivity, "Compte créé ! Bienvenue sur TontinePay, ${it.fullName}.", Toast.LENGTH_SHORT).show()
                                        pendingDeepLinkCode = null
                                        if (!codeToJoin.isNullOrBlank()) {
                                            viewModel.joinTontineByCode(
                                                code = codeToJoin,
                                                onSuccess = { joined ->
                                                    Toast.makeText(this@MainActivity, "Vous avez rejoint '${joined.name}' !", Toast.LENGTH_LONG).show()
                                                },
                                                onError = { err ->
                                                    Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        }
                                    }.onFailure { err ->
                                        Toast.makeText(this@MainActivity, err.message ?: "Erreur d'inscription", Toast.LENGTH_LONG).show()
                                    }
                                },
                                onLogin = { phone, password ->
                                    val result = viewModel.loginUser(phone, password)
                                    result.onSuccess {
                                        Toast.makeText(this@MainActivity, "Connexion réussie ! Ravi de vous revoir, ${it.fullName}.", Toast.LENGTH_SHORT).show()
                                        pendingDeepLinkCode?.let { code ->
                                            viewModel.joinTontineByCode(
                                                code = code,
                                                onSuccess = { joined ->
                                                    pendingDeepLinkCode = null
                                                    Toast.makeText(this@MainActivity, "Vous avez rejoint '${joined.name}' !", Toast.LENGTH_LONG).show()
                                                },
                                                onError = { err ->
                                                    pendingDeepLinkCode = null
                                                    Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        }
                                    }.onFailure { err ->
                                        Toast.makeText(this@MainActivity, err.message ?: "Identifiants invalides", Toast.LENGTH_LONG).show()
                                    }
                                },
                                onResetPassword = { phone, newPassword ->
                                    val result = viewModel.resetPassword(phone, newPassword)
                                    result.onSuccess {
                                        Toast.makeText(this@MainActivity, "Mot de passe réinitialisé ! Vous pouvez maintenant vous connecter.", Toast.LENGTH_LONG).show()
                                    }.onFailure { err ->
                                        Toast.makeText(this@MainActivity, err.message ?: "Erreur de réinitialisation", Toast.LENGTH_LONG).show()
                                    }
                                },
                                onJoinTontineCode = { code ->
                                    Toast.makeText(this@MainActivity, "Code saisi : $code.", Toast.LENGTH_SHORT).show()
                                },
                                isPhoneRegistered = { phone -> viewModel.isPhoneRegistered(phone) }
                            )
                        } else {
                            val activeUser = currentUser!!
                            val tontines by viewModel.tontines.collectAsStateWithLifecycle()
                            val selectedTontine by viewModel.selectedTontine.collectAsStateWithLifecycle()
                            val currentCycle by viewModel.currentCycle.collectAsStateWithLifecycle()
                            val currentMembers by viewModel.currentMembers.collectAsStateWithLifecycle()
                            val matrix by viewModel.cotisationMatrix.collectAsStateWithLifecycle()
                            val activities by viewModel.currentActivities.collectAsStateWithLifecycle()
                            val pendingValidations by viewModel.pendingValidationsForCurrentUser.collectAsStateWithLifecycle()
                            val userNotifications by viewModel.userNotifications.collectAsStateWithLifecycle()
                            val unreadNotifCount by viewModel.unreadNotificationsCount.collectAsStateWithLifecycle()
                            val ocrState by viewModel.ocrState.collectAsStateWithLifecycle()
                            val isUpdateAvailable by viewModel.isUpdateAvailable.collectAsStateWithLifecycle()
                            val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsStateWithLifecycle()
                            val updateProgress by viewModel.updateProgress.collectAsStateWithLifecycle()
                            val isUpdateReadyToRestart by viewModel.isUpdateReadyToRestart.collectAsStateWithLifecycle()
                            val activeSlotMemberId by viewModel.activeSlotMemberId.collectAsStateWithLifecycle()
                            val showDownloadConfigModal by viewModel.showDownloadConfigModal.collectAsStateWithLifecycle()
                            val serverDownloadUrl by viewModel.serverDownloadUrl.collectAsStateWithLifecycle()

                            val notifTontineId = intent?.getStringExtra("selected_tontine_id")
                            val initialScreen = if (!notifTontineId.isNullOrBlank()) AppScreen.TONTINE_DETAIL else AppScreen.TONTINE_LIST
                            val initialTab = if (!notifTontineId.isNullOrBlank()) 2 else 0

                            var currentScreen by remember { mutableStateOf(initialScreen) }
                            var detailInitialTab by remember { mutableIntStateOf(initialTab) }

                            // Feedback A.3: Every back gesture/button press must lead back to Home screen before exiting
                            BackHandler(enabled = currentScreen != AppScreen.TONTINE_LIST) {
                                currentScreen = when (currentScreen) {
                                    AppScreen.SUBMIT_PROOF -> AppScreen.TONTINE_DETAIL
                                    AppScreen.RECEIVER_VALIDATION -> if (selectedTontine != null) AppScreen.TONTINE_DETAIL else AppScreen.TONTINE_LIST
                                    AppScreen.TONTINE_DETAIL -> AppScreen.TONTINE_LIST
                                    AppScreen.CREATE_TONTINE -> AppScreen.TONTINE_LIST
                                    AppScreen.PROFILE_SETTINGS -> AppScreen.TONTINE_LIST
                                    AppScreen.NOTIFICATIONS -> AppScreen.TONTINE_LIST
                                    AppScreen.TONTINE_LIST -> AppScreen.TONTINE_LIST
                                }
                            }

                            LaunchedEffect(pendingDeepLinkCode) {
                                pendingDeepLinkCode?.let { code ->
                                    viewModel.joinTontineByCode(
                                        code = code,
                                        onSuccess = { joined ->
                                            pendingDeepLinkCode = null
                                            Toast.makeText(this@MainActivity, "Vous avez rejoint '${joined.name}' !", Toast.LENGTH_LONG).show()
                                            currentScreen = AppScreen.TONTINE_DETAIL
                                        },
                                        onError = { err ->
                                            pendingDeepLinkCode = null
                                            Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            }

                            // Root tabs in iOS Telegram style
                            val isRootTab = currentScreen in listOf(
                                AppScreen.TONTINE_LIST,
                                AppScreen.CREATE_TONTINE,
                                AppScreen.PROFILE_SETTINGS
                            )

                            val activeTelegramTab = when (currentScreen) {
                                AppScreen.TONTINE_LIST -> TelegramTab.TONTINES
                                AppScreen.CREATE_TONTINE -> TelegramTab.CREATE
                                AppScreen.PROFILE_SETTINGS -> TelegramTab.SETTINGS
                                else -> TelegramTab.TONTINES
                            }

                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                containerColor = MaterialTheme.colorScheme.background,
                                bottomBar = {
                                    if (isRootTab) {
                                        TelegramBottomNavBar(
                                            selectedTab = activeTelegramTab,
                                            onTabSelected = { tab ->
                                                currentScreen = when (tab) {
                                                    TelegramTab.TONTINES -> AppScreen.TONTINE_LIST
                                                    TelegramTab.CREATE -> AppScreen.CREATE_TONTINE
                                                    TelegramTab.SETTINGS -> AppScreen.PROFILE_SETTINGS
                                                }
                                            }
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = if (isRootTab) innerPadding.calculateBottomPadding() else 0.dp)
                                ) {
                                    when (currentScreen) {
                                        AppScreen.TONTINE_LIST -> {
                                            TontineListScreen(
                                                tontines = tontines,
                                                currentUser = activeUser,
                                                pendingValidations = pendingValidations,
                                                unreadNotificationCount = unreadNotifCount,
                                                onSelectTontine = { tontineId ->
                                                    viewModel.selectTontine(tontineId)
                                                    currentScreen = AppScreen.TONTINE_DETAIL
                                                },
                                                onCreateTontine = { name, desc, amount, freq, curr, members ->
                                                    viewModel.createTontine(name, desc, amount, freq, curr, members)
                                                    Toast.makeText(this@MainActivity, "Tontine créée avec succès !", Toast.LENGTH_SHORT).show()
                                                    currentScreen = AppScreen.TONTINE_DETAIL
                                                },
                                                onJoinTontineByCode = { code ->
                                                    viewModel.joinTontineByCode(
                                                        code = code,
                                                        onSuccess = { joined ->
                                                            Toast.makeText(this@MainActivity, "Vous avez rejoint '${joined.name}' !", Toast.LENGTH_LONG).show()
                                                            currentScreen = AppScreen.TONTINE_DETAIL
                                                        },
                                                        onError = { error ->
                                                            Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                                                        }
                                                    )
                                                },
                                                onOpenReceiverValidation = {
                                                    currentScreen = AppScreen.RECEIVER_VALIDATION
                                                },
                                                onOpenNotifications = {
                                                    currentScreen = AppScreen.NOTIFICATIONS
                                                },
                                                onOpenProfileSettings = {
                                                    currentScreen = AppScreen.PROFILE_SETTINGS
                                                },
                                                onNavigateToCreate = {
                                                    currentScreen = AppScreen.CREATE_TONTINE
                                                },
                                                 onRefresh = {
                                                     viewModel.refreshData()
                                                     Toast.makeText(this@MainActivity, "Données actualisées", Toast.LENGTH_SHORT).show()
                                                 },
                                                 isRefreshing = isRefreshing,
                                                 isUpdateAvailable = isUpdateAvailable,
                                                 isDownloadingUpdate = isDownloadingUpdate,
                                                 updateProgress = updateProgress,
                                                 isUpdateReadyToRestart = isUpdateReadyToRestart,
                                                 onDownloadUpdate = { viewModel.downloadAndApplyUpdate(this@MainActivity) },
                                                 onRestartApp = { viewModel.restartAppAfterUpdate(this@MainActivity) },
                                                 onDismissUpdate = { viewModel.dismissUpdateBanner() }
                                             )
                                         }

                                 AppScreen.CREATE_TONTINE -> {
                                     CreateTontineScreen(
                                         onCreateTontine = { name, desc, amount, freq, curr, members, startDate, endDate, penalty, isBeneficiaryExempt, isPendingLaunch, autoRotation ->
                                             viewModel.createTontine(
                                                 name = name,
                                                 description = desc,
                                                 amount = amount,
                                                 frequency = freq,
                                                 currency = curr,
                                                 memberCount = members,
                                                 startDate = startDate,
                                                 endDate = endDate,
                                                 latePenaltyFee = penalty,
                                                 isBeneficiaryExempt = isBeneficiaryExempt,
                                                 isPendingLaunch = isPendingLaunch,
                                                 autoRotation = autoRotation
                                             )
                                             Toast.makeText(this@MainActivity, "Tontine '$name' créée avec succès !", Toast.LENGTH_SHORT).show()
                                             currentScreen = AppScreen.TONTINE_DETAIL
                                         },
                                         onBack = { currentScreen = AppScreen.TONTINE_LIST }
                                     )
                                 }

                                 AppScreen.PROFILE_SETTINGS -> {
                                     ProfileSettingsScreen(
                                         currentUser = activeUser,
                                         displayMode = displayMode,
                                         isDarkMode = isDarkMode,
                                         accentTheme = accentTheme,
                                         currentAppVersion = viewModel.currentAppVersion.collectAsStateWithLifecycle().value,
                                         latestServerVersion = viewModel.latestServerVersion.collectAsStateWithLifecycle().value,
                                         isCheckingUpdate = viewModel.isCheckingUpdate.collectAsStateWithLifecycle().value,
                                         isUpdateAvailable = isUpdateAvailable,
                                         isDownloadingUpdate = isDownloadingUpdate,
                                         updateProgress = updateProgress,
                                         isUpdateReadyToRestart = isUpdateReadyToRestart,
                                         onCheckForUpdate = {
                                             viewModel.checkForServerUpdate { hasNew ->
                                                 if (!hasNew) {
                                                     Toast.makeText(this@MainActivity, "Votre application est déjà à jour !", Toast.LENGTH_SHORT).show()
                                                 }
                                             }
                                         },
                                         onDownloadUpdate = { viewModel.downloadAndApplyUpdate(this@MainActivity) },
                                         onRestartApp = { viewModel.restartAppAfterUpdate(this@MainActivity) },
                                         onSelectDisplayMode = { viewModel.setDisplayMode(it) },
                                         onToggleDarkMode = { viewModel.toggleDarkMode(it) },
                                         onSelectAccentTheme = { viewModel.setAccentTheme(it) },
                                         onSaveProfile = { fullName, phone, avatarUrl, prefs ->
                                             viewModel.updateUserProfile(fullName, phone, avatarUrl, prefs)
                                         },
                                         onDeleteProfilePhoto = {
                                             viewModel.deleteProfilePhoto()
                                             Toast.makeText(this@MainActivity, "Photo de profil supprimée", Toast.LENGTH_SHORT).show()
                                         },
                                         onLogout = {
                                             viewModel.logout()
                                             Toast.makeText(this@MainActivity, "Déconnexion réussie", Toast.LENGTH_SHORT).show()
                                         },
                                         onDeleteAccount = {
                                             viewModel.deleteAccount()
                                             Toast.makeText(this@MainActivity, "Compte supprimé", Toast.LENGTH_LONG).show()
                                         },
                                         onBack = { currentScreen = AppScreen.TONTINE_LIST },
                                         isPhoneRegistered = { viewModel.isPhoneRegistered(it) },
                                         onTestNotification = {
                                             viewModel.testNotificationSystem()
                                             Toast.makeText(this@MainActivity, "Notification de test envoyée avec succès !", Toast.LENGTH_SHORT).show()
                                         },
                                         showBackButton = false
                                     )
                                 }

                                 AppScreen.TONTINE_DETAIL -> {
                                     if (selectedTontine != null) {
                                         TontineDetailScreen(
                                             tontine = selectedTontine!!,
                                             cycle = currentCycle,
                                             members = currentMembers,
                                             matrix = matrix,
                                             activities = activities,
                                             currentUser = activeUser,
                                             pendingValidationsCount = pendingValidations.size,
                                             initialTab = detailInitialTab,
                                             activeSlotMemberId = activeSlotMemberId,
                                             onBack = { currentScreen = AppScreen.TONTINE_LIST },
                                             onUploadProofClick = { currentScreen = AppScreen.SUBMIT_PROOF },
                                             onOpenReceiverValidation = { currentScreen = AppScreen.RECEIVER_VALIDATION },
                                             onAddMember = { name, phone ->
                                                 viewModel.addMember(name, phone)
                                                 Toast.makeText(this@MainActivity, "$name ajouté à la tontine !", Toast.LENGTH_SHORT).show()
                                             },
                                             onInviteByPhone = { phone ->
                                                 viewModel.inviteUserByPhone(
                                                     phoneNumber = phone,
                                                     onSuccess = { msg ->
                                                         Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                                                     },
                                                     onError = { err ->
                                                         Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                                                     }
                                                 )
                                             },
                                             onReorderMember = { memberId, newPosition ->
                                                 viewModel.reorderMember(memberId, newPosition)
                                                 Toast.makeText(this@MainActivity, "Ordre mis à jour", Toast.LENGTH_SHORT).show()
                                             },
                                             onReorderAllMembers = { orderedIds ->
                                                 viewModel.reorderAllMembers(orderedIds)
                                             },
                                             onRemoveMember = { memberId ->
                                                 viewModel.removeMember(memberId)
                                             },
                                             onDeleteTontine = {
                                                 viewModel.deleteCurrentTontine()
                                                 currentScreen = AppScreen.TONTINE_LIST
                                             },
                                             onLeaveTontine = {
                                                 if (selectedTontine != null) {
                                                    viewModel.leaveTontine(
                                                         tontineId = selectedTontine!!.id,
                                                         onSuccess = {
                                                             Toast.makeText(this@MainActivity, "Vous avez quitté la tontine", Toast.LENGTH_SHORT).show()
                                                             currentScreen = AppScreen.TONTINE_LIST
                                                         },
                                                         onError = { err ->
                                                             Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                                                         }
                                                     )
                                                 }
                                             },
                                             onUpdateSettings = { name, desc, amount, freq, curr, startDate, endDate, penalty, isBeneficiaryExempt, autoRotation, isPendingLaunch ->
                                                 viewModel.updateTontineSettings(
                                                     name, desc, amount, freq, curr, startDate, endDate, penalty, isBeneficiaryExempt, autoRotation, isPendingLaunch
                                                 )
                                             },
                                             onSelectActiveSlot = { viewModel.setActiveSlot(it) },
                                             onAddAdditionalSlot = { alias ->
                                                 viewModel.addAdditionalProfileSlot(
                                                     aliasLabel = alias,
                                                     onSuccess = { slot ->
                                                         Toast.makeText(this@MainActivity, "Part '${slot.displayName}' ajoutée !", Toast.LENGTH_SHORT).show()
                                                     },
                                                     onError = { err ->
                                                         Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                                                     }
                                                 )
                                             },
                                             onApproveMember = { memberId ->
                                                 selectedTontine?.let { t ->
                                                     viewModel.approvePendingMember(t.id, memberId)
                                                     Toast.makeText(this@MainActivity, "Membre approuvé avec succès !", Toast.LENGTH_SHORT).show()
                                                 }
                                             },
                                             onRejectMember = { memberId ->
                                                 selectedTontine?.let { t ->
                                                     viewModel.rejectPendingMember(t.id, memberId)
                                                     Toast.makeText(this@MainActivity, "Demande refusée", Toast.LENGTH_SHORT).show()
                                                 }
                                             },
                                             onSetCoAdmin = { memberId ->
                                                 selectedTontine?.let { t ->
                                                     viewModel.setCoAdmin(t.id, memberId)
                                                     Toast.makeText(this@MainActivity, if (memberId != null) "Co-Administrateur nommé" else "Co-Admin retiré", Toast.LENGTH_SHORT).show()
                                                 }
                                             },
                                             onLaunchTontine = {
                                                 selectedTontine?.let { t ->
                                                     viewModel.launchTontine(t.id)
                                                     Toast.makeText(this@MainActivity, "Tontine '${t.name}' lancée avec succès !", Toast.LENGTH_SHORT).show()
                                                 }
                                             }
                                         )
                                     } else {
                                         currentScreen = AppScreen.TONTINE_LIST
                                     }
                                 }

                                AppScreen.SUBMIT_PROOF -> {
                                    if (selectedTontine != null) {
                                        val activeSlotMemberId = viewModel.activeSlotMemberId.collectAsStateWithLifecycle().value
                                        val userSlots = currentMembers.filter { it.userId == activeUser.id && !it.isPendingApproval }
                                        val activeSlot = userSlots.find { it.id == activeSlotMemberId } ?: userSlots.firstOrNull()
                                        val currentMemberStatus = matrix.firstOrNull { it.member.id == activeSlot?.id } 
                                            ?: matrix.firstOrNull { it.member.userId == activeUser.id }
                                        val requiredAmount = currentMemberStatus?.amountDue?.takeIf { it > 0.0 } ?: selectedTontine!!.contributionAmount
                                        val penaltyFee = currentMemberStatus?.penaltyFee ?: 0.0
                                        val slotLabel = if (userSlots.size > 1 && activeSlot != null) activeSlot.getPartDisplayName(currentMembers) else ""

                                        PaymentProofSubmissionScreen(
                                            tontine = selectedTontine!!,
                                            cycle = currentCycle,
                                            ocrState = ocrState,
                                            isDuplicateTransactionId = { viewModel.isDuplicateTransactionId(it) },
                                            requiredAmount = requiredAmount,
                                            penaltyFee = penaltyFee,
                                            senderSlotLabel = slotLabel,
                                            onBack = { currentScreen = AppScreen.TONTINE_DETAIL },
                                            onAnalyzeReceipt = { bmp, hint ->
                                                viewModel.analyzeReceipt(bmp, hint)
                                            },
                                            onSubmitProof = { extractedData ->
                                                viewModel.submitTransaction(
                                                    extractedData = extractedData,
                                                    senderMemberId = activeSlot?.id,
                                                    onSuccess = {
                                                        Toast.makeText(
                                                            this@MainActivity,
                                                            "Preuve soumise avec succès au receveur !",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        currentScreen = AppScreen.TONTINE_DETAIL
                                                    },
                                                    onError = { err ->
                                                        Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            },
                                            onResetOcr = { viewModel.resetOcrState() }
                                        )
                                    } else {
                                        currentScreen = AppScreen.TONTINE_LIST
                                    }
                                }

                                AppScreen.RECEIVER_VALIDATION -> {
                                    ReceiverValidationScreen(
                                        pendingTransactions = pendingValidations,
                                        currentUser = activeUser,
                                        onBack = {
                                            if (selectedTontine != null) currentScreen = AppScreen.TONTINE_DETAIL
                                             else currentScreen = AppScreen.TONTINE_LIST
                                        },
                                        onValidate = { txnId ->
                                            viewModel.validatePayment(txnId)
                                            Toast.makeText(this@MainActivity, "Paiement validé avec succès ! Statut mis à jour.", Toast.LENGTH_SHORT).show()
                                        },
                                        onReject = { txnId, reason ->
                                            viewModel.rejectPayment(txnId, reason)
                                            Toast.makeText(this@MainActivity, "Paiement contesté. Notification envoyée.", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }

                                AppScreen.NOTIFICATIONS -> {
                                    NotificationCenterScreen(
                                        notifications = userNotifications,
                                        onBack = { currentScreen = AppScreen.TONTINE_LIST },
                                        onMarkAllAsRead = {
                                            viewModel.markAllNotificationsAsRead()
                                            Toast.makeText(this@MainActivity, "Toutes les notifications sont lues", Toast.LENGTH_SHORT).show()
                                        },
                                        onMarkAsRead = { notifId ->
                                            viewModel.markNotificationAsRead(notifId)
                                        },
                                        onSimulateNotification = {}
                                    )
                                }
                            }
                        }
                    }

                    if (showDownloadConfigModal) {
                                var inputUrl by remember(serverDownloadUrl) {
                                    mutableStateOf(if (serverDownloadUrl.contains("run.app")) "" else serverDownloadUrl)
                                }
                                AlertDialog(
                                    onDismissRequest = { viewModel.dismissDownloadConfigModal() },
                                    title = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Mise à jour APK (v1.6.0)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(
                                                text = "Pour télécharger directement le fichier APK sur votre téléphone, renseignez l'URL directe du fichier APK (ex: GitHub Releases, Firebase Storage, Google Drive ou votre serveur).",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            OutlinedTextField(
                                                value = inputUrl,
                                                onValueChange = { inputUrl = it },
                                                label = { Text("URL directe du fichier APK") },
                                                placeholder = { Text("https://mon-serveur.com/TontinePay-v1.6.0.apk") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            Text(
                                                text = "💡 Dans AI Studio : vous pouvez exporter l'APK depuis le menu Paramètres > 'Generate APK' puis l'héberger en ligne pour le partager.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                if (inputUrl.isNotBlank()) {
                                                    viewModel.setServerDownloadUrl(inputUrl)
                                                    viewModel.downloadAndApplyUpdate(this@MainActivity)
                                                } else {
                                                    viewModel.dismissDownloadConfigModal()
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Enregistrer et télécharger")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { viewModel.dismissDownloadConfigModal() }) {
                                            Text("Fermer")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("notification_doc_id")?.let { docId ->
            if (docId.isNotBlank()) {
                viewModel.markNotificationAsRead(docId)
            }
        }
        intent.getStringExtra("selected_tontine_id")?.let { tId ->
            if (tId.isNotBlank()) {
                viewModel.selectTontine(tId)
            }
        }
        extractInviteCode(intent)?.let { code ->
            if (code.isNotBlank()) {
                pendingDeepLinkCode = code
            }
        }
    }
}


