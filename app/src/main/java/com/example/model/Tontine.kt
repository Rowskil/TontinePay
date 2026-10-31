package com.example.model

enum class TontineFrequency(val labelFr: String) {
    DAILY("Quotidien"),
    WEEKLY("Hebdomadaire"),
    BIWEEKLY("Bimensuelle"),
    MONTHLY("Mensuelle")
}

data class Tontine(
    val id: String,
    val name: String,
    val description: String,
    val creatorId: String,
    val contributionAmount: Double,
    val frequency: TontineFrequency = TontineFrequency.MONTHLY,
    val currency: String = "XAF",
    val inviteCode: String = "",
    val totalMembers: Int = 0,
    val activeCycleNumber: Int = 1,
    val totalCycles: Int = 1,
    val startDate: String = "01 Oct 2026",
    val endDate: String = "25 Oct 2026",
    val latePenaltyFee: Double = 0.0,
    val isBeneficiaryExempt: Boolean = true,
    val coAdminId: String? = null,
    val coAdminName: String? = null,
    val nextTurnStartDate: String? = null,
    val nextTurnBeneficiaryName: String? = null,
    val isPendingLaunch: Boolean = false,
    val autoRotation: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun calculatePotTarget(memberCount: Int): Double {
        val payingMembers = if (isBeneficiaryExempt) (memberCount - 1).coerceAtLeast(1) else memberCount.coerceAtLeast(1)
        return contributionAmount * payingMembers
    }

    fun isUserAdmin(userId: String): Boolean {
        return creatorId == userId || (coAdminId != null && coAdminId == userId)
    }

    val isEffectivelyPending: Boolean
        get() = isPendingLaunch || description.contains("Lancement prévu", ignoreCase = true)
}
