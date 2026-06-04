package com.nojus.loantracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseUser
import com.nojus.loantracker.data.Loan
import com.nojus.loantracker.data.LoanRepository
import com.nojus.loantracker.data.LoanStatus
import com.nojus.loantracker.ui.LoanViewModel
import com.nojus.loantracker.ui.formatDate
import com.nojus.loantracker.ui.formatMoney
import com.nojus.loantracker.ui.humanizeUntil

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
                    val notAlreadyDeleted = current?.status != LoanStatus.DELETED
                    if (isLender && notAlreadyDeleted) {
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
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        val youAreLender = current.lenderEmail.equals(user.email, ignoreCase = true)
        val youAreBorrower = current.borrowerEmail.equals(user.email, ignoreCase = true)

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AmountCard(current)

            DetailsCard(current, youAreLender)

            if (current.defaultConsequence.isNotBlank()) {
                ConsequenceCard(current.defaultConsequence)
            }

            if (current.note.isNotBlank()) {
                NoteCard(current.note)
            }

            ActionButtons(
                loan = current,
                youAreLender = youAreLender,
                youAreBorrower = youAreBorrower,
                onAccept = { viewModel.acceptLoan(current.id) },
                onDecline = { viewModel.declineLoan(current.id) },
                onMarkPaid = { viewModel.markPaid(current.id) }
            )

            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete) {
        val current = loan
        val explanation = when (current?.status) {
            LoanStatus.PENDING -> "The offer will be withdrawn. It moves to history with a Deleted tag — turn on \"Show loan history\" to find it."
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

@Composable
private fun AmountCard(loan: Loan) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Total due",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(6.dp))
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
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(8.dp))
            StatusChip(loan.status)
            Spacer(Modifier.height(8.dp))
            Text(
                "Due ${formatDate(loan.dueAt)} · ${humanizeUntil(loan.dueAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun DetailsCard(loan: Loan, youAreLender: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            DetailRow("Lender", if (youAreLender) "You" else loan.lenderEmail)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailRow("Borrower", if (!youAreLender) "You" else loan.borrowerEmail)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailRow("Principal", formatMoney(loan.principal, loan.currency))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DetailRow(
                "Interest multiplier",
                if (loan.interestMultiplier == 1.0) "None" else "× ${"%.2f".format(loan.interestMultiplier)}",
                icon = Icons.Filled.Bolt
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
    youAreBorrower: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onMarkPaid: () -> Unit
) {
    when {
        youAreBorrower && loan.status == LoanStatus.PENDING -> {
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
                ) { Text("Accept loan") }
            }
        }
        youAreLender && loan.status == LoanStatus.PENDING -> {
            Text(
                "Waiting for ${loan.borrowerEmail} to accept.",
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
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) { Text("Mark as paid") }
        }
        else -> { /* nothing to do */ }
    }
}
