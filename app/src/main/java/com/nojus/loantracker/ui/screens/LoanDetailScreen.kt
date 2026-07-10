package com.nojus.loantracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseUser
import com.nojus.loantracker.data.CENT_EPSILON
import com.nojus.loantracker.data.Loan
import com.nojus.loantracker.data.LoanKind
import com.nojus.loantracker.data.LoanPayment
import com.nojus.loantracker.data.LoanRepository
import com.nojus.loantracker.data.LoanStatus
import com.nojus.loantracker.data.PaymentStatus
import com.nojus.loantracker.data.RepaymentInterval
import com.nojus.loantracker.ui.DueUrgency
import com.nojus.loantracker.ui.LoanViewModel
import com.nojus.loantracker.ui.dueUrgency
import com.nojus.loantracker.ui.formatDate
import com.nojus.loantracker.ui.formatMoney
import com.nojus.loantracker.ui.humanizeUntil
import java.util.Locale

/** Which action is waiting for an "are you sure?" answer. */
private enum class PendingConfirm { Accept, Decline, MarkPaid, ConfirmPayment, DeclinePayment, CancelPayRequest }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanDetailScreen(
    loanId: String,
    user: FirebaseUser,
    viewModel: LoanViewModel,
    onBack: () -> Unit
) {
    val repo = remember { LoanRepository() }
    val loan by repo.loanById(loanId).collectAsState(initial = null)
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf<PendingConfirm?>(null) }
    var payDialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Loan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val current = loan
                    val isLender = current != null &&
                        current.lenderEmail.equals(user.email.orEmpty(), ignoreCase = true)
                    // Creators can withdraw their own pending offer/request.
                    val canWithdraw = current != null &&
                        current.isCreator(user.email.orEmpty()) &&
                        current.status == LoanStatus.PENDING
                    val notAlreadyDeleted = current?.status != LoanStatus.DELETED
                    if ((isLender || canWithdraw) && notAlreadyDeleted) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete loan")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val current = loan
        if (current == null) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val youAreLender = current.lenderEmail.equals(user.email, ignoreCase = true)
        val youAreCreator = current.isCreator(user.email.orEmpty())

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AmountCard(current)

            if (current.repaymentInterval != RepaymentInterval.NONE) {
                ScheduleCard(current)
            }

            current.pendingPayment?.let { payment ->
                PendingPaymentCard(
                    loan = current,
                    payment = payment,
                    youAreLender = youAreLender,
                    onConfirm = { pendingConfirm = PendingConfirm.ConfirmPayment },
                    onDecline = { pendingConfirm = PendingConfirm.DeclinePayment },
                    onCancel = { pendingConfirm = PendingConfirm.CancelPayRequest }
                )
            }

            if (current.payments.any { it.status != PaymentStatus.REQUESTED }) {
                PaymentsCard(current)
            }

            DetailsCard(current, youAreLender, youAreCreator)

            if (current.defaultConsequence.isNotBlank()) {
                ConsequenceCard(current.defaultConsequence)
            }

            if (current.note.isNotBlank()) {
                NoteCard(current.note)
            }

            ActionButtons(
                loan = current,
                youAreLender = youAreLender,
                youAreCreator = youAreCreator,
                onAccept = { pendingConfirm = PendingConfirm.Accept },
                onDecline = { pendingConfirm = PendingConfirm.Decline },
                onMarkPaid = { pendingConfirm = PendingConfirm.MarkPaid },
                onPay = { payDialogOpen = true }
            )

            Spacer(Modifier.height(16.dp))
        }
    }

    val confirming = pendingConfirm
    val currentLoan = loan
    if (confirming != null && currentLoan != null) {
        ConfirmActionDialog(
            confirm = confirming,
            loan = currentLoan,
            onDismiss = { pendingConfirm = null },
            onConfirmed = {
                when (confirming) {
                    PendingConfirm.Accept -> {
                        viewModel.acceptLoan(currentLoan)
                        onBack()
                    }
                    PendingConfirm.Decline -> {
                        viewModel.declineLoan(currentLoan.id)
                        onBack()
                    }
                    PendingConfirm.MarkPaid -> viewModel.markPaid(currentLoan.id)
                    PendingConfirm.ConfirmPayment -> currentLoan.pendingPayment?.let {
                        viewModel.respondToPayment(currentLoan.id, it.id, confirm = true)
                    }
                    PendingConfirm.DeclinePayment -> currentLoan.pendingPayment?.let {
                        viewModel.respondToPayment(currentLoan.id, it.id, confirm = false)
                    }
                    PendingConfirm.CancelPayRequest -> currentLoan.pendingPayment?.let {
                        viewModel.cancelPaymentRequest(currentLoan.id, it.id)
                    }
                }
                pendingConfirm = null
            }
        )
    }

    if (payDialogOpen && currentLoan != null) {
        PayLoanDialog(
            loan = currentLoan,
            onDismiss = { payDialogOpen = false },
            onSend = { amount ->
                viewModel.requestPayment(currentLoan.id, amount)
                payDialogOpen = false
            }
        )
    }

    if (confirmDelete) {
        val current = loan
        val pendingWord = if (current?.kind == LoanKind.REQUEST) "request" else "offer"
        val explanation = when (current?.status) {
            LoanStatus.PENDING -> "The $pendingWord will be withdrawn. It moves to history with a Deleted tag — turn on \"Show loan history\" to find it."
            LoanStatus.ACTIVE -> "This marks the loan as deleted on both sides. Use only if you've settled it outside the app. It stays in history under a Deleted tag."
            LoanStatus.PAID, LoanStatus.DECLINED, LoanStatus.OVERDUE, LoanStatus.DELETED, null ->
                "Moves the loan to history with a Deleted tag on both sides."
        }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this loan?") },
            text = { Text(explanation) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLoan(loanId)
                    confirmDelete = false
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

/** One "are you sure?" dialog for every loan action that can't be taken back. */
@Composable
private fun ConfirmActionDialog(
    confirm: PendingConfirm,
    loan: Loan,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    val pendingAmount = loan.pendingPayment?.let { formatMoney(it.amount, loan.currency) } ?: ""
    val (title, text, confirmLabel) = when (confirm) {
        PendingConfirm.Accept -> Triple(
            if (loan.kind == LoanKind.REQUEST) "Accept and lend?" else "Accept this loan?",
            "You agree to ${formatMoney(loan.totalDue, loan.currency)} due by ${formatDate(loan.dueAt)}. " +
                "Both sides will see the loan as active.",
            "Accept"
        )
        PendingConfirm.Decline -> Triple(
            if (loan.kind == LoanKind.REQUEST) "Decline this request?" else "Decline this offer?",
            "It disappears from your list. ${loan.creatorEmail} keeps a declined record.",
            "Decline"
        )
        PendingConfirm.MarkPaid -> Triple(
            "Mark the whole loan as paid?",
            "This settles the full ${formatMoney(loan.totalDue, loan.currency)} and closes the loan for both sides.",
            "Mark paid"
        )
        PendingConfirm.ConfirmPayment -> Triple(
            "Confirm payment of $pendingAmount?",
            "The amount is deducted from what ${loan.borrowerEmail} still owes. This can't be undone.",
            "Confirm"
        )
        PendingConfirm.DeclinePayment -> Triple(
            "Decline payment of $pendingAmount?",
            "Nothing is deducted. ${loan.borrowerEmail} can send a new request.",
            "Decline"
        )
        PendingConfirm.CancelPayRequest -> Triple(
            "Cancel your payment request?",
            "Your $pendingAmount request is withdrawn before the lender answers it.",
            "Cancel request"
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirmed) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } }
    )
}

