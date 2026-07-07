package com.nojus.loantracker.data

import com.google.firebase.firestore.DocumentId
import kotlin.math.ceil
import kotlin.math.floor

enum class LoanStatus { PENDING, ACTIVE, PAID, OVERDUE, DECLINED, DELETED }

/**
 * Who initiated the loan: a lender offering money (OFFER) or a borrower asking
 * for it (REQUEST). The other participant accepts or declines. Loans created
 * before this field existed deserialize as OFFER, which matches their history.
 */
enum class LoanKind { OFFER, REQUEST }

/**
 * How the borrower pays the loan back. NONE = single payment on the due date.
 * MONTHLY uses a 30-day approximation so installment math stays calendar-independent.
 */
enum class RepaymentInterval(val days: Int?, val label: String, val per: String) {
    NONE(null, "One-time", ""),
    WEEKLY(7, "Weekly", "week"),
    BIWEEKLY(14, "Every 2 weeks", "2 weeks"),
    MONTHLY(30, "Monthly", "month")
}

private const val MILLIS_PER_DAY = 1000L * 60 * 60 * 24

/**
 * Number of installments needed to repay by [dueAt] when paying once per [interval],
 * starting at [fromMillis]. Always at least 1.
 */
fun installmentCountFor(interval: RepaymentInterval, fromMillis: Long, dueAt: Long): Int {
    val intervalDays = interval.days ?: return 1
    val span = dueAt - fromMillis
    if (span <= 0) return 1
    val days = ceil(span.toDouble() / MILLIS_PER_DAY)
    return ceil(days / intervalDays).toInt().coerceAtLeast(1)
}

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
 * repaymentInterval / installmentCount: optional installment plan. The count is fixed
 * at creation time so both sides always see the same schedule.
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
    val repaymentInterval: RepaymentInterval = RepaymentInterval.NONE,
    val installmentCount: Int = 1,
    val note: String = "",
    val kind: LoanKind = LoanKind.OFFER,
    val status: LoanStatus = LoanStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val acceptedAt: Long? = null,
    val paidAt: Long? = null,
    val deletedAt: Long? = null,
    /** Lower-cased emails for cheap querying. */
    val participants: List<String> = emptyList()
) {
    val totalDue: Double get() = principal * interestMultiplier
    val installmentAmount: Double get() = totalDue / installmentCount.coerceAtLeast(1)

    /** The participant who created this loan: lender for offers, borrower for requests. */
    val creatorEmail: String get() =
        if (kind == LoanKind.REQUEST) borrowerEmail else lenderEmail

    /** The participant who must respond to a pending loan. */
    val recipientEmail: String get() =
        if (kind == LoanKind.REQUEST) lenderEmail else borrowerEmail

    fun isCreator(email: String): Boolean = creatorEmail.equals(email, ignoreCase = true)

    /**
     * Concrete payment plan: equal cent-rounded installments spaced [repaymentInterval]
     * apart, ending exactly on [dueAt]. The last payment absorbs the rounding remainder
     * so the amounts always sum to [totalDue].
     */
    fun paymentSchedule(): List<ScheduledPayment> {
        val count = installmentCount.coerceAtLeast(1)
        val intervalMillis = (repaymentInterval.days ?: 0) * MILLIS_PER_DAY
        val perPayment = floor(totalDue / count * 100) / 100
        return List(count) { i ->
            ScheduledPayment(
                number = i + 1,
                dueAt = dueAt - (count - 1 - i) * intervalMillis,
                amount = if (i == count - 1) totalDue - perPayment * (count - 1) else perPayment
            )
        }
    }
}

data class ScheduledPayment(val number: Int, val dueAt: Long, val amount: Double)

/** A borrower email a given lender has previously sent a loan to. */
data class SavedContact(
    @DocumentId val email: String = "",
    val displayName: String = "",
    val lastUsedAt: Long = System.currentTimeMillis()
)
