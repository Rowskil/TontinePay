package com.example.data

import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.db.UserEntity
import com.example.model.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class TontineRepository(
    private val database: AppDatabase? = null,
    val firestore: FirebaseFirestore? = null,
    private val sessionManager: SessionManager? = null
) {

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val allUsers: List<User> get() = _users.value
    val usersFlow: StateFlow<List<User>> = _users.asStateFlow()

    // App Update broadcast
    private val _serverAppVersion = MutableStateFlow("v1.5.0")
    val serverAppVersion: StateFlow<String> = _serverAppVersion.asStateFlow()

    private val _serverDownloadUrl = MutableStateFlow("https://ais-pre-3k5lkqv7ovqerb6mqswtsl-961012230089.europe-west2.run.app")
    val serverDownloadUrl: StateFlow<String> = _serverDownloadUrl.asStateFlow()

    private val _serverReleaseNotes = MutableStateFlow("Version 1.5.0 : Gestion avancée des cycles, ordre de passage par glissement fluide, parts multiples et contrôles anti-fraude.")
    val serverReleaseNotes: StateFlow<String> = _serverReleaseNotes.asStateFlow()

    // Current logged in user (null on clean first launch until onboarding account creation)
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Passwords stored securely per normalized phone
    private val _passwords = mutableMapOf<String, String>()

    // Tontines
    private val _tontines = MutableStateFlow<List<Tontine>>(emptyList())
    val tontines: StateFlow<List<Tontine>> = _tontines.asStateFlow()

    // Members per tontine
    private val _members = MutableStateFlow<Map<String, List<TontineMember>>>(emptyMap())
    val members: StateFlow<Map<String, List<TontineMember>>> = _members.asStateFlow()

    // Cycles per tontine
    private val _cycles = MutableStateFlow<Map<String, List<TontineCycle>>>(emptyMap())
    val cycles: StateFlow<Map<String, List<TontineCycle>>> = _cycles.asStateFlow()

    // Transactions per cycle
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    // Realtime Activity Feed
    private val _activities = MutableStateFlow<List<ActivityItem>>(emptyList())
    val activities: StateFlow<List<ActivityItem>> = _activities.asStateFlow()

    // Notifications
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    init {
        // 0. Synchronous persistent session check (solves Feedback A.1 & A.2)
        val savedUser = sessionManager?.getActiveUser()
        if (savedUser != null) {
            _currentUser.value = savedUser
        }

        // 1. Local SQLite Room fallback / fast initial cache
        if (database != null) {
            repositoryScope.launch {
                try {
                    val dbUsers = database.userDao().getAllUsers()
                    if (dbUsers.isNotEmpty()) {
                        _users.value = dbUsers.map { it.toUser() }
                        dbUsers.forEach {
                            _passwords[it.phoneNumber.replace(" ", "")] = it.password
                        }
                        val active = dbUsers.find { it.isActiveUser }
                        if (_currentUser.value == null && active != null) {
                            val u = active.toUser()
                            _currentUser.value = u
                            sessionManager?.saveUserSession(u)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TontineRepository", "Error loading local Room DB", e)
                }
            }
        }

        // 2. Cloud Firestore Realtime Sync across all physical devices
        if (firestore != null) {
            initFirestoreRealtimeListeners(firestore)

            // D.1: Purge any tontine called 'Ma Tontine Test' permanently from Firestore
            firestore.collection("tontines").whereEqualTo("name", "Ma Tontine Test").get()
                .addOnSuccessListener { snap ->
                    for (doc in snap.documents) {
                        doc.reference.collection("members").get().addOnSuccessListener { mSnap ->
                            mSnap.documents.forEach { it.reference.delete() }
                        }
                        doc.reference.collection("cycles").get().addOnSuccessListener { cSnap ->
                            cSnap.documents.forEach { it.reference.delete() }
                        }
                        doc.reference.delete()
                        Log.d("TontineRepository", "Purged test tontine ${doc.id}")
                    }
                }
        }
    }

    private fun initFirestoreRealtimeListeners(fs: FirebaseFirestore) {
        // Broadcast and synchronize latest version to system/app_update (Enables all older versions to detect v1.6.0)
        val updatePayload = hashMapOf<String, Any>(
            "latestVersion" to "v1.6.0",
            "versionCode" to 160,
            "releaseNotes" to "Version 1.6.0 : Nouveau logo officiel TP, tours automatiques, fréquence quotidienne, calendrier intégré et micro-animations tactiles.",
            "downloadUrl" to "https://ais-pre-3k5lkqv7ovqerb6mqswtsl-961012230089.europe-west2.run.app",
            "updatedAt" to System.currentTimeMillis()
        )
        fs.collection("system").document("app_update").set(updatePayload, SetOptions.merge())

        // Broadcast to general notifications collection so that all older versions (v1.2.5, v1.5.0) receive it
        val updateNotifDoc = fs.collection("notifications").document("app_update_announcement_v1_6_0")
        updateNotifDoc.get().addOnSuccessListener { docSnap ->
            if (!docSnap.exists()) {
                val notifData = hashMapOf<String, Any>(
                    "userId" to "ALL",
                    "tontineId" to "",
                    "type" to "SYSTEM",
                    "title" to "🚀 Mise à jour v1.6.0 disponible",
                    "message" to "La nouvelle version v1.6.0 de TontinePay est disponible avec le logo officiel modernisé, les tours automatiques et des animations tactiles fluides !",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false
                )
                updateNotifDoc.set(notifData)
            }
        }

        fs.collection("system").document("app_update").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("TontineRepository", "app_update listener error", error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val ver = snapshot.getString("latestVersion") ?: "v1.6.0"
                val url = snapshot.getString("downloadUrl") ?: "https://ais-pre-3k5lkqv7ovqerb6mqswtsl-961012230089.europe-west2.run.app"
                val notes = snapshot.getString("releaseNotes") ?: ""
                _serverAppVersion.value = ver
                _serverDownloadUrl.value = url
                _serverReleaseNotes.value = notes
            }
        }

        // Listen to all users
        fs.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("TontineRepository", "Firestore users listener error", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val cloudUsers = mutableListOf<User>()
                for (doc in snapshot.documents) {
                    val id = doc.getString("id") ?: doc.id
                    val fullName = doc.getString("fullName") ?: ""
                    val phone = doc.getString("phoneNumber") ?: ""
                    val avatar = doc.getString("avatarUrl") ?: ""
                    val pass = doc.getString("password") ?: ""
                    val normPhone = (doc.getString("normPhone") ?: phone).replace(" ", "")

                    if (pass.isNotBlank()) {
                        _passwords[normPhone] = pass
                    }

                    cloudUsers.add(
                        User(
                            id = id,
                            fullName = fullName,
                            phoneNumber = phone,
                            avatarUrl = avatar,
                            preferences = NotificationPreferences(
                                notifyInvitations = doc.getBoolean("notifyInvitations") ?: true,
                                notifyDeadlines = doc.getBoolean("notifyDeadlines") ?: true,
                                notifyValidations = doc.getBoolean("notifyValidations") ?: true,
                                notifyPayoutTurn = doc.getBoolean("notifyPayoutTurn") ?: true
                            )
                        )
                    )
                }
                if (cloudUsers.isNotEmpty()) {
                    _users.value = cloudUsers
                    val current = _currentUser.value ?: sessionManager?.getActiveUser()
                    if (current != null) {
                        val matched = cloudUsers.find { 
                            it.id == current.id || it.phoneNumber.replace(" ", "") == current.phoneNumber.replace(" ", "") 
                        }
                        if (matched != null) {
                            _currentUser.value = matched
                            sessionManager?.saveUserSession(matched)
                        }
                    }
                }
            }
        }

        // Listen to all cloud tontines
        fs.collection("tontines").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("TontineRepository", "Firestore tontines listener error", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val cloudTontines = mutableListOf<Tontine>()
                for (doc in snapshot.documents) {
                    val id = doc.id
                    val name = doc.getString("name") ?: ""
                    // D.1: Permanently ignore and delete 'Ma Tontine Test'
                    if (name.equals("Ma Tontine Test", ignoreCase = true) || id.contains("test", ignoreCase = true)) {
                        doc.reference.collection("members").get().addOnSuccessListener { mSnap ->
                            mSnap.documents.forEach { it.reference.delete() }
                        }
                        doc.reference.collection("cycles").get().addOnSuccessListener { cSnap ->
                            cSnap.documents.forEach { it.reference.delete() }
                        }
                        doc.reference.delete()
                        continue
                    }
                    val desc = doc.getString("description") ?: ""
                    val creator = doc.getString("creatorId") ?: ""
                    val amount = doc.getDouble("contributionAmount") ?: 0.0
                    val freqStr = doc.getString("frequency") ?: "MONTHLY"
                    val freq = try { TontineFrequency.valueOf(freqStr) } catch (e: Exception) { TontineFrequency.MONTHLY }
                    val curr = doc.getString("currency") ?: "XAF"
                    val code = doc.getString("inviteCode") ?: ""
                    val membersCount = doc.getLong("totalMembers")?.toInt() ?: 1
                    val startDate = doc.getString("startDate") ?: "01 du mois"
                    val endDate = doc.getString("endDate") ?: "25 du mois"
                    val penalty = doc.getDouble("latePenaltyFee") ?: 0.0
                    val isExempt = doc.getBoolean("isBeneficiaryExempt") ?: true
                    val isPending = doc.getBoolean("isPendingLaunch") ?: desc.contains("Lancement prévu", ignoreCase = true)
                    val autoRotation = doc.getBoolean("autoRotation") ?: false
                    val coAdminId = doc.getString("coAdminId")
                    val coAdminName = doc.getString("coAdminName")
                    val nextTurnStartDate = doc.getString("nextTurnStartDate")
                    val nextTurnBeneficiaryName = doc.getString("nextTurnBeneficiaryName")
                    val activeCycleNum = doc.getLong("activeCycleNumber")?.toInt() ?: 1
                    val totalCycles = doc.getLong("totalCycles")?.toInt() ?: membersCount
                    val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

                    val t = Tontine(
                        id = id,
                        name = name,
                        description = desc,
                        creatorId = creator,
                        contributionAmount = amount,
                        frequency = freq,
                        currency = curr,
                        inviteCode = code,
                        totalMembers = membersCount,
                        activeCycleNumber = activeCycleNum,
                        totalCycles = totalCycles,
                        startDate = startDate,
                        endDate = endDate,
                        latePenaltyFee = penalty,
                        isBeneficiaryExempt = isExempt,
                        coAdminId = coAdminId,
                        coAdminName = coAdminName,
                        nextTurnStartDate = nextTurnStartDate,
                        nextTurnBeneficiaryName = nextTurnBeneficiaryName,
                        isPendingLaunch = isPending,
                        autoRotation = autoRotation,
                        createdAt = createdAt
                    )
                    cloudTontines.add(t)

                    // Listen to members subcollection for this tontine
                    fs.collection("tontines").document(id).collection("members")
                        .addSnapshotListener { memberSnap, _ ->
                            if (memberSnap != null) {
                                val memberList = memberSnap.documents.mapNotNull { mDoc ->
                                    val mId = mDoc.id
                                    val uId = mDoc.getString("userId") ?: ""
                                    val fName = mDoc.getString("fullName") ?: ""
                                    val mPhone = mDoc.getString("phoneNumber") ?: ""
                                    val roleStr = mDoc.getString("role") ?: "MEMBER"
                                    val role = try { MemberRole.valueOf(roleStr) } catch (e: Exception) { MemberRole.MEMBER }
                                    val order = mDoc.getLong("payoutOrder")?.toInt() ?: 1
                                    TontineMember(mId, id, uId, fName, mPhone, role, order)
                                }.sortedBy { it.payoutOrder }
                                _members.update { it + (id to memberList) }
                            }
                        }

                    // Listen to cycles subcollection for this tontine
                    fs.collection("tontines").document(id).collection("cycles")
                        .addSnapshotListener { cycleSnap, _ ->
                            if (cycleSnap != null) {
                                val cycleList = cycleSnap.documents.mapNotNull { cDoc ->
                                    val cId = cDoc.id
                                    val cNum = cDoc.getLong("cycleNumber")?.toInt() ?: 1
                                    val bId = cDoc.getString("beneficiaryId") ?: ""
                                    val bMemberId = cDoc.getString("beneficiaryMemberId") ?: ""
                                    val bName = cDoc.getString("beneficiaryName") ?: ""
                                    val bPhone = cDoc.getString("beneficiaryPhone") ?: ""
                                    val due = cDoc.getString("dueDate") ?: ""
                                    val statusStr = cDoc.getString("status") ?: "ACTIVE"
                                    val status = try { CycleStatus.valueOf(statusStr) } catch (e: Exception) { CycleStatus.ACTIVE }
                                    val potExp = cDoc.getDouble("potExpected") ?: 0.0
                                    val potCol = cDoc.getDouble("potCollected") ?: 0.0
                                    val cCurr = cDoc.getString("currency") ?: curr
                                    TontineCycle(
                                        id = cId,
                                        tontineId = id,
                                        cycleNumber = cNum,
                                        beneficiaryId = bId,
                                        beneficiaryMemberId = bMemberId,
                                        beneficiaryName = bName,
                                        beneficiaryPhone = bPhone,
                                        dueDate = due,
                                        status = status,
                                        potExpected = potExp,
                                        potCollected = potCol,
                                        currency = cCurr
                                    )
                                }
                                _cycles.update { it + (id to cycleList) }
                            }
                        }
                }
                _tontines.value = cloudTontines
            }
        }

        // Listen to all cloud transactions
        fs.collection("transactions").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("TontineRepository", "Firestore transactions listener error", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val txnList = snapshot.documents.mapNotNull { doc ->
                    val id = doc.id
                    val cycleId = doc.getString("cycleId") ?: ""
                    val sId = doc.getString("senderId") ?: ""
                    val sName = doc.getString("senderName") ?: ""
                    val sPhone = doc.getString("senderPhone") ?: ""
                    val rId = doc.getString("receiverId") ?: ""
                    val rName = doc.getString("receiverName") ?: ""
                    val rPhone = doc.getString("receiverPhone") ?: ""
                    val amount = doc.getDouble("amount") ?: 0.0
                    val currency = doc.getString("currency") ?: "XAF"
                    val statusStr = doc.getString("status") ?: "PENDING_VALIDATION"
                    val status = try { TransactionStatus.valueOf(statusStr) } catch (e: Exception) { TransactionStatus.PENDING_VALIDATION }
                    val txnRef = doc.getString("transactionId") ?: ""
                    val provider = doc.getString("provider") ?: "Mobile Money"
                    val timestamp = doc.getString("timestamp") ?: ""
                    val rejectionReason = doc.getString("rejectionReason")
                    val validatedAt = doc.getLong("validatedAt")
                    val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

                    Transaction(
                        id = id,
                        cycleId = cycleId,
                        senderId = sId,
                        senderName = sName,
                        senderPhone = sPhone,
                        receiverId = rId,
                        receiverName = rName,
                        receiverPhone = rPhone,
                        amount = amount,
                        currency = currency,
                        extractedData = ExtractedReceiptData(
                            transactionId = txnRef,
                            amount = amount,
                            currency = currency,
                            senderPhone = sPhone,
                            recipientPhone = rPhone,
                            provider = provider,
                            timestamp = timestamp,
                            confidence = 0.98f,
                            rawJson = ""
                        ),
                        status = status,
                        rejectionReason = rejectionReason,
                        validatedAt = validatedAt,
                        createdAt = createdAt
                    )
                }.sortedByDescending { it.createdAt }

                _transactions.value = txnList
            }
        }

        // Listen to group activity
        fs.collection("activities").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                val actList = snapshot.documents.mapNotNull { doc ->
                    val id = doc.id
                    val tId = doc.getString("tontineId") ?: ""
                    val typeStr = doc.getString("type") ?: "PAYMENT_SUBMITTED"
                    val type = try { ActivityType.valueOf(typeStr) } catch (e: Exception) { ActivityType.PAYMENT_SUBMITTED }
                    val title = doc.getString("title") ?: ""
                    val desc = doc.getString("description") ?: ""
                    val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    ActivityItem(id, tId, type, title, desc, ts)
                }.sortedByDescending { it.timestamp }
                _activities.value = actList
            }
        }

        // Listen to all notifications in real-time across devices
        fs.collection("notifications").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("TontineRepository", "Firestore notifications listener error", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val cloudNotifs = snapshot.documents.mapNotNull { doc ->
                    val id = doc.id
                    val uId = doc.getString("userId") ?: ""
                    val tId = doc.getString("tontineId")
                    val typeStr = doc.getString("type") ?: "SYSTEM"
                    val type = try { NotificationType.valueOf(typeStr) } catch (e: Exception) { NotificationType.SYSTEM }
                    val title = doc.getString("title") ?: ""
                    val message = doc.getString("message") ?: ""
                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    val isRead = doc.getBoolean("isRead") ?: false
                    AppNotification(id, uId, tId, type, title, message, timestamp, isRead)
                }.sortedByDescending { it.timestamp }

                val currentUserId = _currentUser.value?.id
                if (currentUserId != null) {
                    val previousIds = _notifications.value.map { it.id }.toSet()
                    val newlyArrived = cloudNotifs.filter {
                        (it.userId == currentUserId || it.userId == "ALL" || it.userId.isEmpty()) &&
                        !it.isRead &&
                        (it.id !in previousIds || sessionManager?.isNotificationDispatched(it.id) == false)
                    }
                    newlyArrived.forEach { notif ->
                        onNotificationArrived?.invoke(notif)
                    }
                }

                _notifications.value = cloudNotifs
            }
        }
    }

    fun updateServerDownloadUrl(url: String) {
        val trimmed = url.trim()
        _serverDownloadUrl.value = trimmed
        firestore?.collection("system")?.document("app_update")?.update("downloadUrl", trimmed)
    }

    var onNotificationArrived: ((AppNotification) -> Unit)? = null

    fun switchUser(userId: String) {
        val user = _users.value.find { it.id == userId } ?: return
        _currentUser.value = user
        sessionManager?.saveUserSession(user)
        if (database != null) {
            repositoryScope.launch {
                database.userDao().clearActiveUser()
                database.userDao().setActiveUser(userId)
            }
        }
    }

    // Register a new user account with phone and password
    fun registerUser(
        fullName: String,
        phoneNumber: String,
        password: String,
        avatarUrl: String = ""
    ): Result<User> {
        val cleanName = fullName.trim()
        val cleanPhone = phoneNumber.trim()
        val normPhone = cleanPhone.replace(" ", "")

        if (_users.value.any { it.phoneNumber.replace(" ", "") == normPhone }) {
            return Result.failure(IllegalArgumentException("Un compte existe déjà avec ce numéro Mobile Money. Veuillez vous connecter."))
        }

        val newUserId = "u_" + UUID.randomUUID().toString().take(8)
        val newUser = User(
            id = newUserId,
            phoneNumber = cleanPhone,
            fullName = cleanName,
            avatarUrl = avatarUrl,
            preferences = NotificationPreferences()
        )

        _passwords[normPhone] = password
        _users.update { it + newUser }
        _currentUser.value = newUser
        sessionManager?.saveUserSession(newUser)

        // Persist locally in Room
        if (database != null) {
            repositoryScope.launch {
                try {
                    database.userDao().clearActiveUser()
                    database.userDao().insertUser(UserEntity.fromUser(newUser, password = password, isActive = true))
                } catch (e: Exception) {
                    Log.e("TontineRepository", "Error saving user to Room", e)
                }
            }
        }

        // Persist to Cloud Firestore for multi-device sync
        if (firestore != null) {
            val userMap = hashMapOf<String, Any>(
                "id" to newUserId,
                "fullName" to cleanName,
                "phoneNumber" to cleanPhone,
                "normPhone" to normPhone,
                "password" to password,
                "avatarUrl" to avatarUrl,
                "notifyInvitations" to true,
                "notifyDeadlines" to true,
                "notifyValidations" to true,
                "notifyPayoutTurn" to true,
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection("users").document(newUserId).set(userMap)
                .addOnFailureListener { Log.e("TontineRepository", "Firestore user register failed", it) }
        }

        return Result.success(newUser)
    }

    // Login with phone and password
    fun loginUser(phoneNumber: String, password: String): Result<User> {
        val normPhone = phoneNumber.trim().replace(" ", "")
        val user = _users.value.find { it.phoneNumber.replace(" ", "") == normPhone }
            ?: return Result.failure(IllegalArgumentException("Aucun compte trouvé avec ce numéro Mobile Money. Veuillez créer un compte."))

        val savedPassword = _passwords[normPhone]
        if (savedPassword != null && savedPassword.isNotBlank() && savedPassword != password) {
            return Result.failure(IllegalArgumentException("Mot de passe incorrect."))
        }

        _currentUser.value = user
        sessionManager?.saveUserSession(user)
        if (database != null) {
            repositoryScope.launch {
                try {
                    database.userDao().clearActiveUser()
                    database.userDao().setActiveUser(user.id)
                } catch (e: Exception) {
                    Log.e("TontineRepository", "Error setting active user in Room", e)
                }
            }
        }

        return Result.success(user)
    }

    // Reset password by phone number
    fun resetPassword(phoneNumber: String, newPassword: String): Result<Unit> {
        val cleanPhone = phoneNumber.trim()
        val normPhone = cleanPhone.replace(" ", "")
        val user = _users.value.find { it.phoneNumber.replace(" ", "") == normPhone }
            ?: return Result.failure(IllegalArgumentException("Aucun compte associé à ce numéro Mobile Money."))

        _passwords[normPhone] = newPassword
        if (database != null) {
            repositoryScope.launch {
                try {
                    database.userDao().updatePasswordByPhone(cleanPhone, newPassword)
                    database.userDao().updatePasswordByPhone(normPhone, newPassword)
                } catch (e: Exception) {
                    Log.e("TontineRepository", "Error updating password in Room", e)
                }
            }
        }

        if (firestore != null) {
            firestore.collection("users").document(user.id).update("password", newPassword)
        }

        return Result.success(Unit)
    }

    // Logout
    fun logout() {
        _currentUser.value = null
        sessionManager?.clearSession()
        if (database != null) {
            repositoryScope.launch {
                database.userDao().clearActiveUser()
            }
        }
    }

    // Delete Account (Feedback D.1: Deep clean all database records and memberships)
    fun deleteAccount(userId: String) {
        val user = _users.value.find { it.id == userId }
        val normPhone = user?.phoneNumber?.replace(" ", "") ?: ""

        if (normPhone.isNotBlank()) {
            _passwords.remove(normPhone)
        }
        _users.update { list -> list.filter { it.id != userId } }
        _currentUser.value = null
        sessionManager?.clearSession()

        // 1. Clean up memberships from all local tontines
        val currentTontineMembers = _members.value
        val updatedMembers = mutableMapOf<String, List<TontineMember>>()
        currentTontineMembers.forEach { (tId, mList) ->
            val remaining = mList.filter { it.userId != userId && it.phoneNumber.replace(" ", "") != normPhone }
            updatedMembers[tId] = remaining
        }
        _members.value = updatedMembers

        // Remove tontines where the user was the sole member/creator
        _tontines.update { list ->
            list.filter { t ->
                val remaining = updatedMembers[t.id] ?: emptyList()
                !(t.creatorId == userId && remaining.isEmpty())
            }
        }

        // 2. Local SQLite Room cleanup
        if (database != null) {
            repositoryScope.launch {
                try {
                    database.userDao().deleteUser(userId)
                    database.userDao().clearActiveUser()
                } catch (e: Exception) {
                    Log.e("TontineRepository", "Error clearing Room user", e)
                }
            }
        }

        // 3. Cloud Firestore deep clean
        if (firestore != null) {
            firestore.collection("users").document(userId).delete()

            _tontines.value.forEach { tontine ->
                firestore.collection("tontines").document(tontine.id).collection("members")
                    .whereEqualTo("userId", userId)
                    .get()
                    .addOnSuccessListener { snap ->
                        snap.documents.forEach { it.reference.delete() }
                    }

                if (tontine.creatorId == userId) {
                    firestore.collection("tontines").document(tontine.id).delete()
                }
            }
        }
    }

    // Legacy createUser helper for existing unit tests
    fun createUser(fullName: String, phoneNumber: String, avatarUrl: String = ""): User {
        return registerUser(fullName, phoneNumber, "1234", avatarUrl).getOrElse {
            _users.value.first { it.phoneNumber.replace(" ", "") == phoneNumber.trim().replace(" ", "") }
        }
    }

    // Reset all data
    fun resetAllData() {
        _users.value = emptyList()
        _currentUser.value = null
        _tontines.value = emptyList()
        _members.value = emptyMap()
        _cycles.value = emptyMap()
        _transactions.value = emptyList()
        _activities.value = emptyList()
        _notifications.value = emptyList()
        if (database != null) {
            repositoryScope.launch {
                database.userDao().clearAll()
            }
        }
    }

    // Update user profile
    fun updateUserProfile(
        userId: String,
        fullName: String,
        phoneNumber: String = "",
        avatarUrl: String,
        preferences: NotificationPreferences
    ) {
        val effectivePhone = if (phoneNumber.isNotBlank()) phoneNumber.trim() else ""
        val oldUser = _users.value.find { it.id == userId }
        val cleanName = fullName.trim()
        val isNameChanged = oldUser != null && oldUser.fullName != cleanName
        val isPhoneChanged = oldUser != null && effectivePhone.isNotBlank() && oldUser.phoneNumber != effectivePhone

        // 1. Update in users state
        _users.update { list ->
            list.map {
                if (it.id == userId) {
                    it.copy(
                        fullName = cleanName,
                        phoneNumber = if (effectivePhone.isNotBlank()) effectivePhone else it.phoneNumber,
                        avatarUrl = avatarUrl,
                        preferences = preferences
                    )
                } else it
            }
        }
        if (_currentUser.value?.id == userId) {
            _currentUser.update {
                it?.copy(
                    fullName = cleanName,
                    phoneNumber = if (effectivePhone.isNotBlank()) effectivePhone else it.phoneNumber,
                    avatarUrl = avatarUrl,
                    preferences = preferences
                )
            }
        }

        // 2. Synchronize across all tontines where the user is a member (Feedback 005 D.1 & D.4)
        val userTontines = _members.value.filter { (_, members) -> members.any { it.userId == userId } }.keys

        _members.update { currentMap ->
            currentMap.mapValues { (_, memberList) ->
                memberList.map { m ->
                    if (m.userId == userId) {
                        m.copy(
                            fullName = cleanName,
                            phoneNumber = if (effectivePhone.isNotBlank()) effectivePhone else m.phoneNumber,
                            avatarUrl = avatarUrl
                        )
                    } else m
                }
            }
        }

        // 3. If name or phone changed, notify all tontines where they are a member (Feedback 005 D.1)
        if (isNameChanged || isPhoneChanged) {
            val changeSummary = buildString {
                append("${oldUser?.fullName ?: cleanName} a mis à jour ses coordonnées : ")
                if (isNameChanged) append("Nom : '$cleanName' ")
                if (isPhoneChanged) append("Numéro : '$effectivePhone'")
            }.trim()

            userTontines.forEach { tId ->
                val act = ActivityItem(
                    id = "act_" + UUID.randomUUID().toString().take(8),
                    tontineId = tId,
                    type = ActivityType.MEMBER_JOINED,
                    title = "Coordonnées mises à jour",
                    description = changeSummary,
                    timestamp = System.currentTimeMillis()
                )
                _activities.update { listOf(act) + it }

                // Notify other members of this tontine
                val tontineMembers = _members.value[tId] ?: emptyList()
                tontineMembers.filter { it.userId != userId }.forEach { otherMember ->
                    triggerNotification(
                        userId = otherMember.userId,
                        type = NotificationType.SYSTEM,
                        title = "Coordonnées mises à jour",
                        message = changeSummary,
                        tontineId = tId
                    )
                }
            }
        }

        if (database != null) {
            repositoryScope.launch {
                val target = _users.value.find { it.id == userId }
                if (target != null) {
                    database.userDao().updateUserProfile(
                        userId = userId,
                        fullName = target.fullName,
                        phoneNumber = target.phoneNumber,
                        avatarUrl = target.avatarUrl,
                        notifyInv = target.preferences.notifyInvitations,
                        notifyDead = target.preferences.notifyDeadlines,
                        notifyValid = target.preferences.notifyValidations,
                        notifyPayout = target.preferences.notifyPayoutTurn
                    )
                }
            }
        }

        if (firestore != null) {
            val updates = hashMapOf<String, Any>(
                "fullName" to cleanName,
                "avatarUrl" to avatarUrl,
                "notifyInvitations" to preferences.notifyInvitations,
                "notifyDeadlines" to preferences.notifyDeadlines,
                "notifyValidations" to preferences.notifyValidations,
                "notifyPayoutTurn" to preferences.notifyPayoutTurn
            )
            if (effectivePhone.isNotBlank()) {
                updates["phoneNumber"] = effectivePhone
                updates["normPhone"] = effectivePhone.replace(" ", "")
            }
            firestore.collection("users").document(userId).update(updates)
        }
    }

    // Push Notification trigger
    fun triggerNotification(
        userId: String,
        type: NotificationType,
        title: String,
        message: String,
        tontineId: String? = null
    ) {
        val user = _users.value.find { it.id == userId }
        val prefs = user?.preferences ?: NotificationPreferences()

        val shouldNotify = when (type) {
            NotificationType.INVITATION -> prefs.notifyInvitations
            NotificationType.DEADLINE -> prefs.notifyDeadlines
            NotificationType.VALIDATION -> prefs.notifyValidations
            NotificationType.PAYOUT_TURN -> prefs.notifyPayoutTurn
            NotificationType.SYSTEM -> true
        }

        if (shouldNotify) {
            val newNotif = AppNotification(
                id = "notif_" + UUID.randomUUID().toString().take(8),
                userId = userId,
                tontineId = tontineId,
                type = type,
                title = title,
                message = message,
                timestamp = System.currentTimeMillis(),
                isRead = false
            )
            _notifications.update { listOf(newNotif) + it }

            // If the notification targets the local current user or is broadcast, dispatch local heads-up alert
            if (_currentUser.value?.id == userId || userId == "ALL" || userId.isEmpty()) {
                onNotificationArrived?.invoke(newNotif)
            }

            if (firestore != null) {
                val notifMap = hashMapOf<String, Any>(
                    "id" to newNotif.id,
                    "userId" to userId,
                    "tontineId" to (tontineId ?: ""),
                    "type" to type.name,
                    "title" to title,
                    "message" to message,
                    "timestamp" to newNotif.timestamp,
                    "isRead" to false
                )
                firestore.collection("notifications").document(newNotif.id).set(notifMap)
            }
        }
    }

    fun markNotificationAsRead(id: String) {
        _notifications.update { list ->
            list.map { if (it.id == id) it.copy(isRead = true) else it }
        }
        if (firestore != null) {
            firestore.collection("notifications").document(id).update("isRead", true)
        }
    }

    fun markAllNotificationsAsRead(userId: String) {
        _notifications.update { list ->
            list.map { if (it.userId == userId) it.copy(isRead = true) else it }
        }
    }

    // Anti-duplicate check
    fun isDuplicateTransactionId(transactionId: String): Boolean {
        if (transactionId.isBlank()) return false
        return _transactions.value.any {
            it.extractedData.transactionId.equals(transactionId.trim(), ignoreCase = true) &&
                    (it.status == TransactionStatus.VALIDATED || it.status == TransactionStatus.PENDING_VALIDATION)
        }
    }

    // Submit a new transaction
    fun submitTransaction(
        cycleId: String,
        sender: User,
        receiverId: String,
        receiverName: String,
        receiverPhone: String,
        amount: Double,
        currency: String,
        extractedData: ExtractedReceiptData,
        senderMemberId: String = "",
        receiverMemberId: String = "",
        senderDisplayName: String? = null
    ): Result<Transaction> {
        if (isDuplicateTransactionId(extractedData.transactionId)) {
            return Result.failure(
                IllegalArgumentException("Transaction rejetée : L'identifiant Mobile Money '${extractedData.transactionId}' a déjà été enregistré et validé dans cette tontine !")
            )
        }

        val effectiveSenderName = senderDisplayName?.ifBlank { sender.fullName } ?: sender.fullName
        val newTxn = Transaction(
            id = "txn_" + UUID.randomUUID().toString().take(8),
            cycleId = cycleId,
            senderId = sender.id,
            senderMemberId = senderMemberId,
            senderName = effectiveSenderName,
            senderPhone = sender.phoneNumber,
            receiverId = receiverId,
            receiverMemberId = receiverMemberId,
            receiverName = receiverName,
            receiverPhone = receiverPhone,
            amount = amount,
            currency = currency,
            extractedData = extractedData,
            status = TransactionStatus.PENDING_VALIDATION,
            createdAt = System.currentTimeMillis()
        )

        _transactions.update { listOf(newTxn) + it }

        val tontineId = getTontineIdForCycle(cycleId)

        // Post to activity feed
        val act = ActivityItem(
            id = "act_" + UUID.randomUUID().toString().take(8),
            tontineId = tontineId,
            type = ActivityType.PAYMENT_SUBMITTED,
            title = "Cotisation soumise",
            description = "$effectiveSenderName a envoyé son reçu ${extractedData.provider} ($amount $currency) pour validation.",
            timestamp = System.currentTimeMillis()
        )
        _activities.update { listOf(act) + it }

        // Trigger push notification to receiver
        triggerNotification(
            userId = receiverId,
            type = NotificationType.VALIDATION,
            title = "Nouveau paiement reçu",
            message = "$effectiveSenderName a déposé une preuve de cotisation (${amount.toInt()} $currency). Cliquez pour valider.",
            tontineId = tontineId
        )

        // Save to Firestore cloud
        if (firestore != null) {
            val txMap = hashMapOf<String, Any>(
                "id" to newTxn.id,
                "cycleId" to cycleId,
                "senderId" to sender.id,
                "senderMemberId" to newTxn.senderMemberId,
                "senderName" to effectiveSenderName,
                "senderPhone" to sender.phoneNumber,
                "receiverId" to receiverId,
                "receiverMemberId" to newTxn.receiverMemberId,
                "receiverName" to receiverName,
                "receiverPhone" to receiverPhone,
                "amount" to amount,
                "currency" to currency,
                "status" to TransactionStatus.PENDING_VALIDATION.name,
                "transactionId" to extractedData.transactionId,
                "provider" to extractedData.provider,
                "timestamp" to extractedData.timestamp,
                "createdAt" to newTxn.createdAt
            )
            firestore.collection("transactions").document(newTxn.id).set(txMap)
            val actMap = hashMapOf<String, Any>(
                "id" to act.id,
                "tontineId" to tontineId,
                "type" to act.type.name,
                "title" to act.title,
                "description" to act.description,
                "timestamp" to act.timestamp
            )
            firestore.collection("activities").document(act.id).set(actMap)
        }

        return Result.success(newTxn)
    }

    // Receiver validates a transaction
    fun validateTransaction(txnId: String) {
        val txn = _transactions.value.find { it.id == txnId } ?: return

        _transactions.update { list ->
            list.map {
                if (it.id == txnId) {
                    it.copy(
                        status = TransactionStatus.VALIDATED,
                        validatedAt = System.currentTimeMillis()
                    )
                } else it
            }
        }

        // Update cycle pot
        _cycles.update { map ->
            map.mapValues { (_, cycleList) ->
                cycleList.map { cycle ->
                    if (cycle.id == txn.cycleId) {
                        cycle.copy(potCollected = cycle.potCollected + txn.amount)
                    } else cycle
                }
            }
        }

        val tontineId = getTontineIdForCycle(txn.cycleId)

        // Post automatic confirmation message to group feed
        val act = ActivityItem(
            id = "act_" + UUID.randomUUID().toString().take(8),
            tontineId = tontineId,
            type = ActivityType.PAYMENT_VALIDATED,
            title = "Cotisation validée ✓",
            description = "${txn.receiverName} a confirmé la réception des ${txn.amount.toInt()} ${txn.currency} de ${txn.senderName}.",
            timestamp = System.currentTimeMillis()
        )
        _activities.update { listOf(act) + it }

        // Trigger push notification to sender
        triggerNotification(
            userId = txn.senderId,
            type = NotificationType.VALIDATION,
            title = "Cotisation validée !",
            message = "Votre cotisation de ${txn.amount.toInt()} ${txn.currency} a été validée par ${txn.receiverName}.",
            tontineId = tontineId
        )

        // Check if all members have paid for this cycle (Feedback C.3)
        val tontine = _tontines.value.find { it.id == tontineId }
        val currentCycle = _cycles.value[tontineId]?.find { it.id == txn.cycleId }
        val allMembers = _members.value[tontineId] ?: emptyList()

        if (tontine != null && currentCycle != null && allMembers.isNotEmpty()) {
            val sortedMembers = allMembers.filter { !it.isPendingApproval }.sortedBy { it.payoutOrder }
            val currentBeneficiaryMember = sortedMembers.find {
                (currentCycle.beneficiaryMemberId.isNotBlank() && it.id == currentCycle.beneficiaryMemberId) ||
                it.id == currentCycle.beneficiaryId ||
                it.userId == currentCycle.beneficiaryId
            }
            val payingMembers = if (tontine.isBeneficiaryExempt && currentBeneficiaryMember != null) {
                allMembers.filter { !it.isPendingApproval && it.id != currentBeneficiaryMember.id }
            } else {
                allMembers.filter { !it.isPendingApproval }
            }

            val validatedTxns = _transactions.value
                .filter { it.cycleId == txn.cycleId && (it.status == TransactionStatus.VALIDATED || it.id == txnId) }
            val validatedMemberIds = (validatedTxns.mapNotNull { it.senderMemberId.ifBlank { null } } + listOfNotNull(txn.senderMemberId.ifBlank { null })).toSet()
            val validatedUserIds = (validatedTxns.map { it.senderId } + txn.senderId).toSet()

            val allPaid = payingMembers.all { m ->
                m.id in validatedMemberIds || (m.userId in validatedUserIds && allMembers.count { it.userId == m.userId } == 1)
            }

            if (allPaid) {
                val currentOrder = currentBeneficiaryMember?.payoutOrder ?: currentCycle.cycleNumber
                val nextBeneficiary = sortedMembers.find { it.payoutOrder > currentOrder }

                val payingCount = payingMembers.size
                val calculatedGain = tontine.contributionAmount * payingCount
                val exemptExplanation = if (tontine.isBeneficiaryExempt) " (receveur exempté, $payingCount cotisants)" else " ($payingCount cotisants)"
                val nextStartDate = tontine.nextTurnStartDate ?: tontine.endDate

                if (nextBeneficiary != null) {
                    val nextBeneficiaryName = nextBeneficiary.getPartDisplayName(allMembers)
                    val notifTitle = "🎉 Tour ${currentCycle.cycleNumber} terminé avec succès !"
                    val notifMsg = "Tous les membres ont cotisé ! La cagnotte nette de ${"%,.0f".format(calculatedGain).replace(',', ' ')} ${tontine.currency}$exemptExplanation a été validée pour ${currentCycle.beneficiaryName}. Le prochain tour commence le $nextStartDate avec pour bénéficiaire : $nextBeneficiaryName."

                    allMembers.forEach { m ->
                        triggerNotification(
                            userId = m.userId,
                            type = NotificationType.PAYOUT_TURN,
                            title = notifTitle,
                            message = notifMsg,
                            tontineId = tontineId
                        )
                    }

                    // Advance cycle to next turn
                    val nextCycleNum = currentCycle.cycleNumber + 1
                    val nextCycleId = "cycle_${tontineId}_$nextCycleNum"
                    val nextPotExpected = tontine.contributionAmount * payingCount
                    val nextCycle = TontineCycle(
                        id = nextCycleId,
                        tontineId = tontineId,
                        cycleNumber = nextCycleNum,
                        beneficiaryId = nextBeneficiary.userId,
                        beneficiaryMemberId = nextBeneficiary.id,
                        beneficiaryName = nextBeneficiaryName,
                        beneficiaryPhone = nextBeneficiary.phoneNumber,
                        dueDate = nextStartDate,
                        status = CycleStatus.ACTIVE,
                        potExpected = nextPotExpected,
                        potCollected = 0.0,
                        currency = tontine.currency
                    )

                    _cycles.update { map ->
                        val existing = map[tontineId] ?: emptyList()
                        val updated = existing.map { if (it.id == currentCycle.id) it.copy(status = CycleStatus.COMPLETED) else it } + nextCycle
                        map + (tontineId to updated)
                    }

                    val actRound = ActivityItem(
                        id = "act_" + UUID.randomUUID().toString().take(8),
                        tontineId = tontineId,
                        type = ActivityType.CYCLE_STARTED,
                        title = "Fin du Tour ${currentCycle.cycleNumber} • Prochain : $nextBeneficiaryName",
                        description = notifMsg,
                        timestamp = System.currentTimeMillis()
                    )
                    _activities.update { listOf(actRound) + it }
                    if (firestore != null) {
                        val actRoundMap = hashMapOf<String, Any>(
                            "id" to actRound.id,
                            "tontineId" to tontineId,
                            "type" to actRound.type.name,
                            "title" to actRound.title,
                            "description" to actRound.description,
                            "timestamp" to actRound.timestamp
                        )
                        firestore.collection("activities").document(actRound.id).set(actRoundMap)
                    }
                } else {
                    val newCycleIndex = tontine.totalCycles + 1

                    // Auto-rotation (Feedback B.1): If enabled by admin, rotate member order for the new cycle!
                    val effectiveMembers = if (tontine.autoRotation && sortedMembers.size > 1) {
                        val count = sortedMembers.size
                        val rotated = sortedMembers.map { m ->
                            val newOrder = if (m.payoutOrder == 1) count else (m.payoutOrder - 1)
                            m.copy(payoutOrder = newOrder)
                        }
                        _members.update { it + (tontineId to rotated) }
                        if (firestore != null) {
                            rotated.forEach { rm ->
                                firestore.collection("tontines").document(tontineId).collection("members").document(rm.id)
                                    .update("payoutOrder", rm.payoutOrder)
                            }
                        }
                        rotated.sortedBy { it.payoutOrder }
                    } else {
                        sortedMembers
                    }

                    val firstBeneficiary = effectiveMembers.firstOrNull()
                    val firstBeneficiaryName = firstBeneficiary?.getPartDisplayName(allMembers) ?: "Bénéficiaire"
                    val notifTitle = "🏆 Cycle ${tontine.totalCycles} terminé ! La boucle reprend"
                    val notifMsg = if (tontine.autoRotation) {
                        "Tous les membres ont reçu leur cagnotte ! Le cycle ${tontine.totalCycles} est clôturé. Avec les tours automatiques, l'ordre a tourné pour le Cycle $newCycleIndex : Tour #1 avec pour bénéficiaire $firstBeneficiaryName."
                    } else {
                        "Tous les membres de la tontine '${tontine.name}' ont reçu leur cagnotte avec succès ! Le cycle ${tontine.totalCycles} est clôturé et la boucle reprend pour le Cycle $newCycleIndex : Tour #1 avec pour bénéficiaire $firstBeneficiaryName."
                    }

                    allMembers.forEach { m ->
                        triggerNotification(
                            userId = m.userId,
                            type = NotificationType.SYSTEM,
                            title = notifTitle,
                            message = notifMsg,
                            tontineId = tontineId
                        )
                    }

                    val actCycle = ActivityItem(
                        id = "act_" + UUID.randomUUID().toString().take(8),
                        tontineId = tontineId,
                        type = ActivityType.CYCLE_STARTED,
                        title = "🏆 Fin du Cycle ${tontine.totalCycles} • Nouveau Cycle $newCycleIndex",
                        description = notifMsg,
                        timestamp = System.currentTimeMillis()
                    )
                    _activities.update { listOf(actCycle) + it }
                    if (firestore != null) {
                        val actCycleMap = hashMapOf<String, Any>(
                            "id" to actCycle.id,
                            "tontineId" to tontineId,
                            "type" to actCycle.type.name,
                            "title" to actCycle.title,
                            "description" to actCycle.description,
                            "timestamp" to actCycle.timestamp
                        )
                        firestore.collection("activities").document(actCycle.id).set(actCycleMap)
                    }

                    // Start Tour 1 of next cycle
                    if (firstBeneficiary != null) {
                        val nextCycleId = "cycle_${tontineId}_c${newCycleIndex}_1"
                        val nextStartDate = tontine.nextTurnStartDate ?: tontine.endDate
                        val nextCycle = TontineCycle(
                            id = nextCycleId,
                            tontineId = tontineId,
                            cycleNumber = 1,
                            beneficiaryId = firstBeneficiary.userId,
                            beneficiaryMemberId = firstBeneficiary.id,
                            beneficiaryName = firstBeneficiaryName,
                            beneficiaryPhone = firstBeneficiary.phoneNumber,
                            dueDate = nextStartDate,
                            status = CycleStatus.ACTIVE,
                            potExpected = tontine.contributionAmount * payingCount,
                            potCollected = 0.0,
                            currency = tontine.currency
                        )
                        _cycles.update { map ->
                            val existing = map[tontineId] ?: emptyList()
                            val updated = existing.map { if (it.id == currentCycle.id) it.copy(status = CycleStatus.COMPLETED) else it } + nextCycle
                            map + (tontineId to updated)
                        }
                        _tontines.update { list ->
                            list.map { if (it.id == tontineId) it.copy(totalCycles = newCycleIndex, activeCycleNumber = newCycleIndex) else it }
                        }
                        if (firestore != null) {
                            firestore.collection("tontines").document(tontineId)
                                .update(mapOf("totalCycles" to newCycleIndex, "activeCycleNumber" to newCycleIndex))
                            val nextCycleMap = hashMapOf<String, Any>(
                                "id" to nextCycle.id,
                                "tontineId" to nextCycle.tontineId,
                                "cycleNumber" to nextCycle.cycleNumber,
                                "beneficiaryId" to nextCycle.beneficiaryId,
                                "beneficiaryMemberId" to nextCycle.beneficiaryMemberId,
                                "beneficiaryName" to nextCycle.beneficiaryName,
                                "beneficiaryPhone" to nextCycle.beneficiaryPhone,
                                "dueDate" to nextCycle.dueDate,
                                "status" to nextCycle.status.name,
                                "potExpected" to nextCycle.potExpected,
                                "potCollected" to nextCycle.potCollected,
                                "currency" to nextCycle.currency
                            )
                            firestore.collection("tontines").document(tontineId).collection("cycles").document(nextCycle.id).set(nextCycleMap)
                        }
                    }
                }
            }
        }

        // Sync validation to Cloud Firestore
        if (firestore != null) {
            firestore.collection("transactions").document(txnId)
                .update(
                    mapOf(
                        "status" to TransactionStatus.VALIDATED.name,
                        "validatedAt" to System.currentTimeMillis()
                    )
                )

            if (tontineId.isNotBlank()) {
                firestore.collection("tontines").document(tontineId).collection("cycles").document(txn.cycleId)
                    .update("potCollected", FieldValue.increment(txn.amount))
            }

            val actMap = hashMapOf<String, Any>(
                "id" to act.id,
                "tontineId" to tontineId,
                "type" to act.type.name,
                "title" to act.title,
                "description" to act.description,
                "timestamp" to act.timestamp
            )
            firestore.collection("activities").document(act.id).set(actMap)
        }
    }

    // Receiver contests / rejects a transaction
    fun rejectTransaction(txnId: String, reason: String) {
        val txn = _transactions.value.find { it.id == txnId } ?: return

        _transactions.update { list ->
            list.map {
                if (it.id == txnId) {
                    it.copy(
                        status = TransactionStatus.REJECTED,
                        rejectionReason = reason,
                        validatedAt = System.currentTimeMillis()
                    )
                } else it
            }
        }

        val tontineId = getTontineIdForCycle(txn.cycleId)

        val act = ActivityItem(
            id = "act_" + UUID.randomUUID().toString().take(8),
            tontineId = tontineId,
            type = ActivityType.PAYMENT_REJECTED,
            title = "Paiement contesté",
            description = "${txn.receiverName} a contesté le reçu de ${txn.senderName} : $reason",
            timestamp = System.currentTimeMillis()
        )
        _activities.update { listOf(act) + it }

        val members = _members.value[tontineId] ?: emptyList()
        members.forEach { m ->
            if (m.userId != txn.receiverId) {
                triggerNotification(
                    userId = m.userId,
                    type = NotificationType.VALIDATION,
                    title = if (m.userId == txn.senderId) "Paiement non confirmé" else "Paiement contesté",
                    message = if (m.userId == txn.senderId) {
                        "${txn.receiverName} a signalé un problème sur votre reçu : $reason"
                    } else {
                        "${txn.receiverName} a contesté le reçu de ${txn.senderName} : $reason"
                    },
                    tontineId = tontineId
                )
            }
        }

        if (firestore != null) {
            firestore.collection("transactions").document(txnId)
                .update(
                    mapOf(
                        "status" to TransactionStatus.REJECTED.name,
                        "rejectionReason" to reason,
                        "validatedAt" to System.currentTimeMillis()
                    )
                )

            val actMap = hashMapOf<String, Any>(
                "id" to act.id,
                "tontineId" to tontineId,
                "type" to act.type.name,
                "title" to act.title,
                "description" to act.description,
                "timestamp" to act.timestamp
            )
            firestore.collection("activities").document(act.id).set(actMap)
        }
    }

    // Create a new Tontine
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
    ): Tontine {
        val current = _currentUser.value ?: User(
            id = "u_admin",
            phoneNumber = "+237 690 00 00 00",
            fullName = "Administrateur"
        )

        val newId = "t_" + UUID.randomUUID().toString().take(6)
        val generatedInviteCode = name.take(3).uppercase() + "-" + (1000..9999).random()

        val newTontine = Tontine(
            id = newId,
            name = name,
            description = description,
            creatorId = current.id,
            contributionAmount = amount,
            frequency = frequency,
            currency = currency,
            inviteCode = generatedInviteCode,
            totalMembers = 1,
            activeCycleNumber = 1,
            totalCycles = memberCount,
            startDate = startDate,
            endDate = endDate,
            latePenaltyFee = latePenaltyFee,
            isBeneficiaryExempt = isBeneficiaryExempt,
            isPendingLaunch = isPendingLaunch,
            autoRotation = autoRotation,
            createdAt = System.currentTimeMillis()
        )

        _tontines.update { listOf(newTontine) + it }

        // Creator as first member (admin)
        val adminMember = TontineMember(
            id = "tm_" + UUID.randomUUID().toString().take(6),
            tontineId = newId,
            userId = current.id,
            fullName = current.fullName,
            phoneNumber = current.phoneNumber,
            role = MemberRole.ADMIN,
            payoutOrder = 1
        )
        _members.update { it + (newId to listOf(adminMember)) }

        // First cycle
        val payingMembers = if (isBeneficiaryExempt) (memberCount - 1).coerceAtLeast(1) else memberCount.coerceAtLeast(1)
        val expectedPot = amount * payingMembers
        val cycle1 = TontineCycle(
            id = "c_" + UUID.randomUUID().toString().take(6),
            tontineId = newId,
            cycleNumber = 1,
            beneficiaryId = current.id,
            beneficiaryName = current.fullName,
            beneficiaryPhone = current.phoneNumber,
            dueDate = endDate,
            status = CycleStatus.ACTIVE,
            potExpected = expectedPot,
            potCollected = 0.0,
            currency = currency
        )
        _cycles.update { it + (newId to listOf(cycle1)) }

        val act = ActivityItem(
            id = "act_" + UUID.randomUUID().toString().take(8),
            tontineId = newId,
            type = ActivityType.CYCLE_STARTED,
            title = "Tontine créée",
            description = "${current.fullName} a initialisé la tontine '$name' (${amount.toInt()} $currency). Objectif cagnotte : ${expectedPot.toInt()} $currency.",
            timestamp = System.currentTimeMillis()
        )
        _activities.update { listOf(act) + it }

        // Persist to Cloud Firestore for real-time multi-device interaction
        if (firestore != null) {
            val tontineMap = hashMapOf<String, Any>(
                "id" to newId,
                "name" to name,
                "description" to description,
                "creatorId" to current.id,
                "contributionAmount" to amount,
                "frequency" to frequency.name,
                "currency" to currency,
                "inviteCode" to generatedInviteCode,
                "totalMembers" to 1,
                "startDate" to startDate,
                "endDate" to endDate,
                "latePenaltyFee" to latePenaltyFee,
                "isBeneficiaryExempt" to isBeneficiaryExempt,
                "isPendingLaunch" to isPendingLaunch,
                "autoRotation" to autoRotation,
                "createdAt" to newTontine.createdAt
            )
            firestore.collection("tontines").document(newId).set(tontineMap)

            val memberMap = hashMapOf<String, Any>(
                "id" to adminMember.id,
                "tontineId" to newId,
                "userId" to adminMember.userId,
                "fullName" to adminMember.fullName,
                "phoneNumber" to adminMember.phoneNumber,
                "role" to adminMember.role.name,
                "payoutOrder" to 1
            )
            firestore.collection("tontines").document(newId).collection("members").document(adminMember.id).set(memberMap)

            val cycleMap = hashMapOf<String, Any>(
                "id" to cycle1.id,
                "tontineId" to newId,
                "cycleNumber" to 1,
                "beneficiaryId" to current.id,
                "beneficiaryName" to current.fullName,
                "beneficiaryPhone" to current.phoneNumber,
                "dueDate" to endDate,
                "status" to cycle1.status.name,
                "potExpected" to expectedPot,
                "potCollected" to 0.0,
                "currency" to currency
            )
            firestore.collection("tontines").document(newId).collection("cycles").document(cycle1.id).set(cycleMap)

            val actMap = hashMapOf<String, Any>(
                "id" to act.id,
                "tontineId" to newId,
                "type" to act.type.name,
                "title" to act.title,
                "description" to act.description,
                "timestamp" to act.timestamp
            )
            firestore.collection("activities").document(act.id).set(actMap)
        }

        return newTontine
    }

    // Admin updates tontine settings (Feedback B.1 & B.5)
    fun updateTontineSettings(
        tontineId: String,
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
        _tontines.update { list ->
            list.map {
                if (it.id == tontineId) {
                    it.copy(
                        name = name,
                        description = description,
                        contributionAmount = amount,
                        frequency = frequency,
                        currency = currency,
                        startDate = startDate,
                        endDate = endDate,
                        latePenaltyFee = latePenaltyFee,
                        isBeneficiaryExempt = isBeneficiaryExempt,
                        autoRotation = if (it.isEffectivelyPending) autoRotation else it.autoRotation,
                        isPendingLaunch = isPendingLaunch
                    )
                } else it
            }
        }

        val memberCount = _members.value[tontineId]?.size ?: 1
        val payingCount = if (isBeneficiaryExempt) (memberCount - 1).coerceAtLeast(1) else memberCount.coerceAtLeast(1)
        val newPotTarget = amount * payingCount
        _cycles.update { map ->
            map.mapValues { (tId, cycles) ->
                if (tId == tontineId) {
                    cycles.map { if (it.status == CycleStatus.ACTIVE) it.copy(potExpected = newPotTarget, dueDate = endDate) else it }
                } else cycles
            }
        }

        if (firestore != null) {
            val updates = hashMapOf<String, Any>(
                "name" to name,
                "description" to description,
                "contributionAmount" to amount,
                "frequency" to frequency.name,
                "currency" to currency,
                "startDate" to startDate,
                "endDate" to endDate,
                "latePenaltyFee" to latePenaltyFee,
                "isBeneficiaryExempt" to isBeneficiaryExempt,
                "autoRotation" to autoRotation,
                "isPendingLaunch" to isPendingLaunch
            )
            firestore.collection("tontines").document(tontineId).update(updates)

            // Update active cycle pot expected & due date in Firestore
            val activeCycle = _cycles.value[tontineId]?.find { it.status == CycleStatus.ACTIVE }
            if (activeCycle != null) {
                firestore.collection("tontines").document(tontineId).collection("cycles").document(activeCycle.id)
                    .update(mapOf("potExpected" to newPotTarget, "dueDate" to endDate))
            }
        }

        // Post activity event to Fil Groupe
        val act = ActivityItem(
            id = "act_" + UUID.randomUUID().toString().take(8),
            tontineId = tontineId,
            type = ActivityType.TONTINE_UPDATED,
            title = "Paramètres mis à jour",
            description = "L'administrateur a mis à jour les conditions : Cotisation $amount $currency, réception du $startDate au $endDate.",
            timestamp = System.currentTimeMillis()
        )
        _activities.update { listOf(act) + it }

        if (firestore != null) {
            val actMap = hashMapOf<String, Any>(
                "id" to act.id,
                "tontineId" to tontineId,
                "type" to act.type.name,
                "title" to act.title,
                "description" to act.description,
                "timestamp" to act.timestamp
            )
            firestore.collection("activities").document(act.id).set(actMap)
        }

        // Broadcast notification in real-time to all other members of this tontine
        val tontineMembers = _members.value[tontineId] ?: emptyList()
        val currentUserId = _currentUser.value?.id
        tontineMembers.forEach { member ->
            if (member.userId != currentUserId) {
                triggerNotification(
                    userId = member.userId,
                    type = NotificationType.SYSTEM,
                    title = "Paramètres modifiés : $name",
                    message = "L'administrateur a mis à jour les dates ($startDate au $endDate) et la cotisation ($amount $currency).",
                    tontineId = tontineId
                )
            }
        }
    }

    // Admin launches a pending tontine at any time (Feedback B.10)
    fun launchTontine(tontineId: String) {
        val tontine = _tontines.value.firstOrNull { it.id == tontineId } ?: return
        val allMembers = _members.value[tontineId] ?: emptyList()
        val firstBeneficiary = allMembers.sortedBy { it.payoutOrder }.firstOrNull()
        val cleanDesc = tontine.description
            .replace(Regex("""\s*\(?Lancement\s*prévu\s*:[^)]*\)?""", RegexOption.IGNORE_CASE), "")
            .trim()

        _tontines.update { list ->
            list.map {
                if (it.id == tontineId) {
                    it.copy(
                        isPendingLaunch = false,
                        description = cleanDesc
                    )
                } else it
            }
        }

        if (firestore != null) {
            firestore.collection("tontines").document(tontineId)
                .update(mapOf(
                    "isPendingLaunch" to false,
                    "description" to cleanDesc
                ))
        }

        val beneficiaryLabel = firstBeneficiary?.getPartDisplayName(allMembers) ?: "Premier bénéficiaire"
        val launchMsg = "🚀 La tontine '${tontine.name}' vient d'être lancée ! Les cotisations pour le tour #1 (Bénéficiaire : $beneficiaryLabel) sont ouvertes."

        val act = ActivityItem(
            id = "act_" + UUID.randomUUID().toString().take(8),
            tontineId = tontineId,
            type = ActivityType.CYCLE_STARTED,
            title = "🚀 Tontine lancée !",
            description = launchMsg,
            timestamp = System.currentTimeMillis()
        )
        _activities.update { listOf(act) + it }
        if (firestore != null) {
            val actMap = hashMapOf<String, Any>(
                "id" to act.id,
                "tontineId" to tontineId,
                "type" to act.type.name,
                "title" to act.title,
                "description" to act.description,
                "timestamp" to act.timestamp
            )
            firestore.collection("activities").document(act.id).set(actMap)
        }

        allMembers.forEach { m ->
            triggerNotification(
                userId = m.userId,
                type = NotificationType.SYSTEM,
                title = "Tontine '${tontine.name}' lancée !",
                message = launchMsg,
                tontineId = tontineId
            )
        }
    }
    fun deleteTontine(tontineId: String) {
        val tontine = _tontines.value.find { it.id == tontineId }
        val tontineName = tontine?.name ?: "Tontine"
        val membersToNotify = _members.value[tontineId] ?: emptyList()
        val currentUserId = _currentUser.value?.id

        // Notify all members
        membersToNotify.forEach { member ->
            if (member.userId != currentUserId) {
                triggerNotification(
                    userId = member.userId,
                    type = NotificationType.SYSTEM,
                    title = "Tontine supprimée",
                    message = "La tontine '$tontineName' a été supprimée par l'administrateur.",
                    tontineId = tontineId
                )
            }
        }

        _tontines.update { list -> list.filter { it.id != tontineId } }
        _members.update { map -> map.filterKeys { it != tontineId } }
        _cycles.update { map -> map.filterKeys { it != tontineId } }
        _activities.update { list -> list.filter { it.tontineId != tontineId } }

        if (firestore != null) {
            firestore.collection("tontines").document(tontineId).delete()
        }
    }

    // Member leaves a tontine (Feedback C.1: with financial and administrative security checks)
    fun leaveTontine(tontineId: String, userId: String): Result<Unit> {
        val tontine = _tontines.value.find { it.id == tontineId }
            ?: return Result.failure(IllegalArgumentException("Tontine introuvable"))
        val currentMembers = _members.value[tontineId] ?: emptyList()
        val member = currentMembers.find { it.userId == userId }
            ?: return Result.failure(IllegalArgumentException("Vous n'êtes pas membre de cette tontine"))

        // Security check 1: Cannot leave if sole admin when other members exist
        val admins = currentMembers.filter { it.role == MemberRole.ADMIN }
        if (member.role == MemberRole.ADMIN && admins.size <= 1 && currentMembers.size > 1) {
            return Result.failure(IllegalStateException("Vous êtes le seul administrateur de cette tontine. Veuillez désigner un autre administrateur avant de la quitter."))
        }

        // Security check 2: Cannot leave if member has already benefited in the current rotation cycle
        val cycles = _cycles.value[tontineId] ?: emptyList()
        val activeCycle = cycles.find { it.status == CycleStatus.ACTIVE }
        val currentCycleNumber = activeCycle?.cycleNumber ?: 1
        val memberHasBenefited = member.payoutOrder < currentCycleNumber || (activeCycle?.beneficiaryId == userId)
        if (memberHasBenefited && currentMembers.size > 1 && currentCycleNumber < currentMembers.size) {
            return Result.failure(IllegalStateException("Mesure de sécurité financière : Vous avez déjà bénéficié de la cagnotte pour ce cycle de rotation. Vous ne pouvez pas quitter la tontine avant la fin du cycle."))
        }

        val remaining = currentMembers.filter { it.userId != userId }
        val reordered = remaining.sortedBy { it.payoutOrder }.mapIndexed { index, m ->
            m.copy(payoutOrder = index + 1)
        }

        _members.update { it + (tontineId to reordered) }
        _tontines.update { list ->
            list.map {
                if (it.id == tontineId) it.copy(totalMembers = reordered.size) else it
            }
        }

        // Recalculate pot expected
        val payingCount = if (tontine.isBeneficiaryExempt) (reordered.size - 1).coerceAtLeast(1) else reordered.size
        val newPot = tontine.contributionAmount * payingCount
        _cycles.update { map ->
            map.mapValues { (tId, cList) ->
                if (tId == tontineId) {
                    cList.map { if (it.status == CycleStatus.ACTIVE) it.copy(potExpected = newPot) else it }
                } else cList
            }
        }

        val act = ActivityItem(
            id = "act_" + UUID.randomUUID().toString().take(8),
            tontineId = tontineId,
            type = ActivityType.SYSTEM_ALERT,
            title = "Départ d'un membre",
            description = "${member.fullName} a quitté le groupe.",
            timestamp = System.currentTimeMillis()
        )
        _activities.update { listOf(act) + it }

        // Notify admins
        admins.filter { it.userId != userId }.forEach { admin ->
            triggerNotification(
                userId = admin.userId,
                type = NotificationType.SYSTEM,
                title = "Départ d'un membre",
                message = "${member.fullName} a quitté la tontine '${tontine.name}'.",
                tontineId = tontineId
            )
        }

        if (firestore != null) {
            firestore.collection("tontines").document(tontineId).collection("members").document(member.id).delete()
            firestore.collection("tontines").document(tontineId).update("totalMembers", reordered.size)
            val actMap = hashMapOf<String, Any>(
                "id" to act.id,
                "tontineId" to tontineId,
                "type" to act.type.name,
                "title" to act.title,
                "description" to act.description,
                "timestamp" to act.timestamp
            )
            firestore.collection("activities").document(act.id).set(actMap)
        }

        return Result.success(Unit)
    }

    // Admin removes a member from tontine
    fun removeMember(tontineId: String, memberId: String) {
        val currentMembers = _members.value[tontineId] ?: return
        val remaining = currentMembers.filter { it.id != memberId }
        val reordered = remaining.sortedBy { it.payoutOrder }.mapIndexed { index, m ->
            m.copy(payoutOrder = index + 1)
        }

        _members.update { it + (tontineId to reordered) }
        _tontines.update { list ->
            list.map {
                if (it.id == tontineId) it.copy(totalMembers = reordered.size) else it
            }
        }

        if (firestore != null) {
            firestore.collection("tontines").document(tontineId).collection("members").document(memberId).delete()
            firestore.collection("tontines").document(tontineId).update("totalMembers", reordered.size)
        }
    }

    // Reorder member payout priority (Feedback B.5 & B.6)
    fun reorderMember(tontineId: String, memberId: String, newPosition: Int) {
        val currentMembers = (_members.value[tontineId] ?: return).toMutableList()
        val member = currentMembers.find { it.id == memberId } ?: return
        val tontine = _tontines.value.find { it.id == tontineId }
        val isPending = tontine?.isEffectivelyPending == true

        // Security check: Only lock members who have ALREADY benefited when tontine is active
        val cycles = _cycles.value[tontineId] ?: emptyList()
        val activeCycle = cycles.find { it.status == CycleStatus.ACTIVE }
        val currentCycleNumber = activeCycle?.cycleNumber ?: 1

        val hasBenefited = if (isPending) false else (member.payoutOrder < currentCycleNumber)
        if (hasBenefited) {
            Log.w("TontineRepository", "Member ${member.fullName} has already benefited, order cannot be changed")
            return
        }

        // Target position cannot precede the current cycle (in pending, any position 1..N is valid)
        val minPosition = if (isPending) 1 else currentCycleNumber
        val safePosition = newPosition.coerceAtLeast(minPosition)

        val memberIndex = currentMembers.indexOfFirst { it.id == memberId }
        if (memberIndex == -1) return

        currentMembers.removeAt(memberIndex)
        val clampedPosition = (safePosition - 1).coerceIn(0, currentMembers.size)
        currentMembers.add(clampedPosition, member)

        val reindexed = currentMembers.mapIndexed { index, m ->
            m.copy(payoutOrder = index + 1)
        }

        _members.update { it + (tontineId to reindexed) }

        // If position 1 changed and tontine is pending or cycle has 0 pot, update beneficiary of active cycle
        if (isPending || (activeCycle?.potCollected ?: 0.0) == 0.0) {
            val newFirst = reindexed.firstOrNull()
            if (newFirst != null && activeCycle != null && activeCycle.beneficiaryId != newFirst.userId) {
                val updatedCycle = activeCycle.copy(
                    beneficiaryId = newFirst.userId,
                    beneficiaryName = newFirst.fullName,
                    beneficiaryPhone = newFirst.phoneNumber
                )
                _cycles.update { map ->
                    map + (tontineId to (cycles.filter { it.id != activeCycle.id } + updatedCycle))
                }
                if (firestore != null) {
                    firestore.collection("tontines").document(tontineId).collection("cycles").document(activeCycle.id)
                        .update(mapOf(
                            "beneficiaryId" to newFirst.userId,
                            "beneficiaryName" to newFirst.fullName,
                            "beneficiaryPhone" to newFirst.phoneNumber
                        ))
                }
            }
        }

        if (firestore != null) {
            reindexed.forEach { m ->
                firestore.collection("tontines").document(tontineId).collection("members").document(m.id)
                    .update("payoutOrder", m.payoutOrder)
            }
        }
    }

    // Continuous & Full Drag-and-Drop Reorder batch update
    fun reorderAllMembers(tontineId: String, orderedMemberIds: List<String>) {
        val currentMembers = (_members.value[tontineId] ?: return).toMutableList()
        val tontine = _tontines.value.find { it.id == tontineId }
        val isPending = tontine?.isEffectivelyPending == true
        val cycles = _cycles.value[tontineId] ?: emptyList()
        val activeCycle = cycles.find { it.status == CycleStatus.ACTIVE }

        val memberMap = currentMembers.associateBy { it.id }
        val reordered = orderedMemberIds.mapNotNull { memberMap[it] }
        val missing = currentMembers.filter { it.id !in orderedMemberIds }
        val fullList = (reordered + missing).mapIndexed { index, m ->
            m.copy(payoutOrder = index + 1)
        }

        _members.update { it + (tontineId to fullList) }

        if (isPending || (activeCycle?.potCollected ?: 0.0) == 0.0) {
            val newFirst = fullList.firstOrNull()
            if (newFirst != null && activeCycle != null && activeCycle.beneficiaryId != newFirst.userId) {
                val updatedCycle = activeCycle.copy(
                    beneficiaryId = newFirst.userId,
                    beneficiaryName = newFirst.fullName,
                    beneficiaryPhone = newFirst.phoneNumber
                )
                _cycles.update { map ->
                    map + (tontineId to (cycles.filter { it.id != activeCycle.id } + updatedCycle))
                }
                if (firestore != null) {
                    firestore.collection("tontines").document(tontineId).collection("cycles").document(activeCycle.id)
                        .update(mapOf(
                            "beneficiaryId" to newFirst.userId,
                            "beneficiaryName" to newFirst.fullName,
                            "beneficiaryPhone" to newFirst.phoneNumber
                        ))
                }
            }
        }

        if (firestore != null) {
            fullList.forEach { m ->
                firestore.collection("tontines").document(tontineId).collection("members").document(m.id)
                    .update("payoutOrder", m.payoutOrder)
            }
        }
    }

    // Invite user by phone number
    fun inviteUserByPhone(tontineId: String, phoneNumber: String): Result<String> {
        val tontine = _tontines.value.find { it.id == tontineId }
            ?: return Result.failure(IllegalArgumentException("Tontine introuvable"))

        val cleanPhone = phoneNumber.trim()
        val existingUser = _users.value.find { it.phoneNumber.replace(" ", "") == cleanPhone.replace(" ", "") }

        val targetUser = existingUser ?: run {
            val newUserId = "u_" + UUID.randomUUID().toString().take(6)
            val newUser = User(
                id = newUserId,
                phoneNumber = cleanPhone,
                fullName = "Membre $cleanPhone"
            )
            _users.update { it + newUser }
            if (firestore != null) {
                firestore.collection("users").document(newUserId).set(
                    mapOf(
                        "id" to newUserId,
                        "fullName" to newUser.fullName,
                        "phoneNumber" to cleanPhone,
                        "normPhone" to cleanPhone.replace(" ", "")
                    )
                )
            }
            newUser
        }

        val currentMembers = _members.value[tontineId] ?: emptyList()
        if (currentMembers.any { it.userId == targetUser.id }) {
            return Result.failure(IllegalArgumentException("${targetUser.fullName} fait déjà partie de cette tontine !"))
        }

        val nextOrder = (currentMembers.maxOfOrNull { it.payoutOrder } ?: 0) + 1
        val newMember = TontineMember(
            id = "tm_" + UUID.randomUUID().toString().take(6),
            tontineId = tontineId,
            userId = targetUser.id,
            fullName = targetUser.fullName,
            phoneNumber = targetUser.phoneNumber,
            role = MemberRole.MEMBER,
            payoutOrder = nextOrder
        )

        _members.update { it + (tontineId to (currentMembers + newMember)) }
        _tontines.update { list ->
            list.map {
                if (it.id == tontineId) it.copy(totalMembers = currentMembers.size + 1) else it
            }
        }

        val newTotalMembers = currentMembers.size + 1
        val isExempt = tontine.isBeneficiaryExempt
        val payingCount = if (isExempt) (newTotalMembers - 1).coerceAtLeast(1) else newTotalMembers
        val newPotExpected = tontine.contributionAmount * payingCount

        _cycles.update { map ->
            map.mapValues { (tId, cycles) ->
                if (tId == tontineId) {
                    cycles.map { if (it.status == CycleStatus.ACTIVE) it.copy(potExpected = newPotExpected) else it }
                } else cycles
            }
        }

        val act = ActivityItem(
            id = "act_" + UUID.randomUUID().toString().take(8),
            tontineId = tontineId,
            type = ActivityType.MEMBER_JOINED,
            title = "Membre invité",
            description = "${targetUser.fullName} a été ajouté(e) à la tontine (Ordre #$nextOrder).",
            timestamp = System.currentTimeMillis()
        )
        _activities.update { listOf(act) + it }

        if (firestore != null) {
            val memberMap = hashMapOf<String, Any>(
                "id" to newMember.id,
                "tontineId" to tontineId,
                "userId" to newMember.userId,
                "fullName" to newMember.fullName,
                "phoneNumber" to newMember.phoneNumber,
                "role" to newMember.role.name,
                "payoutOrder" to nextOrder
            )
            firestore.collection("tontines").document(tontineId).collection("members").document(newMember.id).set(memberMap)
            firestore.collection("tontines").document(tontineId).update("totalMembers", newTotalMembers)

            val activeCycle = _cycles.value[tontineId]?.find { it.status == CycleStatus.ACTIVE }
            if (activeCycle != null) {
                firestore.collection("tontines").document(tontineId).collection("cycles").document(activeCycle.id)
                    .update("potExpected", newPotExpected)
            }

            val actMap = hashMapOf<String, Any>(
                "id" to act.id,
                "tontineId" to tontineId,
                "type" to act.type.name,
                "title" to act.title,
                "description" to act.description,
                "timestamp" to act.timestamp
            )
            firestore.collection("activities").document(act.id).set(actMap)
        }

        // Notify invited user and existing members (Feedback B.1)
        triggerNotification(
            userId = targetUser.id,
            type = NotificationType.INVITATION,
            title = "Invitation : ${tontine.name}",
            message = "Vous avez été invité(e) dans la tontine '${tontine.name}' (Tour de passage #$nextOrder).",
            tontineId = tontineId
        )

        // B.1: Notify all existing members that a new member joined
        currentMembers.forEach { m ->
            if (m.userId != targetUser.id) {
                triggerNotification(
                    userId = m.userId,
                    type = NotificationType.SYSTEM,
                    title = "Nouveau membre : ${tontine.name}",
                    message = "${targetUser.fullName} a été ajouté(e) au groupe (Ordre de passage #$nextOrder).",
                    tontineId = tontineId
                )
            }
        }

        return Result.success("Invitation envoyée avec succès à ${targetUser.fullName} (${targetUser.phoneNumber})")
    }

    // Join tontine via Invite Code or QR Link
    fun joinTontineByInviteCode(inviteCode: String, user: User): Result<Tontine> {
        val cleanCode = inviteCode.trim().uppercase()
        val tontine = _tontines.value.find { it.inviteCode.equals(cleanCode, ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("Code d'invitation invalide ou introuvable."))

        val currentMembers = _members.value[tontine.id] ?: emptyList()
        if (currentMembers.any { it.userId == user.id }) {
            return Result.success(tontine)
        }

        val nextOrder = (currentMembers.maxOfOrNull { it.payoutOrder } ?: 0) + 1
        val newMember = TontineMember(
            id = "tm_" + UUID.randomUUID().toString().take(6),
            tontineId = tontine.id,
            userId = user.id,
            fullName = user.fullName,
            phoneNumber = user.phoneNumber,
            role = MemberRole.MEMBER,
            payoutOrder = nextOrder
        )

        _members.update { it + (tontine.id to (currentMembers + newMember)) }
        _tontines.update { list ->
            list.map {
                if (it.id == tontine.id) it.copy(totalMembers = currentMembers.size + 1) else it
            }
        }

        val newTotalMembers = currentMembers.size + 1
        val isExempt = tontine.isBeneficiaryExempt
        val payingCount = if (isExempt) (newTotalMembers - 1).coerceAtLeast(1) else newTotalMembers
        val newPotExpected = tontine.contributionAmount * payingCount

        _cycles.update { map ->
            map.mapValues { (tId, cycles) ->
                if (tId == tontine.id) {
                    cycles.map { if (it.status == CycleStatus.ACTIVE) it.copy(potExpected = newPotExpected) else it }
                } else cycles
            }
        }

        val act = ActivityItem(
            id = "act_" + UUID.randomUUID().toString().take(8),
            tontineId = tontine.id,
            type = ActivityType.MEMBER_JOINED,
            title = "Nouveau membre via QR / Code",
            description = "${user.fullName} a rejoint le groupe via le code d'invitation $cleanCode.",
            timestamp = System.currentTimeMillis()
        )
        _activities.update { listOf(act) + it }

        // Sync new member to Cloud Firestore
        if (firestore != null) {
            val memberMap = hashMapOf<String, Any>(
                "id" to newMember.id,
                "tontineId" to tontine.id,
                "userId" to newMember.userId,
                "fullName" to newMember.fullName,
                "phoneNumber" to newMember.phoneNumber,
                "role" to newMember.role.name,
                "payoutOrder" to nextOrder
            )
            firestore.collection("tontines").document(tontine.id).collection("members").document(newMember.id).set(memberMap)
            firestore.collection("tontines").document(tontine.id).update("totalMembers", newTotalMembers)

            val activeCycle = _cycles.value[tontine.id]?.find { it.status == CycleStatus.ACTIVE }
            if (activeCycle != null) {
                firestore.collection("tontines").document(tontine.id).collection("cycles").document(activeCycle.id)
                    .update("potExpected", newPotExpected)
            }

            val actMap = hashMapOf<String, Any>(
                "id" to act.id,
                "tontineId" to tontine.id,
                "type" to act.type.name,
                "title" to act.title,
                "description" to act.description,
                "timestamp" to act.timestamp
            )
            firestore.collection("activities").document(act.id).set(actMap)
        }

        // B.1: Notify existing members that a new member joined via code / QR
        currentMembers.forEach { m ->
            if (m.userId != user.id) {
                triggerNotification(
                    userId = m.userId,
                    type = NotificationType.SYSTEM,
                    title = "Nouveau membre : ${tontine.name}",
                    message = "${user.fullName} a rejoint le groupe via le code d'invitation $cleanCode (Ordre #$nextOrder).",
                    tontineId = tontine.id
                )
            }
        }

        return Result.success(tontine)
    }

    // Add member to tontine directly
    fun addMember(tontineId: String, fullName: String, phone: String, requestedByUserId: String? = null) {
        val currentMembers = _members.value[tontineId] ?: emptyList()
        val nextOrder = (currentMembers.maxOfOrNull { it.payoutOrder } ?: 0) + 1
        val newUserId = "u_" + UUID.randomUUID().toString().take(6)
        val cleanPhone = phone.trim()
        val cleanName = fullName.trim()

        val newUser = User(
            id = newUserId,
            phoneNumber = cleanPhone,
            fullName = cleanName
        )
        _users.update { it + newUser }

        val tontine = _tontines.value.find { it.id == tontineId }
        val isRequesterAdmin = if (requestedByUserId != null && tontine != null) {
            tontine.isUserAdmin(requestedByUserId)
        } else true // Default direct add if admin/system

        val newMember = TontineMember(
            id = "tm_" + UUID.randomUUID().toString().take(6),
            tontineId = tontineId,
            userId = newUserId,
            fullName = cleanName,
            phoneNumber = cleanPhone,
            role = MemberRole.MEMBER,
            payoutOrder = nextOrder,
            isPendingApproval = !isRequesterAdmin
        )
        _members.update { it + (tontineId to (currentMembers + newMember)) }

        if (isRequesterAdmin) {
            val newTotalMembers = currentMembers.count { !it.isPendingApproval } + 1
            _tontines.update { list ->
                list.map {
                    if (it.id == tontineId) it.copy(totalMembers = newTotalMembers) else it
                }
            }

            val isExempt = tontine?.isBeneficiaryExempt ?: true
            val payingCount = if (isExempt) (newTotalMembers - 1).coerceAtLeast(1) else newTotalMembers
            val newPotExpected = (tontine?.contributionAmount ?: 0.0) * payingCount

            _cycles.update { map ->
                map.mapValues { (tId, cycles) ->
                    if (tId == tontineId) {
                        cycles.map { if (it.status == CycleStatus.ACTIVE) it.copy(potExpected = newPotExpected) else it }
                    } else cycles
                }
            }

            val act = ActivityItem(
                id = "act_" + UUID.randomUUID().toString().take(8),
                tontineId = tontineId,
                type = ActivityType.MEMBER_JOINED,
                title = "Nouveau membre",
                description = "$cleanName a rejoint le groupe (Position de passage #$nextOrder).",
                timestamp = System.currentTimeMillis()
            )
            _activities.update { listOf(act) + it }
        } else {
            // Not added by admin: Notify administrator and post pending notice (Feedback B.2)
            val admins = currentMembers.filter { it.role == MemberRole.ADMIN || it.role == MemberRole.CO_ADMIN }
            val requester = currentMembers.find { it.userId == requestedByUserId }
            val requesterName = requester?.fullName ?: "Un membre"

            admins.forEach { admin ->
                triggerNotification(
                    userId = admin.userId,
                    type = NotificationType.SYSTEM,
                    title = "Demande d'ajout d'un membre",
                    message = "$requesterName a proposé d'ajouter $cleanName ($cleanPhone) à la tontine '${tontine?.name}'. Approbation requise.",
                    tontineId = tontineId
                )
            }

            val act = ActivityItem(
                id = "act_" + UUID.randomUUID().toString().take(8),
                tontineId = tontineId,
                type = ActivityType.SYSTEM_ALERT,
                title = "Demande d'adhésion en attente",
                description = "$requesterName a proposé d'ajouter $cleanName au groupe. En attente de validation par l'administrateur.",
                timestamp = System.currentTimeMillis()
            )
            _activities.update { listOf(act) + it }
        }

        if (firestore != null) {
            val userMap = hashMapOf<String, Any>(
                "id" to newUserId,
                "fullName" to cleanName,
                "phoneNumber" to cleanPhone,
                "normPhone" to cleanPhone.replace(" ", ""),
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection("users").document(newUserId).set(userMap)

            val memberMap = hashMapOf<String, Any>(
                "id" to newMember.id,
                "tontineId" to tontineId,
                "userId" to newMember.userId,
                "fullName" to newMember.fullName,
                "phoneNumber" to newMember.phoneNumber,
                "role" to newMember.role.name,
                "payoutOrder" to nextOrder,
                "isPendingApproval" to newMember.isPendingApproval
            )
            firestore.collection("tontines").document(tontineId).collection("members").document(newMember.id).set(memberMap)
            if (isRequesterAdmin) {
                firestore.collection("tontines").document(tontineId).update("totalMembers", currentMembers.size + 1)
            }
        }
    }

    // Set or unset Co-Administrator (Feedback B.5)
    fun setCoAdmin(tontineId: String, memberId: String?) {
        val tontine = _tontines.value.find { it.id == tontineId } ?: return
        val currentMembers = _members.value[tontineId] ?: emptyList()
        val targetMember = if (memberId != null) currentMembers.find { it.id == memberId } else null

        val newCoAdminId = targetMember?.userId
        val newCoAdminName = targetMember?.fullName

        _tontines.update { list ->
            list.map {
                if (it.id == tontineId) it.copy(coAdminId = newCoAdminId, coAdminName = newCoAdminName) else it
            }
        }

        _members.update { map ->
            map.mapValues { (tId, mList) ->
                if (tId == tontineId) {
                    mList.map { m ->
                        when {
                            m.role == MemberRole.ADMIN -> m
                            m.id == memberId -> m.copy(role = MemberRole.CO_ADMIN)
                            m.role == MemberRole.CO_ADMIN -> m.copy(role = MemberRole.MEMBER)
                            else -> m
                        }
                    }
                } else mList
            }
        }

        if (targetMember != null) {
            val act = ActivityItem(
                id = "act_" + UUID.randomUUID().toString().take(8),
                tontineId = tontineId,
                type = ActivityType.TONTINE_UPDATED,
                title = "Nomination Co-Admin",
                description = "${targetMember.fullName} a été désigné(e) comme Co-Administrateur de la tontine.",
                timestamp = System.currentTimeMillis()
            )
            _activities.update { listOf(act) + it }

            triggerNotification(
                userId = targetMember.userId,
                type = NotificationType.SYSTEM,
                title = "Vous êtes Co-Administrateur !",
                message = "Vous avez été désigné(e) comme administrateur second de la tontine '${tontine.name}'.",
                tontineId = tontineId
            )
        }

        if (firestore != null) {
            val updates = hashMapOf<String, Any>(
                "coAdminId" to (newCoAdminId ?: ""),
                "coAdminName" to (newCoAdminName ?: "")
            )
            firestore.collection("tontines").document(tontineId).update(updates)
            if (targetMember != null) {
                firestore.collection("tontines").document(tontineId).collection("members").document(targetMember.id)
                    .update("role", MemberRole.CO_ADMIN.name)
            }
        }
    }

    // Admin approves a pending member or extra slot (Feedback B.2 & E)
    fun approvePendingMember(tontineId: String, memberId: String) {
        val tontine = _tontines.value.find { it.id == tontineId } ?: return
        val currentMembers = _members.value[tontineId] ?: return
        val member = currentMembers.find { it.id == memberId } ?: return
        if (!member.isPendingApproval) return

        val updatedMembers = currentMembers.map {
            if (it.id == memberId) it.copy(isPendingApproval = false) else it
        }
        _members.update { it + (tontineId to updatedMembers) }

        val activeCount = updatedMembers.count { !it.isPendingApproval }
        _tontines.update { list ->
            list.map { if (it.id == tontineId) it.copy(totalMembers = activeCount) else it }
        }

        val isExempt = tontine.isBeneficiaryExempt
        val payingCount = if (isExempt) (activeCount - 1).coerceAtLeast(1) else activeCount
        val newPotExpected = tontine.contributionAmount * payingCount
        _cycles.update { map ->
            map.mapValues { (tId, cycles) ->
                if (tId == tontineId) {
                    cycles.map { if (it.status == CycleStatus.ACTIVE) it.copy(potExpected = newPotExpected) else it }
                } else cycles
            }
        }

        val act = ActivityItem(
            id = "act_" + UUID.randomUUID().toString().take(8),
            tontineId = tontineId,
            type = ActivityType.MEMBER_JOINED,
            title = "Adhésion approuvée ✓",
            description = "${member.displayName} a été officiellement approuvé(e) par l'administrateur.",
            timestamp = System.currentTimeMillis()
        )
        _activities.update { listOf(act) + it }

        triggerNotification(
            userId = member.userId,
            type = NotificationType.SYSTEM,
            title = "Adhésion validée !",
            message = "Votre demande d'adhésion (${member.displayName}) dans '${tontine.name}' a été approuvée par l'administrateur.",
            tontineId = tontineId
        )

        if (firestore != null) {
            firestore.collection("tontines").document(tontineId).collection("members").document(memberId)
                .update("isPendingApproval", false)
            firestore.collection("tontines").document(tontineId).update("totalMembers", activeCount)
        }
    }

    // Admin rejects a pending member or extra slot
    fun rejectPendingMember(tontineId: String, memberId: String) {
        val currentMembers = _members.value[tontineId] ?: return
        val remaining = currentMembers.filter { it.id != memberId }
        _members.update { it + (tontineId to remaining) }

        if (firestore != null) {
            firestore.collection("tontines").document(tontineId).collection("members").document(memberId).delete()
        }
    }

    // Add multiple shares / profiles under same user account (Feedback E)
    fun addAdditionalProfileSlot(tontineId: String, userId: String, aliasLabel: String): Result<TontineMember> {
        val tontine = _tontines.value.find { it.id == tontineId }
            ?: return Result.failure(IllegalArgumentException("Tontine introuvable"))
        val currentMembers = _members.value[tontineId] ?: emptyList()
        val userSlots = currentMembers.filter { it.userId == userId }
        if (userSlots.isEmpty()) {
            return Result.failure(IllegalStateException("Vous devez être membre de la tontine pour ajouter une part."))
        }

        val baseUser = userSlots.first()
        val isUserAdmin = tontine.isUserAdmin(userId)
        val nextOrder = (currentMembers.maxOfOrNull { it.payoutOrder } ?: 0) + 1

        val newSlot = TontineMember(
            id = "tm_slot_" + UUID.randomUUID().toString().take(6),
            tontineId = tontineId,
            userId = userId,
            fullName = baseUser.fullName,
            phoneNumber = baseUser.phoneNumber,
            avatarUrl = baseUser.avatarUrl,
            aliasLabel = aliasLabel.trim(),
            role = MemberRole.MEMBER,
            payoutOrder = nextOrder,
            isPendingApproval = !isUserAdmin
        )

        val updatedMembers = currentMembers + newSlot
        _members.update { it + (tontineId to updatedMembers) }

        if (isUserAdmin) {
            val activeCount = updatedMembers.count { !it.isPendingApproval }
            _tontines.update { list ->
                list.map { if (it.id == tontineId) it.copy(totalMembers = activeCount) else it }
            }
            val isExempt = tontine.isBeneficiaryExempt
            val payingCount = if (isExempt) (activeCount - 1).coerceAtLeast(1) else activeCount
            val newPotExpected = tontine.contributionAmount * payingCount
            _cycles.update { map ->
                map.mapValues { (tId, cycles) ->
                    if (tId == tontineId) {
                        cycles.map { if (it.status == CycleStatus.ACTIVE) it.copy(potExpected = newPotExpected) else it }
                    } else cycles
                }
            }
        } else {
            // Notify admin for approval
            val admins = updatedMembers.filter { it.role == MemberRole.ADMIN || it.role == MemberRole.CO_ADMIN }
            admins.forEach { admin ->
                triggerNotification(
                    userId = admin.userId,
                    type = NotificationType.SYSTEM,
                    title = "Demande de nouvelle part",
                    message = "${baseUser.fullName} a demandé une part supplémentaire '$aliasLabel' dans '${tontine.name}'. Approbation requise.",
                    tontineId = tontineId
                )
            }
        }

        if (firestore != null) {
            val memberMap = hashMapOf<String, Any>(
                "id" to newSlot.id,
                "tontineId" to tontineId,
                "userId" to newSlot.userId,
                "fullName" to newSlot.fullName,
                "phoneNumber" to newSlot.phoneNumber,
                "avatarUrl" to newSlot.avatarUrl,
                "aliasLabel" to newSlot.aliasLabel,
                "role" to newSlot.role.name,
                "payoutOrder" to newSlot.payoutOrder,
                "isPendingApproval" to newSlot.isPendingApproval
            )
            firestore.collection("tontines").document(tontineId).collection("members").document(newSlot.id).set(memberMap)
        }

        return Result.success(newSlot)
    }

    private fun getTontineIdForCycle(cycleId: String): String {
        for ((tId, cycleList) in _cycles.value) {
            if (cycleList.any { it.id == cycleId }) return tId
        }
        return _tontines.value.firstOrNull()?.id ?: ""
    }
}