/** Borrower picks how much to pay; the lender still has to confirm it. */
@Composable
private fun PayLoanDialog(
    loan: Loan,
    onDismiss: () -> Unit,
    onSend: (Double) -> Unit
) {
    val suggested = loan.nextPayment()?.amount ?: loan.remainingDue
    var amountText by remember {
        mutableStateOf(String.format(Locale.US, "%.2f", suggested))
    }
    val amount = amountText.toDoubleOrNull() ?: 0.0
    val tooMuch = amount > loan.remainingDue + CENT_EPSILON
    val valid = amount > 0.0 && !tooMuch

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay loan") },
        text = {
            Column {
                Text(
                    "Tell ${loan.lenderEmail} how much you've paid. " +
                        "It counts once they confirm, and any extra rolls into the next payment.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { text ->
                        amountText = text
                            .filter { c -> c.isDigit() || c == '.' || c == ',' }
                            .replace(',', '.')
                    },
                    label = { Text("Amount") },
                    prefix = { Text("€") },
                    isError = amountText.isNotBlank() && !valid,
                    supportingText = {
                        Text(
                            if (tooMuch)
                                "That's more than the ${formatMoney(loan.remainingDue, loan.currency)} left"
                            else
                                "${formatMoney(loan.remainingDue, loan.currency)} left on this loan"
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(amount) }, enabled = valid) { Text("Send request") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** A payment request waiting for an answer — actions differ by which side you're on. */
@Composable
private fun PendingPaymentCard(
    loan: Loan,
    payment: LoanPayment,
    youAreLender: Boolean,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.HourglassEmpty,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(Modifier.size(8.dp))
                Column {
                    Text(
                        "Payment of ${formatMoney(payment.amount, loan.currency)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        if (youAreLender)
                            "${loan.borrowerEmail} says they've paid this — confirm to deduct it."
                        else
                            "Waiting for ${loan.lenderEmail} to confirm.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (youAreLender) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Decline") }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Confirm") }
                }
            } else {
                TextButton(onClick = onCancel) { Text("Cancel request") }
            }
        }
    }
}

