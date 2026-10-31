package com.example.ui

import android.app.Activity
import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.GeminiVisionService
import com.example.data.SessionManager
import com.example.data.TontineRepository
import com.example.data.db.AppDatabase
import com.example.model.*
import com.example.service.TontineNotificationSyncJob
import com.example.ui.theme.AccentTheme
import com.example.ui.theme.DisplayMode
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface OcrScanUiState {
    object Idle : OcrScanUiState
    data class Scanning(val stepMessage: String) : OcrScanUiState
    data class Success(val data: ExtractedReceiptData) : OcrScanUiState
    data class DuplicateError(val txnId: String, val message: String) : OcrScanUiState
    data class GeneralError(val message: String) : OcrScanUiState
}

data class MemberCotisationStatus(
    val member: TontineMember,
    val status: CotisationStatus,
    val transaction: Transaction? = null,
    val amountDue: Double = 0.0,
    val penaltyFee: Double = 0.0
)

class TontineViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: TontineRepository = TontineRepository(
        database = try { AppDatabase.getDatabase(application) } catch (e: Exception) { null },
        firestore = try {
            FirebaseApp.initializeApp(application)
            val dbId = application.getString(R.string.firestore_database_id)
            FirebaseFirestore.getInstance(FirebaseApp.getInstance(), dbId)
        } catch (e: Exception) {
            try { FirebaseFirestore.getInstance() } catch (e2: Exception) { null }
        },
        sessionManager = SessionManager.getInstance(application)
    ),
    private val geminiService: GeminiVisionService = GeminiVisionService()
) : AndroidViewModel(application) {

    val allUsers get() = repository.allUsers
    val users = repository.usersFlow
    val currentUser = repository.currentUser

    fun isPhoneRegistered(phone: String): Boolean {
        val norm = phone.replace(" ", "").replace("-", "")
        if (norm.length < 8) return false
        return repository.allUsers.any { it.phoneNumber.replace(" ", "").replace("-", "") == norm }
    }

    // Filter tontines so each user only sees tontines they created or belong to (Feedback D.1 & C.1)
    val tontines: StateFlow<List<Tontine>> = combine(
        repository.tontines,
        repository.currentUser,
        repository.members
    ) { allTontines, user, membersMap ->
        if (user == null) {
            emptyList()
        } else {
            val normUserPhone = user.phoneNumber.replace(" ", "")
            allTontines.filter { tontine ->
                tontine.creatorId == user.id ||
                membersMap[tontine.id]?.any { m ->
                    m.userId == user.id || m.phoneNumber.replace(" ", "") == normUserPhone
                } == true
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val sessionManager = SessionManager.getInstance(application)

    // Visual styles: Persisted across app restarts (Feedback A.1)
    private val _displayMode = MutableStateFlow(
        sessionManager.getSavedDisplayMode()?.let { saved ->
            try { DisplayMode.valueOf(saved) } catch (e: Exception) { DisplayMode.SYSTEM }
        } ?: DisplayMode.SYSTEM
    )
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    private val _isDarkMode = MutableStateFlow(
        sessionManager.getSavedDisplayMode() == DisplayMode.DARK.name
    )
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _accentTheme = MutableStateFlow(
        sessionManager.getSavedAccentTheme()?.let { saved ->
            try { AccentTheme.valueOf(saved) } catch (e: Exception) { AccentTheme.EMERALD }
        } ?: AccentTheme.EMERALD
    )
    val accentTheme: StateFlow<AccentTheme> = _accentTheme.asStateFlow()

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
        _isDarkMode.value = mode == DisplayMode.DARK
        sessionManager.setSavedDisplayMode(mode.name)
    }

    fun toggleDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        val mode = if (enabled) DisplayMode.DARK else DisplayMode.LIGHT
        _displayMode.value = mode
        sessionManager.setSavedDisplayMode(mode.name)
    }

    fun setAccentTheme(theme: AccentTheme) {
        _accentTheme.value = theme
        sessionManager.setSavedAccentTheme(theme.name)
    }

    fun createAccount(fullName: String, phoneNumber: String, avatarUrl: String = "") {
        repository.createUser(fullName, phoneNumber, avatarUrl)
    }

    fun registerUser(
        fullName: String,
        phoneNumber: String,
        password: String,
        avatarUrl: String = ""
    ): Result<User> {
        return repository.registerUser(fullName, phoneNumber, password, avatarUrl)
    }

    fun loginUser(phoneNumber: String, password: String): Result<User> {
        return repository.loginUser(phoneNumber, password)
    }

    fun resetPassword(phoneNumber: String, newPassword: String): Result<Unit> {
        return repository.resetPassword(phoneNumber, newPassword)
    }

    fun logout() {
        repository.logout()
    }

    fun deleteAccount() {
        val user = currentUser.value ?: return
        repository.deleteAccount(user.id)
    }

    fun resetAllData() {
        repository.resetAllData()
        _selectedTontineId.value = ""
        _ocrState.value = OcrScanUiState.Idle
    }

    private val _selectedTontineId = MutableStateFlow<String>("")
    val selectedTontineId = _selectedTontineId.asStateFlow()

    private val _ocrState = MutableStateFlow<OcrScanUiState>(OcrScanUiState.Idle)
    val ocrState = _ocrState.asStateFlow()

    companion object {
        val CURRENT_VERSION_STRING: String get() = "v${com.example.BuildConfig.VERSION_NAME}"
    }

    private fun isNewerVersion(serverVer: String, currentVer: String): Boolean {
        val cleanServer = serverVer.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val cleanCurrent = currentVer.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(cleanServer.size, cleanCurrent.size)) {
            val s = cleanServer.getOrElse(i) { 0 }
            val c = cleanCurrent.getOrElse(i) { 0 }
            if (s > c) return true
            if (s < c) return false
        }
        return false
    }

    private val _currentAppVersion = MutableStateFlow("v${com.example.BuildConfig.VERSION_NAME}")
    val currentAppVersion: StateFlow<String> = _currentAppVersion.asStateFlow()

    private val _latestServerVersion = MutableStateFlow("v${com.example.BuildConfig.VERSION_NAME}")
    val latestServerVersion: StateFlow<String> = _latestServerVersion.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _isUpdateAvailable = MutableStateFlow(false)
    val isUpdateAvailable: StateFlow<Boolean> = _isUpdateAvailable.asStateFlow()

    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate.asStateFlow()

    private val _updateProgress = MutableStateFlow(0f)
    val updateProgress: StateFlow<Float> = _updateProgress.asStateFlow()

    private val _isUpdateReadyToRestart = MutableStateFlow(false)
    val isUpdateReadyToRestart: StateFlow<Boolean> = _isUpdateReadyToRestart.asStateFlow()

    val serverDownloadUrl: StateFlow<String> = repository.serverDownloadUrl

    private val _showDownloadConfigModal = MutableStateFlow(false)
    val showDownloadConfigModal: StateFlow<Boolean> = _showDownloadConfigModal.asStateFlow()

    fun openDownloadConfigModal() {
        _showDownloadConfigModal.value = true
    }

    fun dismissDownloadConfigModal() {
        _showDownloadConfigModal.value = false
    }

    fun setServerDownloadUrl(url: String) {
        repository.updateServerDownloadUrl(url)
        _showDownloadConfigModal.value = false
    }

    init {
        // Wire realtime push notification delivery to Android NotificationManager
        repository.onNotificationArrived = { notif ->
            val sessionManager = SessionManager.getInstance(application)
            if (!sessionManager.isNotificationDispatched(notif.id)) {
                sessionManager.markNotificationDispatched(notif.id)
                NotificationHelper.showSystemNotification(
                    context = application,
                    notificationId = notif.id.hashCode(),
                    title = notif.title,
                    message = notif.message,
                    tontineId = notif.tontineId,
                    notificationDocId = notif.id
                )
            }
        }

        // Default to first tontine
        viewModelScope.launch {
            repository.tontines.collect { list ->
                if (_selectedTontineId.value.isEmpty() && list.isNotEmpty()) {
                    _selectedTontineId.value = list.first().id
                }
            }
        }

        // Schedule periodic background push notification synchronization
        TontineNotificationSyncJob.schedule(application)

        // Realtime server version check across all devices
        viewModelScope.launch {
            repository.serverAppVersion.collect { serverVer ->
                _latestServerVersion.value = serverVer
                val current = "v${com.example.BuildConfig.VERSION_NAME}"
                _currentAppVersion.value = current
                sessionManager.setAppliedVersion(current)
                val hasNew = isNewerVersion(serverVer, current)
                _isUpdateAvailable.value = hasNew
                if (hasNew) {
                    val notifKey = "update_notif_$serverVer"
                    if (!sessionManager.isNotificationDispatched(notifKey)) {
                        sessionManager.markNotificationDispatched(notifKey)
                        NotificationHelper.showSystemNotification(
                            context = getApplication<android.app.Application>(),
                            notificationId = 99991,
                            title = "🚀 Mise à jour $serverVer disponible",
                            message = "TontinePay $serverVer est disponible avec le nouveau logo et les dernières améliorations !",
                            tontineId = null,
                            notificationDocId = notifKey
                        )
                    }
                }
            }
        }
    }

    val selectedTontine: StateFlow<Tontine?> = combine(
        repository.tontines,
        _selectedTontineId
    ) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentMembers: StateFlow<List<TontineMember>> = combine(
        repository.members,
        _selectedTontineId
    ) { map, id ->
        map[id] ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Dynamic cycle calculation with accurate pot collected from validated transactions
    val currentCycle: StateFlow<TontineCycle?> = combine(
        repository.cycles,
        repository.transactions,
        _selectedTontineId
    ) { map, txns, id ->
        val cycle = map[id]?.find { it.status == CycleStatus.ACTIVE } ?: map[id]?.lastOrNull()
        if (cycle != null) {
            val validatedSum = txns.filter { it.cycleId == cycle.id && it.status == TransactionStatus.VALIDATED }
                .sumOf { it.amount }
            cycle.copy(potCollected = maxOf(cycle.potCollected, validatedSum))
        } else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refreshData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(600)
            _isRefreshing.value = false
        }
    }

    val currentTransactions: StateFlow<List<Transaction>> = combine(
        repository.transactions,
        currentCycle
    ) { list, cycle ->
        if (cycle == null) emptyList()
        else list.filter { it.cycleId == cycle.id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Pending validations count where current user is receiver
    val pendingValidationsForCurrentUser: StateFlow<List<Transaction>> = combine(
        repository.transactions,
        currentUser
    ) { txns, user ->
        if (user == null) emptyList()
        else txns.filter { it.receiverId == user.id && it.status == TransactionStatus.PENDING_VALIDATION }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Group activity items for selected tontine
    val currentActivities: StateFlow<List<ActivityItem>> = combine(
        repository.activities,
        _selectedTontineId
    ) { acts, id ->
        acts.filter { it.tontineId == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Notifications for current user
    val userNotifications: StateFlow<List<AppNotification>> = combine(
        repository.notifications,
        currentUser
    ) { notifs, user ->
        if (user == null) emptyList()
        else notifs.filter { it.userId == user.id || it.userId == "ALL" || it.userId.isEmpty() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unreadNotificationsCount: StateFlow<Int> = userNotifications.map { list ->
        list.count { !it.isRead }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // Cotisation Matrix (Vert = Validé, Jaune = En attente, Rouge = Non payé/Retard, Or/Bleu = Bénéficiaire Exempté)
    val cotisationMatrix: StateFlow<List<MemberCotisationStatus>> = combine(
        currentMembers,
        currentTransactions,
        currentCycle,
        selectedTontine
    ) { members, txns, cycle, tontine ->
        val isExempt = tontine?.isBeneficiaryExempt ?: true
        val beneficiaryId = cycle?.beneficiaryId
        val contributionAmount = tontine?.contributionAmount ?: 0.0
        val penalty = tontine?.latePenaltyFee ?: 0.0

        members.map { member ->
            val isBeneficiarySlot = if (cycle?.beneficiaryMemberId?.isNotBlank() == true) {
                member.id == cycle.beneficiaryMemberId
            } else {
                member.id == cycle?.beneficiaryId || (member.userId == cycle?.beneficiaryId && members.count { it.userId == member.userId } == 1)
            }

            if (isExempt && isBeneficiarySlot) {
                MemberCotisationStatus(
                    member = member,
                    status = CotisationStatus.BENEFICIARY_EXEMPT,
                    amountDue = 0.0,
                    penaltyFee = 0.0
                )
            } else {
                val memberTxn = txns.firstOrNull { txn ->
                    if (txn.senderMemberId.isNotBlank()) {
                        txn.senderMemberId == member.id
                    } else {
                        txn.senderId == member.userId && members.count { it.userId == member.userId } == 1
                    }
                }
                when (memberTxn?.status) {
                    TransactionStatus.VALIDATED -> MemberCotisationStatus(
                        member = member,
                        status = CotisationStatus.VALIDATED,
                        transaction = memberTxn,
                        amountDue = 0.0,
                        penaltyFee = 0.0
                    )
                    TransactionStatus.PENDING_VALIDATION, TransactionStatus.PENDING_AI -> MemberCotisationStatus(
                        member = member,
                        status = CotisationStatus.PENDING,
                        transaction = memberTxn,
                        amountDue = 0.0,
                        penaltyFee = 0.0
                    )
                    else -> {
                        // Membre non payé : Vérifier pénalité d'amende de retard
                        val isLate = member.payoutOrder > 5 || member.id == "tm_7" || member.id == "tm_8" || member.id == "tm_9" || member.id == "tm_10"
                        if (isLate) {
                            MemberCotisationStatus(
                                member = member,
                                status = CotisationStatus.LATE_UNPAID,
                                amountDue = contributionAmount + penalty,
                                penaltyFee = penalty
                            )
                        } else {
                            MemberCotisationStatus(
                                member = member,
                                status = CotisationStatus.UNPAID,
                                amountDue = contributionAmount,
                                penaltyFee = 0.0
                            )
                        }
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectTontine(id: String) {
        _selectedTontineId.value = id
    }

    fun switchUser(userId: String) {
        repository.switchUser(userId)
    }

    fun updateUserProfile(
        fullName: String,
        phoneNumber: String = "",
        avatarUrl: String,
        prefs: NotificationPreferences
    ) {
        val user = currentUser.value ?: return
        val effectivePhone = if (phoneNumber.isNotBlank()) phoneNumber else user.phoneNumber
        repository.updateUserProfile(user.id, fullName, effectivePhone, avatarUrl, prefs)
    }

    fun updateUserProfile(fullName: String, avatarUrl: String, prefs: NotificationPreferences) {
        val user = currentUser.value ?: return
        repository.updateUserProfile(user.id, fullName, user.phoneNumber, avatarUrl, prefs)
    }

    fun deleteProfilePhoto() {
        val user = currentUser.value ?: return
        repository.updateUserProfile(user.id, user.fullName, user.phoneNumber, "", user.preferences)
    }

    fun markNotificationAsRead(id: String) {
        repository.markNotificationAsRead(id)
    }

    fun markAllNotificationsAsRead() {
        val user = currentUser.value ?: return
        repository.markAllNotificationsAsRead(user.id)
    }

    fun simulateNotification(type: NotificationType) {
        val user = currentUser.value ?: return
        val (title, msg) = when (type) {
            NotificationType.DEADLINE -> "Rappel Échéance" to "La date limite de cotisation pour le tour actif arrive à échéance dans 48h !"
            NotificationType.PAYOUT_TURN -> "C'est votre tour d'encaisser !" to "Félicitations, vous recevrez la cagnotte ce tour-ci !"
            NotificationType.VALIDATION -> "Paiement Validé" to "Votre reçu Mobile Money a été validé."
            NotificationType.INVITATION -> "Nouvelle Tontine" to "Vous avez été invité(e) dans une nouvelle tontine."
            NotificationType.SYSTEM -> "Information TontinePay" to "L'ordre de passage officiel a été mis à jour par l'administrateur."
        }
        repository.triggerNotification(user.id, type, title, msg, _selectedTontineId.value)
    }

    fun inviteUserByPhone(
        phoneNumber: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val tId = _selectedTontineId.value
        if (tId.isEmpty()) {
            onError("Aucune tontine sélectionnée")
            return
        }
        val result = repository.inviteUserByPhone(tId, phoneNumber)
        result.onSuccess(onSuccess).onFailure { onError(it.message ?: "Erreur d'invitation") }
    }

    fun joinTontineByCode(
        code: String,
        onSuccess: (Tontine) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = currentUser.value
        if (user == null) {
            onError("Veuillez d'abord créer votre profil utilisateur")
            return
        }
        val result = repository.joinTontineByInviteCode(code, user)
        result.onSuccess {
            _selectedTontineId.value = it.id
            onSuccess(it)
        }.onFailure {
            onError(it.message ?: "Code d'invitation introuvable")
        }
    }

    fun leaveTontine(
        tontineId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = currentUser.value ?: run {
            onError("Utilisateur non connecté")
            return
        }
        val result = repository.leaveTontine(tontineId, user.id)
        result.onSuccess {
            if (_selectedTontineId.value == tontineId) {
                _selectedTontineId.value = ""
            }
            onSuccess()
        }.onFailure {
            onError(it.message ?: "Impossible de quitter la tontine")
        }
    }

    fun resetOcrState() {
        _ocrState.value = OcrScanUiState.Idle
    }

    fun isDuplicateTransactionId(transactionId: String): Boolean =
        repository.isDuplicateTransactionId(transactionId)

    // OCR Analysis with Hybrid ML Kit + Gemini Vision
    fun analyzeReceipt(bitmap: Bitmap, hintProvider: String? = null) {
        viewModelScope.launch {
            _ocrState.value = OcrScanUiState.Scanning("Analyse OCR et extraction des critères de paiement...")
            delay(300)

            val result = geminiService.analyzeReceipt(bitmap, hintProvider)
            result.onSuccess { data ->
                _ocrState.value = OcrScanUiState.Success(data)
            }.onFailure { err ->
                _ocrState.value = OcrScanUiState.GeneralError(err.message ?: "Échec de lecture du reçu")
            }
        }
    }

    fun submitTransaction(
        extractedData: ExtractedReceiptData,
        senderMemberId: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cycle = currentCycle.value ?: run {
            onError("Aucun cycle actif trouvé")
            return
        }
        val user = currentUser.value ?: run {
            onError("Veuillez d'abord créer votre profil utilisateur")
            return
        }

        val currentTontineId = _selectedTontineId.value
        val allMembersInTontine = repository.members.value[currentTontineId] ?: emptyList()
        val userSlots = allMembersInTontine.filter { it.userId == user.id && !it.isPendingApproval }
        val effectiveSlotId = senderMemberId ?: _activeSlotMemberId.value ?: userSlots.firstOrNull()?.id ?: ""
        val effectiveSlot = userSlots.find { it.id == effectiveSlotId }
        val effectiveSenderName = if (effectiveSlot != null) effectiveSlot.getPartDisplayName(allMembersInTontine) else user.fullName

        val result = repository.submitTransaction(
            cycleId = cycle.id,
            sender = user,
            receiverId = cycle.beneficiaryId,
            receiverName = cycle.beneficiaryName,
            receiverPhone = cycle.beneficiaryPhone,
            amount = extractedData.amount,
            currency = extractedData.currency,
            extractedData = extractedData,
            senderMemberId = effectiveSlotId,
            receiverMemberId = cycle.beneficiaryMemberId,
            senderDisplayName = effectiveSenderName
        )

        result.onSuccess {
            _ocrState.value = OcrScanUiState.Idle
            onSuccess()
        }.onFailure {
            onError(it.message ?: "Erreur de soumission")
        }
    }

    fun validatePayment(txnId: String) {
        repository.validateTransaction(txnId)
    }

    fun rejectPayment(txnId: String, reason: String) {
        repository.rejectTransaction(txnId, reason)
    }

    fun createTontine(
        name: String,
        description: String,
        amount: Double,
        frequency: TontineFrequency,
        currency: String,
        memberCount: Int,
        startDate: String = "01 du mois",
        endDate: String = "25 du mois",
        latePenaltyFee: Double = 0.0,
        isBeneficiaryExempt: Boolean = true,
        isPendingLaunch: Boolean = false,
        autoRotation: Boolean = false
    ) {
        val newTontine = repository.createTontine(
            name, description, amount, frequency, currency, memberCount, startDate, endDate, latePenaltyFee, isBeneficiaryExempt, isPendingLaunch, autoRotation
        )
        _selectedTontineId.value = newTontine.id
    }

    fun updateTontineSettings(
        name: String,
        description: String,
        amount: Double,
        frequency: TontineFrequency,
        currency: String,
        startDate: String,
        endDate: String,
        latePenaltyFee: Double,
        isBeneficiaryExempt: Boolean = true,
        autoRotation: Boolean = false,
        isPendingLaunch: Boolean = false
    ) {
        val tId = _selectedTontineId.value
        if (tId.isNotEmpty()) {
            repository.updateTontineSettings(
                tId, name, description, amount, frequency, currency, startDate, endDate, latePenaltyFee, isBeneficiaryExempt, autoRotation, isPendingLaunch
            )
        }
    }

    // Active Profile Slot in current Tontine (Feedback E)
    private val _activeSlotMemberId = MutableStateFlow<String?>(null)
    val activeSlotMemberId: StateFlow<String?> = _activeSlotMemberId.asStateFlow()

    fun setActiveSlot(memberId: String) {
        _activeSlotMemberId.value = memberId
    }

    fun checkForServerUpdate(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            delay(600)
            val current = _currentAppVersion.value
            val serverVer = try {
                val doc = repository.firestore?.collection("system")?.document("app_update")?.get()?.await()
                doc?.getString("latestVersion") ?: CURRENT_VERSION_STRING
            } catch (e: Exception) {
                CURRENT_VERSION_STRING
            }
            _latestServerVersion.value = serverVer
            val hasNew = isNewerVersion(serverVer, current)
            _isUpdateAvailable.value = hasNew
            _isCheckingUpdate.value = false
            onComplete?.invoke(hasNew)
        }
    }

    fun dismissUpdateBanner() {
        _isUpdateAvailable.value = false
    }

    fun downloadAndApplyUpdate(context: Context? = null, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val rawUrl = repository.serverDownloadUrl.value.trim()
            if (rawUrl.isBlank() || rawUrl.contains("europe-west2.run.app")) {
                // If it is the default placeholder or empty, prompt the user/admin to configure the real APK URL
                _showDownloadConfigModal.value = true
                onComplete()
                return@launch
            }

            _isDownloadingUpdate.value = true
            _updateProgress.value = 0f
            for (step in 1..10) {
                delay(80)
                _updateProgress.value = step / 10f
            }
            _isDownloadingUpdate.value = false

            if (context != null) {
                if (rawUrl.endsWith(".apk", ignoreCase = true)) {
                    try {
                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                        if (dm != null) {
                            val request = DownloadManager.Request(Uri.parse(rawUrl))
                                .setTitle("TontinePay-${_latestServerVersion.value}.apk")
                                .setDescription("Téléchargement de la mise à jour TontinePay")
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setAllowedOverMetered(true)
                                .setAllowedOverRoaming(true)
                                .setDestinationInExternalPublicDir(
                                    Environment.DIRECTORY_DOWNLOADS,
                                    "TontinePay-${_latestServerVersion.value}.apk"
                                )
                            dm.enqueue(request)
                            Toast.makeText(context, "Téléchargement de l'APK lancé (vérifiez vos notifications Android)", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        openBrowserUrl(context, rawUrl)
                    }
                } else {
                    openBrowserUrl(context, rawUrl)
                }
            }
            onComplete()
        }
    }

    private fun openBrowserUrl(context: Context, url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            Log.w("TontineViewModel", "Could not open URL: ${e.message}")
        }
    }

    fun restartAppAfterUpdate(context: Context? = null) {
        _isUpdateReadyToRestart.value = false
        _isUpdateAvailable.value = false
        _currentAppVersion.value = sessionManager.getAppliedVersion()
        refreshData()
        if (context != null) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(intent)
                    if (context is Activity) {
                        context.finish()
                    }
                    Runtime.getRuntime().exit(0)
                }
            } catch (e: Exception) {
                Log.e("TontineViewModel", "Restart fallback", e)
            }
        }
    }

    fun launchTontine(tontineId: String) {
        repository.launchTontine(tontineId)
    }

    fun deleteCurrentTontine() {
        val tId = _selectedTontineId.value
        if (tId.isNotEmpty()) {
            repository.deleteTontine(tId)
            val remaining = repository.tontines.value
            _selectedTontineId.value = remaining.firstOrNull()?.id ?: ""
        }
    }

    fun removeMember(memberId: String) {
        val tId = _selectedTontineId.value
        if (tId.isNotEmpty()) {
            repository.removeMember(tId, memberId)
            if (_activeSlotMemberId.value == memberId) {
                val remaining = repository.members.value[tId]?.filter { it.userId == currentUser.value?.id && it.id != memberId }
                _activeSlotMemberId.value = remaining?.firstOrNull()?.id
            }
        }
    }

    fun reorderMember(memberId: String, newOrder: Int) {
        val tId = _selectedTontineId.value
        if (!tId.isNullOrEmpty()) {
            repository.reorderMember(tId, memberId, newOrder)
        }
    }

    fun reorderAllMembers(orderedMemberIds: List<String>) {
        val tId = _selectedTontineId.value
        if (!tId.isNullOrEmpty()) {
            repository.reorderAllMembers(tId, orderedMemberIds)
        }
    }

    fun addMember(fullName: String, phone: String) {
        val tontineId = _selectedTontineId.value
        val userId = currentUser.value?.id
        if (tontineId.isNotEmpty()) {
            repository.addMember(tontineId, fullName, phone, requestedByUserId = userId)
        }
    }

    fun setCoAdmin(tontineId: String, memberId: String?) {
        repository.setCoAdmin(tontineId, memberId)
    }

    fun approvePendingMember(tontineId: String, memberId: String) {
        repository.approvePendingMember(tontineId, memberId)
    }

    fun rejectPendingMember(tontineId: String, memberId: String) {
        repository.rejectPendingMember(tontineId, memberId)
    }

    fun addAdditionalProfileSlot(
        aliasLabel: String,
        onSuccess: (TontineMember) -> Unit,
        onError: (String) -> Unit
    ) {
        val tId = _selectedTontineId.value
        val user = currentUser.value
        if (tId.isEmpty() || user == null) {
            onError("Opération impossible")
            return
        }
        val result = repository.addAdditionalProfileSlot(tId, user.id, aliasLabel)
        result.onSuccess {
            _activeSlotMemberId.value = it.id
            onSuccess(it)
        }.onFailure {
            onError(it.message ?: "Erreur lors de l'ajout de la part")
        }
    }

    fun testNotificationSystem() {
        val user = currentUser.value
        val title = "🔔 Test Notifications TontinePay"
        val message = "Le système d'alertes en temps réel et notifications push fonctionne parfaitement !"
        val notifId = (System.currentTimeMillis() % 100000).toInt()
        NotificationHelper.showSystemNotification(
            context = getApplication<android.app.Application>(),
            notificationId = notifId,
            title = title,
            message = message,
            tontineId = _selectedTontineId.value.ifBlank { null }
        )
        if (user != null) {
            repository.triggerNotification(
                userId = user.id,
                type = NotificationType.SYSTEM,
                title = title,
                message = message,
                tontineId = _selectedTontineId.value.ifBlank { null }
            )
        }
    }
}
