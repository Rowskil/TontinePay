package com.example

import com.example.data.TontineRepository
import com.example.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ExampleUnitTest {

    private lateinit var repository: TontineRepository

    @Before
    fun setup() {
        repository = TontineRepository(null)
    }

    @Test
    fun testPristineInitialState() {
        // App must start completely clean: no test users, no test tontines
        assertNull("Current user must be null on clean launch", repository.currentUser.value)
        assertTrue("All users list must be empty initially", repository.allUsers.isEmpty())
        assertTrue("Tontines list must be empty initially", repository.tontines.value.isEmpty())
        assertTrue("Transactions list must be empty initially", repository.transactions.value.isEmpty())
    }

    @Test
    fun testAccountCreationOnFirstLaunch() {
        val user = repository.createUser(
            fullName = "Aïcha Koné",
            phoneNumber = "+225 07 12 34 56 78",
            avatarUrl = ""
        )
        assertNotNull("Created user must not be null", user)
        assertEquals("Aïcha Koné", user.fullName)
        assertEquals("+225 07 12 34 56 78", user.phoneNumber)
        assertEquals(user, repository.currentUser.value)
        assertEquals(1, repository.allUsers.size)
    }

    @Test
    fun testCreateTontineInPristineApp() {
        val creator = repository.createUser("Moussa Traoré", "+223 70 00 11 22")
        val tontine = repository.createTontine(
            name = "Tontine Entreprise",
            description = "Épargne solidaire mensuelle",
            amount = 50000.0,
            frequency = TontineFrequency.MONTHLY,
            currency = "XOF",
            memberCount = 5
        )

        assertEquals("Tontine Entreprise", tontine.name)
        assertEquals(50000.0, tontine.contributionAmount, 0.01)
        assertEquals(1, repository.tontines.value.size)

        // Creator is admin member
        val members = repository.members.value[tontine.id]
        assertNotNull(members)
        assertEquals(1, members?.size)
        assertEquals(creator.id, members?.first()?.userId)
        assertEquals(MemberRole.ADMIN, members?.first()?.role)

        // First cycle active
        val cycles = repository.cycles.value[tontine.id]
        assertNotNull(cycles)
        assertEquals(1, cycles?.size)
        assertEquals(CycleStatus.ACTIVE, cycles?.first()?.status)
    }

    @Test
    fun testInviteUserByPhone() {
        repository.createUser("Moussa Traoré", "+223 70 00 11 22")
        val tontine = repository.createTontine(
            name = "Tontine Famille",
            description = "Épargne",
            amount = 25000.0,
            frequency = TontineFrequency.MONTHLY,
            currency = "XOF",
            memberCount = 4
        )

        val result = repository.inviteUserByPhone(tontine.id, "+223 76 55 44 33")
        assertTrue("Invite by phone must succeed", result.isSuccess)

        val members = repository.members.value[tontine.id]
        assertEquals(2, members?.size)
        assertTrue(members?.any { it.phoneNumber == "+223 76 55 44 33" } == true)
    }

    @Test
    fun testJoinTontineByInviteCode() {
        repository.createUser("Admin User", "+221 77 123 45 67")
        val tontine = repository.createTontine(
            name = "Tontine Dakar",
            description = "Épargne",
            amount = 30000.0,
            frequency = TontineFrequency.MONTHLY,
            currency = "XOF",
            memberCount = 6
        )

        val joiningUser = User("u_joiner", "+221 78 999 88 77", "Fatou Ndiaye")
        val joinResult = repository.joinTontineByInviteCode(tontine.inviteCode, joiningUser)
        assertTrue("Joining with valid code must succeed", joinResult.isSuccess)

        val members = repository.members.value[tontine.id]
        assertTrue(members?.any { it.userId == joiningUser.id } == true)
    }

    @Test
    fun testSubmitTransactionAndAntiDuplicate() {
        val admin = repository.createUser("Admin Tontine", "+237 690 00 00 01")
        val tontine = repository.createTontine(
            name = "Tontine Yaoundé",
            description = "Épargne",
            amount = 50000.0,
            frequency = TontineFrequency.MONTHLY,
            currency = "XAF",
            memberCount = 3
        )
        val cycle = repository.cycles.value[tontine.id]?.first()!!

        val contributor = User("u_contrib", "+237 690 11 22 33", "Paul Biya")
        val extractedReceipt = ExtractedReceiptData(
            transactionId = "OM.261023.1000.1234",
            amount = 50000.0,
            currency = "XAF",
            provider = "Orange Money"
        )

        // Submit first transaction
        val submitResult = repository.submitTransaction(
            cycleId = cycle.id,
            sender = contributor,
            receiverId = admin.id,
            receiverName = admin.fullName,
            receiverPhone = admin.phoneNumber,
            amount = 50000.0,
            currency = "XAF",
            extractedData = extractedReceipt
        )
        assertTrue("First submission must succeed", submitResult.isSuccess)

        // Check anti-duplicate: duplicate ID should now be detected
        assertTrue(
            "Transaction ID must be detected as duplicate",
            repository.isDuplicateTransactionId("OM.261023.1000.1234")
        )

        // Submit duplicate should fail
        val duplicateResult = repository.submitTransaction(
            cycleId = cycle.id,
            sender = contributor,
            receiverId = admin.id,
            receiverName = admin.fullName,
            receiverPhone = admin.phoneNumber,
            amount = 50000.0,
            currency = "XAF",
            extractedData = extractedReceipt
        )
        assertTrue("Submitting duplicate must fail", duplicateResult.isFailure)

        // Validate transaction and verify pot collection
        val txn = submitResult.getOrThrow()
        repository.validateTransaction(txn.id)
        val updatedTxn = repository.transactions.value.first { it.id == txn.id }
        assertEquals(TransactionStatus.VALIDATED, updatedTxn.status)

        val updatedCycle = repository.cycles.value[tontine.id]?.first()
        assertEquals(50000.0, updatedCycle?.potCollected ?: 0.0, 0.01)
    }

    @Test
    fun testUpdateUserProfileAndPreferences() {
        val user = repository.createUser("Ancien Nom", "+237 690 00 00 00")
        val newPrefs = NotificationPreferences(
            notifyInvitations = false,
            notifyDeadlines = true,
            notifyValidations = true,
            notifyPayoutTurn = false
        )

        repository.updateUserProfile(
            userId = user.id,
            fullName = "Nouveau Nom",
            phoneNumber = "+237 699 88 77 66",
            avatarUrl = "https://example.com/avatar.png",
            preferences = newPrefs
        )

        val updated = repository.currentUser.value
        assertNotNull(updated)
        assertEquals("Nouveau Nom", updated?.fullName)
        assertEquals("+237 699 88 77 66", updated?.phoneNumber)
        assertEquals("https://example.com/avatar.png", updated?.avatarUrl)
        assertFalse(updated!!.preferences.notifyInvitations)

        // Test delete photo
        repository.updateUserProfile(
            userId = user.id,
            fullName = updated.fullName,
            phoneNumber = updated.phoneNumber,
            avatarUrl = "",
            preferences = updated.preferences
        )
        assertEquals("", repository.currentUser.value?.avatarUrl)
    }

    @Test
    fun testDisplayModesAndNeutralAccent() {
        assertEquals(3, com.example.ui.theme.DisplayMode.entries.size)
        assertTrue(com.example.ui.theme.DisplayMode.entries.contains(com.example.ui.theme.DisplayMode.SYSTEM))
        assertTrue(com.example.ui.theme.DisplayMode.entries.contains(com.example.ui.theme.DisplayMode.LIGHT))
        assertTrue(com.example.ui.theme.DisplayMode.entries.contains(com.example.ui.theme.DisplayMode.DARK))

        assertTrue(com.example.ui.theme.AccentTheme.entries.contains(com.example.ui.theme.AccentTheme.NEUTRAL))
        assertEquals("Neutre", com.example.ui.theme.AccentTheme.NEUTRAL.labelFr)
    }

    @Test
    fun testResetAllDataReturnsToPristine() {
        val user = repository.createUser("Test User", "+225 01 02 03 04")
        repository.createTontine(
            name = "Tontine Test",
            description = "Desc",
            amount = 10000.0,
            frequency = TontineFrequency.WEEKLY,
            currency = "XOF",
            memberCount = 3
        )
        assertNotNull(repository.currentUser.value)
        assertEquals(1, repository.tontines.value.size)

        // Reset
        repository.resetAllData()

        assertNull("Current user must be null after reset", repository.currentUser.value)
        assertTrue("Users must be empty after reset", repository.allUsers.isEmpty())
        assertTrue("Tontines must be empty after reset", repository.tontines.value.isEmpty())
    }

    @Test
    fun testRegisterLoginAndResetPassword() {
        // Register user with password
        val regResult = repository.registerUser(
            fullName = "Koffi Annan",
            phoneNumber = "+233 24 000 1122",
            password = "SecretPassword123"
        )
        assertTrue("Registration must succeed", regResult.isSuccess)
        val user = regResult.getOrThrow()
        assertEquals("Koffi Annan", user.fullName)
        assertEquals(user, repository.currentUser.value)

        // Attempt duplicate registration with same phone
        val dupResult = repository.registerUser(
            fullName = "Duplicate Koffi",
            phoneNumber = "+233 24 000 1122",
            password = "AnotherPassword"
        )
        assertTrue("Duplicate phone registration must fail", dupResult.isFailure)

        // Logout
        repository.logout()
        assertNull("Current user must be null after logout", repository.currentUser.value)

        // Login with bad password
        val badLogin = repository.loginUser("+233 24 000 1122", "WrongPassword")
        assertTrue("Bad password login must fail", badLogin.isFailure)

        // Login with correct password
        val goodLogin = repository.loginUser("+233 24 000 1122", "SecretPassword123")
        assertTrue("Good password login must succeed", goodLogin.isSuccess)
        assertEquals(user.id, repository.currentUser.value?.id)

        // Reset password
        val resetRes = repository.resetPassword("+233 24 000 1122", "BrandNewPass456")
        assertTrue("Password reset must succeed", resetRes.isSuccess)

        // Logout and verify new password works
        repository.logout()
        val newLogin = repository.loginUser("+233 24 000 1122", "BrandNewPass456")
        assertTrue("Login with new password must succeed", newLogin.isSuccess)

        // Delete account
        repository.deleteAccount(user.id)
        assertNull("User must be logged out after deleting account", repository.currentUser.value)
        assertTrue("All users must be empty after deleting account", repository.allUsers.isEmpty())
    }

    @Test
    fun testBeneficiaryExemptionPotCalculation() {
        // When beneficiary is exempt: (memberCount - 1) * amount
        val tontineExempt = Tontine(
            id = "t_exempt",
            name = "Tontine Exempt",
            description = "Test",
            creatorId = "u_1",
            contributionAmount = 10000.0,
            frequency = TontineFrequency.MONTHLY,
            currency = "XOF",
            totalMembers = 5,
            inviteCode = "EXEMPT1",
            isBeneficiaryExempt = true
        )
        assertEquals(40000.0, tontineExempt.calculatePotTarget(5), 0.01)

        // When beneficiary is NOT exempt: memberCount * amount
        val tontineNonExempt = tontineExempt.copy(
            isBeneficiaryExempt = false
        )
        assertEquals(50000.0, tontineNonExempt.calculatePotTarget(5), 0.01)
    }

    @Test
    fun testTontineSettingsUpdateBroadcastsActivityAndNotifications() {
        val admin = repository.createUser("Admin Tontine", "+237 690 00 00 01")
        val tontine = repository.createTontine(
            name = "Tontine Test Notif",
            description = "Test Desc",
            amount = 25000.0,
            frequency = TontineFrequency.MONTHLY,
            currency = "XAF",
            memberCount = 3
        )
        // Add member
        repository.addMember(tontine.id, "Membre 2", "+237 690 22 22 22")

        // Update settings
        repository.updateTontineSettings(
            tontineId = tontine.id,
            name = "Tontine Modifiée",
            description = "Nouvelle desc",
            amount = 30000.0,
            frequency = TontineFrequency.MONTHLY,
            currency = "XAF",
            startDate = "05 du mois",
            endDate = "10 du mois",
            latePenaltyFee = 2000.0
        )

        // Activity must exist
        val activities = repository.activities.value.filter { it.tontineId == tontine.id }
        assertTrue("Activity must be created for settings update", activities.any { it.type == ActivityType.TONTINE_UPDATED })

        // Notifications must exist for the member
        val notifs = repository.notifications.value
        assertTrue("Notification must be sent to tontine members", notifs.any { it.type == NotificationType.SYSTEM })
    }

    @Test
    fun testAddMemberUpdatesCycleExpectedPotAndActivities() {
        val admin = repository.createUser("Admin Test", "+237 690 11 11 11")
        val tontine = repository.createTontine(
            name = "Tontine Dynamique",
            description = "Test",
            amount = 20000.0,
            frequency = TontineFrequency.MONTHLY,
            currency = "XAF",
            memberCount = 2,
            isBeneficiaryExempt = true
        )

        // Initial cycle expected pot with 1 member: 1 paying member (coerced to 1): 20000.0
        val cycle1 = repository.cycles.value[tontine.id]?.first()
        assertEquals(20000.0, cycle1?.potExpected ?: 0.0, 0.01)

        // Add 2nd member: totalMembers = 2, paying members (exempt) = (2-1) = 1 -> 20000.0
        repository.addMember(tontine.id, "Membre B", "+237 690 22 22 22")
        val cycle2 = repository.cycles.value[tontine.id]?.first()
        assertEquals(20000.0, cycle2?.potExpected ?: 0.0, 0.01)

        // Add 3rd member: totalMembers = 3, paying members (exempt) = (3-1) = 2 -> 40000.0
        repository.addMember(tontine.id, "Membre C", "+237 690 33 33 33")
        val cycle3 = repository.cycles.value[tontine.id]?.first()
        assertEquals(40000.0, cycle3?.potExpected ?: 0.0, 0.01)

        val activities = repository.activities.value.filter { it.tontineId == tontine.id }
        assertTrue("Member joined activity must be recorded", activities.any { it.type == ActivityType.MEMBER_JOINED })
    }

    @Test
    fun testMobileMoneyPhonePattern() {
        val phoneRegex = Regex("""^(\+?[0-9]{8,15})$""")
        fun isValid(p: String) = p.replace(" ", "").replace("-", "").matches(phoneRegex)

        assertTrue(isValid("+237690000000"))
        assertTrue(isValid("+237 690 00 00 00"))
        assertTrue(isValid("+225 07 12 34 56"))
        assertTrue(isValid("690000000"))
        assertFalse(isValid("123"))
        assertFalse(isValid("phone_text"))
    }
}

