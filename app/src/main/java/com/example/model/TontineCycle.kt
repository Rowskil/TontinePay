package com.example.model

enum class CycleStatus {
    ACTIVE,
    COMPLETED,
    UPCOMING
}

data class TontineCycle(
    val id: String,
    val tontineId: String,
    val cycleNumber: Int,
    val beneficiaryId: String,
    val beneficiaryMemberId: String = "",
    val beneficiaryName: String,
    val beneficiaryPhone: String,
    val dueDate: String, // e.g. "30 Oct 2026"
    val status: CycleStatus = CycleStatus.ACTIVE,
    val potExpected: Double,
    val potCollected: Double = 0.0,
    val currency: String = "XAF"
)