/** Answered payment requests, newest first. */
@Composable
private fun PaymentsCard(loan: Loan) {
    val answered = loan.payments
        .filter { it.status != PaymentStatus.REQUESTED }
        .sortedByDescending { it.requestedAt }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Payments", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            answered.forEachIndexed { i, payment ->
                val confirmed = payment.status == PaymentStatus.CONFIRMED
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            formatMoney(payment.amount, loan.currency),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (confirmed) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatDate(payment.respondedAt ?: payment.requestedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (confirmed) "Confirmed" else "Declined",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (confirmed) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                    )
                }
                if (i < answered.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AmountCard(loan: Loan) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "TOTAL DUE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatMoney(loan.totalDue, loan.currency),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (loan.interestMultiplier != 1.0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatMoney(loan.principal, loan.currency)} × ${"%.2f".format(loan.interestMultiplier)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
            if (loan.repaymentInterval != RepaymentInterval.NONE) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${loan.installmentCount} × ${formatMoney(loan.installmentAmount, loan.currency)} — one every ${loan.repaymentInterval.per}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
            if (loan.paidSoFar > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatMoney(loan.paidSoFar, loan.currency)} paid · ${formatMoney(loan.remainingDue, loan.currency)} left",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(12.dp))
            StatusChip(loan.status, loan.kind)
            Spacer(Modifier.height(10.dp))
            val urgency = dueUrgency(loan.dueAt)
            val deadlineMatters = loan.status == LoanStatus.ACTIVE || loan.status == LoanStatus.PENDING
            Text(
                "Due ${formatDate(loan.dueAt)} · ${humanizeUntil(loan.dueAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (deadlineMatters && urgency != DueUrgency.Normal)
                    MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = if (deadlineMatters && urgency != DueUrgency.Normal)
                    FontWeight.SemiBold
                else null
            )
        }
    }
}

