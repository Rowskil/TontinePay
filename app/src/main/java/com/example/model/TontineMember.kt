package com.example.model

enum class MemberRole(val labelFr: String) {
    ADMIN("Administrateur"),
    CO_ADMIN("Admin Second"),
    TREASURER("Trésorier"),
    MEMBER("Membre")
}

enum class CotisationStatus {
    VALIDATED,           // Vert (Reçu validé)
    PENDING,             // Jaune (En attente de validation du receveur)
    UNPAID,              // Rouge (Non payé dans les délais)
    LATE_UNPAID,         // Rouge vif (En retard avec amende de pénalité)
    BENEFICIARY_EXEMPT   // Or/Bleu (Le bénéficiaire du tour ne cotise pas ce tour-ci)
}

data class TontineMember(
    val id: String,
    val tontineId: String,
    val userId: String,
    val fullName: String,
    val phoneNumber: String,
    val role: MemberRole = MemberRole.MEMBER,
    val payoutOrder: Int,
    val avatarUrl: String = "",
    val aliasLabel: String = "",       // e.g. "(Part 2)", "(Main 2)", "#2"
    val isPendingApproval: Boolean = false,
    val joinedAt: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() = if (aliasLabel.isNotBlank()) "$fullName $aliasLabel" else fullName

    fun getPartDisplayName(allMembers: List<TontineMember>): String {
        val userParts = allMembers.filter { it.userId == this.userId && !it.isPendingApproval }
            .sortedWith(compareBy({ it.payoutOrder }, { it.joinedAt }))
        return if (userParts.size > 1) {
            val index = userParts.indexOfFirst { it.id == this.id }
            val partNum = if (index >= 0) index + 1 else {
                val all = allMembers.filter { it.userId == this.userId }
                    .sortedWith(compareBy({ it.payoutOrder }, { it.joinedAt }))
                val idx = all.indexOfFirst { it.id == this.id }
                if (idx >= 0) idx + 1 else 1
            }
            "$fullName (Nom #$partNum)"
        } else if (aliasLabel.isNotBlank()) {
            "$fullName $aliasLabel"
        } else {
            fullName
        }
    }
}
