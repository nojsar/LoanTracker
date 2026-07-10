package com.nojus.loantracker.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.nojus.loantracker.data.Loan
import com.nojus.loantracker.data.LoanKind
import com.nojus.loantracker.data.LoanRepository
import com.nojus.loantracker.data.LoanStatus
import com.nojus.loantracker.data.PaymentStatus
import com.nojus.loantracker.ui.formatDate
import com.nojus.loantracker.ui.formatMoney
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Watches the signed-in user's loans and posts a notification whenever the
 * *other* participant did something: sent an offer, accepted, declined,
 * requested a payment, answered one, or settled the loan.
 *
 * State is remembered per loan in SharedPreferences as a small fingerprint, so
 * only real transitions notify — nothing fires on app start for old news, and
 * your own actions never notify you (they're diffed against a fingerprint that
 * updates in the same snapshot your action produces, with rules keyed to which
 * side changed).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoanEventNotifier(
    private val context: Context,
    private val repo: LoanRepository = LoanRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val prefs = context.getSharedPreferences("loan_notify_state", Context.MODE_PRIVATE)

    private data class Update(val uid: String, val email: String, val loans: List<Loan>)

    fun start(scope: CoroutineScope) {
        val users = MutableStateFlow(auth.currentUser)
        auth.addAuthStateListener { users.value = it.currentUser }
        scope.launch {
            users.flatMapLatest { user ->
                val email = user?.email?.lowercase().orEmpty()
                if (user == null || email.isBlank()) flowOf(null)
                else repo.loansForUser(user.uid, email)
                    .map { Update(user.uid, email, it) }
                    .catch { t ->
                        Log.w(TAG, "loan stream for notifications failed", t)
                        emit(Update(user.uid, email, emptyList()))
                    }
            }.collect { update ->
                if (update != null && update.loans.isNotEmpty()) {
                    diffAndNotify(update.uid, update.email, update.loans)
                }
            }
        }
    }

    private fun diffAndNotify(uid: String, email: String, loans: List<Loan>) {
        val seenKey = "seen_$uid"
        val firstRun = !prefs.getBoolean(seenKey, false)
        val editor = prefs.edit()
        loans.forEach { loan ->
            val key = "fp_${uid}_${loan.id}"
            val old = prefs.getString(key, null)
            val new = fingerprint(loan)
            if (new != old) {
                if (!firstRun) notifyChange(email, loan, old)
                editor.putString(key, new)
            }
        }
        editor.putBoolean(seenKey, true).apply()
    }

    private fun fingerprint(loan: Loan): String =
        loan.status.name + "|" +
            loan.payments.joinToString(",") { "${it.id}:${it.status.name}" }

    private fun notifyChange(email: String, loan: Loan, oldFingerprint: String?) {
        val oldStatus = oldFingerprint?.substringBefore('|')
        val oldPayments = oldFingerprint?.substringAfter('|', "")
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.associate { it.substringBeforeLast(':') to it.substringAfterLast(':') }
            .orEmpty()

        val youAreLender = loan.lenderEmail.equals(email, ignoreCase = true)
        val counterparty = if (youAreLender) {
            loan.borrowerName.ifBlank { loan.borrowerEmail }
        } else {
            loan.lenderName.ifBlank { loan.lenderEmail }
        }
        val total = formatMoney(loan.totalDue, loan.currency)
        val messages = mutableListOf<Pair<String, String>>()

        // A loan I haven't seen before that needs my answer.
        if (oldStatus == null && loan.status == LoanStatus.PENDING && !loan.isCreator(email)) {
            messages += if (loan.kind == LoanKind.REQUEST) {
                "Loan request from $counterparty" to
                    "They ask to borrow $total, due ${formatDate(loan.dueAt)}."
            } else {
                "Loan offer from $counterparty" to
                    "$total due ${formatDate(loan.dueAt)} — open to accept or decline."
            }
        }

        // My own offer/request got answered.
        if (oldStatus == LoanStatus.PENDING.name && loan.isCreator(email)) {
            when (loan.status) {
                LoanStatus.ACTIVE -> messages += "Loan accepted" to
                    "$counterparty accepted the $total loan."
                LoanStatus.DECLINED -> messages += "Loan declined" to
                    "$counterparty declined the $total loan."
                else -> Unit
            }
        }

        // Payment requests and their answers.
        loan.payments.forEach { payment ->
            val before = oldPayments[payment.id]
            val amount = formatMoney(payment.amount, loan.currency)
            when {
                before == null && payment.status == PaymentStatus.REQUESTED && youAreLender ->
                    messages += "Payment from $counterparty" to
                        "They say they've paid $amount — confirm to deduct it."
                before == PaymentStatus.REQUESTED.name &&
                    payment.status == PaymentStatus.CONFIRMED && !youAreLender ->
                    messages += "Payment confirmed" to
                        "$counterparty confirmed your $amount payment."
                before == PaymentStatus.REQUESTED.name &&
                    payment.status == PaymentStatus.DECLINED && !youAreLender ->
                    messages += "Payment declined" to
                        "$counterparty declined your $amount payment."
            }
        }

        // The lender settled the whole loan (skip when a confirmed payment
        // already tells the borrower the same thing).
        if (oldStatus == LoanStatus.ACTIVE.name && loan.status == LoanStatus.PAID &&
            !youAreLender && messages.isEmpty()
        ) {
            messages += "Loan paid off" to "The $total loan from $counterparty is settled."
        }

        messages.forEachIndexed { i, (title, text) ->
            LoanNotifications.notify(
                context,
                LoanNotifications.CHANNEL_ACTIVITY,
                notificationId = (loan.id + "#" + i).hashCode(),
                title = title,
                text = text,
                loanId = loan.id
            )
        }
    }

    private companion object { const val TAG = "LoanEventNotifier" }
}
