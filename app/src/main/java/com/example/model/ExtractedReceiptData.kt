package com.example.model

data class ExtractedReceiptData(
    val transactionId: String = "",
    val amount: Double = 0.0,
    val currency: String = "XAF",
    val senderPhone: String = "",
    val recipientPhone: String = "",
    val allDetectedPhones: List<String> = emptyList(),
    val provider: String = "Orange Money", // Orange Money, MTN MoMo, Wave, Moov
    val timestamp: String = "",
    val confidence: Float = 0.95f,
    val rawJson: String = "",
    val fullOcrText: String = ""
)
