package com.nojus.loantracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseUser
import com.nojus.loantracker.data.Loan
import com.nojus.loantracker.data.LoanStatus
import com.nojus.loantracker.ui.AuthViewModel
import com.nojus.loantracker.ui.LoanLists
import com.nojus.loantracker.ui.LoanViewModel
import com.nojus.loantracker.ui.NextDeadline
import com.nojus.loantracker.ui.formatDate
import com.nojus.loantracker.ui.formatMoney
import com.nojus.loantracker.ui.humanizeUntil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    user: FirebaseUser,
    authViewModel: AuthViewModel,
    loanViewModel: LoanViewModel,
    onCreateLoan: () -> Unit,
    onOpenLoan: (String) -> Unit
) {
    val lists by loanViewModel.loans.collectAsStateWithLifecycle()
    val streamError by loanViewModel.streamError.collectAsStateWithLifecycle()
    val showHistory by loanViewModel.showHistory.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(streamError) {
        streamError?.let {
            snackbarHostState.showSnackbar(it)
            loanViewModel.clearStreamError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Loan Tracker", style = MaterialTheme.typography.titleLarge)
                        Text(
                            user.email.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                        DropdownMenu(
                            expanded = settingsOpen,
                            onDismissRequest = { settingsOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Show loan history") },
                                leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                                trailingIcon = {
                                    Switch(
                                        checked = showHistory,
                                        onCheckedChange = { loanViewModel.setShowHistory(it) }
                                    )
                                },
                                onClick = { loanViewModel.setShowHistory(!showHistory) }
                            )
                        }
                    }
                    IconButton(onClick = { authViewModel.signOut(context) }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateLoan,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New loan") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (lists.hasSummary) {
                SummarySection(lists)
            }
            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Sent (${lists.sentCount})") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Received (${lists.receivedCount})") }
                )
            }

            val pending = if (tab == 0) lists.sentPending else lists.receivedPending
            val other = if (tab == 0) lists.sentOther else lists.receivedOther
            val youAreLender = tab == 0

            if (pending.isEmpty() && other.isEmpty()) {
                EmptyState(youAreLender)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    section(
                        title = if (youAreLender) "Awaiting acceptance" else "Pending — needs your response",
                        loans = pending,
                        youAreLender = youAreLender,
                        onOpen = onOpenLoan
                    )
                    section(
                        title = if (youAreLender) "Active & history" else "Active & history",
                        loans = other,
                        youAreLender = youAreLender,
                        onOpen = onOpenLoan
                    )
                }
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    loans: List<Loan>,
    youAreLender: Boolean,
    onOpen: (String) -> Unit
) {
    if (loans.isEmpty()) return
    item(key = "h-$title") { SectionHeader(title) }
    items(loans, key = { it.id }) { loan ->
        LoanRow(loan = loan, youAreLender = youAreLender, onClick = { onOpen(loan.id) })
    }
}

@Composable
private fun SummarySection(lists: LoanLists) {
    val showLent = lists.lentExpectedReturn > 0.0
    val showOwed = lists.owedAtDue > 0.0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showLent) {
            StatCard(
                label = "To receive",
                value = formatMoney(lists.lentExpectedReturn),
                sub = "on ${formatMoney(lists.lentPrincipal)} lent",
                next = lists.nextLentDeadline,
                accent = SummaryAccent.Positive,
                modifier = Modifier.weight(1f)
            )
        }
        if (showOwed) {
            StatCard(
                label = "You owe",
                value = formatMoney(lists.owedAtDue),
                sub = "on ${formatMoney(lists.owedPrincipal)} borrowed",
                next = lists.nextOwedDeadline,
                accent = SummaryAccent.Neutral,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private enum class SummaryAccent { Positive, Neutral }

@Composable
private fun StatCard(
    label: String,
    value: String,
    sub: String,
    next: NextDeadline?,
    accent: SummaryAccent,
    modifier: Modifier = Modifier
) {
    val bg: Color
    val fg: Color
    when (accent) {
        SummaryAccent.Positive -> {
            bg = MaterialTheme.colorScheme.tertiaryContainer
            fg = MaterialTheme.colorScheme.onTertiaryContainer
        }
        SummaryAccent.Neutral -> {
            bg = MaterialTheme.colorScheme.secondaryContainer
            fg = MaterialTheme.colorScheme.onSecondaryContainer
        }
    }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = fg,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = fg.copy(alpha = 0.85f)
            )
            if (next != null) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = fg.copy(alpha = 0.2f))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "NEXT DEADLINE",
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.75f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatMoney(next.total),
                    style = MaterialTheme.typography.titleMedium,
                    color = fg,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${formatDate(next.dueAt)} · ${humanizeUntil(next.dueAt)}" +
                        if (next.count > 1) " · ${next.count} loans" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = fg.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun EmptyState(youAreLender: Boolean) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = if (youAreLender)
                "You haven't sent any loans yet.\nTap \"New loan\" to send one."
            else
                "Nobody has sent you a loan yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun LoanRow(loan: Loan, youAreLender: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DirectionIcon(youAreLender = youAreLender)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (youAreLender) "To ${loan.borrowerEmail}" else "From ${loan.lenderEmail}",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Due ${formatDate(loan.dueAt)} · ${humanizeUntil(loan.dueAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                StatusChip(loan.status)
            }
            Spacer(Modifier.size(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatMoney(loan.totalDue, loan.currency),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (loan.interestMultiplier != 1.0) {
                    Text(
                        text = "× ${"%.2f".format(loan.interestMultiplier)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectionIcon(youAreLender: Boolean) {
    Surface(
        modifier = Modifier.size(40.dp).clip(CircleShape),
        color = if (youAreLender) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (youAreLender) Icons.Filled.ArrowOutward else Icons.AutoMirrored.Filled.CallReceived,
                contentDescription = null,
                tint = if (youAreLender) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
fun StatusChip(status: LoanStatus) {
    val (label, bg, fg) = when (status) {
        LoanStatus.PENDING -> Triple("Pending", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        LoanStatus.ACTIVE -> Triple("Active", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        LoanStatus.PAID -> Triple("Paid", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        LoanStatus.OVERDUE -> Triple("Overdue", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        LoanStatus.DECLINED -> Triple("Declined", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        LoanStatus.DELETED -> Triple("Deleted", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}
