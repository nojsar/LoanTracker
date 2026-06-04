package com.nojus.loantracker.data

import com.google.firebase.firestore.DocumentId

enum class LoanStatus { PENDING, ACTIVE, PAID, OVERDUE, DECLINED, DELETED }

data class UserProfile(
    @DocumentId val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = ""
)

/**
 * Loan stored in Firestore.
 *
 * principal: original amount borrowed (EUR).
 * interestMultiplier: one-time multiplier applied to principal (1.0 = no interest, 1.10 = +10%).
 * totalDue = principal * interestMultiplier, computed on read.
 * dueAt: epoch millis the loan must be repaid by.
 * defaultConsequence: free-text describing what happens if it isn't paid on time.
 */
data class Loan(
    @DocumentId val id: String = "",
    val lenderUid: String = "",
    val lenderEmail: String = "",
    val lenderName: String = "",
    val borrowerEmail: String = "",
    val borrowerUid: String = "",
    val borrowerName: String = "",
    val principal: Double = 0.0,
    val interestMultiplier: Double = 1.0,
    val currency: String = "EUR",
    val dueAt: Long = 0L,
    val defaultConsequence: String = "",
    val note: String = "",
    val status: LoanStatus = LoanStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val acceptedAt: Long? = null,
    val paidAt: Long? = null,
    val deletedAt: Long? = null,
    /** Lower-cased emails for cheap querying. */
    val participants: List<String> = emptyList()
) {
    val totalDue: Double get() = principal * interestMultiplier
}

/** A borrower email a given lender has previously sent a loan to. */
data class SavedContact(
    @DocumentId val email: String = "",
    val displayName: String = "",
    val lastUsedAt: Long = System.currentTimeMillis()
)