/** The concrete payment plan, with paid installments ticked off and the next one highlighted. */
@Composable
private fun ScheduleCard(loan: Loan) {
    val schedule = loan.paymentSchedule()
    val now = System.currentTimeMillis()
    val nextNumber = schedule.firstOrNull { !it.settled }?.number
    val settled = loan.status == LoanStatus.PAID ||
        loan.status == LoanStatus.DELETED || loan.status == LoanStatus.DECLINED

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Payments,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Payment plan",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.weight(1f))
                Text(
                    loan.repaymentInterval.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            schedule.forEachIndexed { i, payment ->
                val isCovered = settled || payment.settled
                val isNext = !settled && payment.number == nextNumber
                val isPast = !settled && !isCovered && payment.dueAt < now
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = when {
                            isCovered -> MaterialTheme.colorScheme.primaryContainer
                            isNext -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isCovered) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Paid",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text(
                                    "${payment.number}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isNext) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            formatDate(payment.dueAt),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isNext) FontWeight.SemiBold else null,
                            color = when {
                                isPast -> MaterialTheme.colorScheme.error
                                settled || isCovered -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (isNext) {
                            Text(
                                if (payment.covered > 0)
                                    "next · ${formatMoney(payment.covered, loan.currency)} already covered · ${humanizeUntil(payment.dueAt)}"
                                else
                                    "next payment · ${humanizeUntil(payment.dueAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        if (isNext && payment.covered > 0)
                            formatMoney(payment.remaining, loan.currency)
                        else
                            formatMoney(payment.amount, loan.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (settled || isCovered) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (i < schedule.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 40.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsCard(loan: Loan, youAreLender: Boolean, youAreCreator: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            DetailRow("Lender", if (youAreLender) "You" else loan.lenderEmail.ifBlank { "—" })
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailRow("Borrower", if (!youAreLender) "You" else loan.borrowerEmail)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailRow(
                if (loan.kind == LoanKind.REQUEST) "Requested by" else "Offered by",
                if (youAreCreator) "You" else loan.creatorEmail
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailRow("Principal", formatMoney(loan.principal, loan.currency))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailRow(
                "Interest multiplier",
                if (loan.interestMultiplier == 1.0) "None" else "× ${"%.2f".format(loan.interestMultiplier)}",
                icon = Icons.Filled.Bolt
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailRow(
                "Repayment",
                if (loan.repaymentInterval == RepaymentInterval.NONE) "One-time"
                else "${loan.repaymentInterval.label} · ${loan.installmentCount} × ${formatMoney(loan.installmentAmount, loan.currency)}",
                icon = Icons.Filled.Payments
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailRow("Created", formatDate(loan.createdAt))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailRow("Due", formatDate(loan.dueAt), icon = Icons.Filled.CalendarMonth)
            if (loan.paidAt != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                DetailRow("Paid", formatDate(loan.paidAt))
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.padding(end = 8.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ConsequenceCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.padding(end = 12.dp))
            Column {
                Text(
                    "If not paid on time",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun NoteCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Note",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ActionButtons(
    loan: Loan,
    youAreLender: Boolean,
    youAreCreator: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onMarkPaid: () -> Unit,
    onPay: () -> Unit
) {
    when {
        !youAreCreator && loan.status == LoanStatus.PENDING -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Decline") }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(if (loan.kind == LoanKind.REQUEST) "Accept & lend" else "Accept loan")
                }
            }
        }
        youAreCreator && loan.status == LoanStatus.PENDING -> {
            Text(
                "Waiting for ${loan.recipientEmail} to accept your " +
                    (if (loan.kind == LoanKind.REQUEST) "request." else "offer."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        youAreLender && loan.status == LoanStatus.ACTIVE -> {
            Button(
                onClick = onMarkPaid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) { Text("Mark as paid") }
        }
        !youAreLender && loan.status == LoanStatus.ACTIVE && loan.pendingPayment == null -> {
            Button(
                onClick = onPay,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) { Text("Pay loan") }
        }
        else -> { /* nothing to do */ }
    }
}
