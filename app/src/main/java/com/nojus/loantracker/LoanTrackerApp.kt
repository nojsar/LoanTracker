package com.nojus.loantracker

import android.app.Application
import com.nojus.loantracker.notifications.LoanEventNotifier
import com.nojus.loantracker.notifications.LoanNotifications
import com.nojus.loantracker.notifications.PaymentReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LoanTrackerApp : Application() {

    // Lives as long as the process; hosts the loan listener that turns
    // counterparty actions into notifications.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        LoanNotifications.ensureChannels(this)
        LoanEventNotifier(this).start(appScope)
        PaymentReminderWorker.schedule(this)
    }
}
