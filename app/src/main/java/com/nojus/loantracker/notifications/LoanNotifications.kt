package com.nojus.loantracker.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nojus.loantracker.MainActivity
import com.nojus.loantracker.R

/**
 * Channels and a single posting helper for everything the app notifies about.
 * Tapping any notification opens the loan it belongs to.
 */
object LoanNotifications {
    const val EXTRA_LOAN_ID = "extra_loan_id"
    const val CHANNEL_ACTIVITY = "loan_activity"
    const val CHANNEL_REMINDERS = "payment_reminders"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ACTIVITY,
                "Loan activity",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "New offers and requests, acceptances, and payments" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Payment reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Upcoming and overdue payments" }
        )
    }

    fun notify(
        context: Context,
        channel: String,
        notificationId: Int,
        title: String,
        text: String,
        loanId: String
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_LOAN_ID, loanId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_loan)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        // Permission can be revoked between the check above and here; never crash for a notification.
        runCatching { manager.notify(notificationId, notification) }
    }
}
