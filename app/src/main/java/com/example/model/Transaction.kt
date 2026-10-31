package com.example.model

enum class TransactionStatus {
    PENDING_AI,
    PENDING_VALIDATION,
    VALIDATED,
    REJECTED
}

data class Transaction(
    val id: String,
    val cycleId: String,
    val senderId: String,
    val senderMemberId: String = "",
    val senderName: String,
    val senderPhone: String,
    val receiverId: String,
    val receiverMemberId: String = "",
    val receiverName: String,
    val receiverPhone: String,
    val amount: Double,
    val currency: String = "XAF",
    val proofImageUrl: String = "",
    val proofBitmapKey: String = "", // for locally loaded bitmaps
    val extractedData: ExtractedReceiptData = ExtractedReceiptData(),
    val status: TransactionStatus = TransactionStatus.PENDING_VALIDATION,
    val rejectionReason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val validatedAt: Long? = null
)
