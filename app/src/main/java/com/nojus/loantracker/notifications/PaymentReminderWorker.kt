package com.nojus.loantracker.notifications

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nojus.loantracker.data.Loan
import com.nojus.loantracker.data.LoanStatus
import com.nojus.loantracker.ui.formatDate
import com.nojus.loantracker.ui.formatMoney
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Runs twice a day and reminds the borrower about their next payment when it's
 * within three days or overdue. One notification per loan, replaced on each run,
 * so it never piles up.
 */
class PaymentReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        val email = user.email?.lowercase().orEmpty()
        if (email.isBlank()) return Result.success()

        val snapshot = runCatching {
            FirebaseFirestore.getInstance().collection("loans")
                .whereArrayContains("participants", email)
                .get()
                .await()
        }.getOrElse { t ->
            Log.w(TAG, "reminder query failed", t)
            return Result.retry()
        }

        val now = System.currentTimeMillis()
        snapshot.documents
            .mapNotNull { it.toObject(Loan::class.java) }
            .filter { it.status == LoanStatus.ACTIVE && it.borrowerEmail.equals(email, ignoreCase = true) }
            .forEach { loan ->
                val next = loan.nextPayment() ?: return@forEach
                val millisLeft = next.dueAt - now
                if (millisLeft > SOON_MILLIS) return@forEach

                val amount = formatMoney(next.amount, loan.currency)
                val lender = loan.lenderName.ifBlank { loan.lenderEmail }
                val (title, text) = if (millisLeft < 0) {
                    "Payment overdue" to
                        "$amount to $lender was due ${formatDate(next.dueAt)}."
                } else {
                    val days = (millisLeft / MILLIS_PER_DAY).toInt()
                    val whenText = when (days) {
                        0 -> "today"
                        1 -> "tomorrow"
                        else -> "in $days days"
                    }
                    "Payment due $whenText" to
                        "$amount to $lender, due ${formatDate(next.dueAt)}."
                }
                LoanNotifications.notify(
                    applicationContext,
                    LoanNotifications.CHANNEL_REMINDERS,
                    notificationId = ("reminder-" + loan.id).hashCode(),
                    title = title,
                    text = text,
                    loanId = loan.id
                )
            }
        return Result.success()
    }

    companion object {
        private const val TAG = "PaymentReminderWorker"
        private const val MILLIS_PER_DAY = 1000L * 60 * 60 * 24
        private const val SOON_MILLIS = 3 * MILLIS_PER_DAY
        private const val WORK_NAME = "payment-reminders"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PaymentReminderWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
