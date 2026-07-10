package com.nojus.loantracker.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.outlined.Handshake
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseUser
import com.nojus.loantracker.data.Loan
import com.nojus.loantracker.data.LoanKind
import com.nojus.loantracker.data.LoanStatus
import com.nojus.loantracker.data.RepaymentInterval
import com.nojus.loantracker.ui.AuthViewModel
import com.nojus.loantracker.ui.DueUrgency
import com.nojus.loantracker.ui.LoanLists
import com.nojus.loantracker.ui.LoanViewModel
import com.nojus.loantracker.ui.NextDeadline
import com.nojus.loantracker.ui.dueUrgency
import com.nojus.loantracker.ui.formatDate
import com.nojus.loantracker.ui.formatMoney
import com.nojus.loantracker.ui.humanizeUntil
import com.nojus.loantracker.ui.theme.LedgerSerif

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
    val appVersionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
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
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "Loan Tracker",
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = LedgerSerif
                            )
                            if (appVersionName.isNotBlank()) {
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = "v$appVersionName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
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

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SegmentedButton(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    label = { Text("Received (${lists.receivedCount})") }
                )
                SegmentedButton(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    label = { Text("Sent (${lists.sentCount})") }
                )
                SegmentedButton(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    label = { Text("Active (${lists.activeCount})") }
                )
            }

            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(240)) { direction * it / 10 } + fadeIn(tween(240))) togetherWith
                        (slideOutHorizontally(tween(160)) { -direction * it / 10 } + fadeOut(tween(120)))
                },
                label = "loan-lists"
            ) { selected ->
                if (selected == 2) {
                    // Accepted loans from both sides, whoever sent them.
                    if (lists.activeCount == 0) {
                        EmptyState(
                            icon = Icons.Outlined.Handshake,
                            title = "No active loans",
                            message = "Loans show up here once both\nsides have agreed to them."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            section(
                                title = "You lent",
                                loans = lists.activeLent,
                                myEmail = user.email.orEmpty(),
                                onOpen = onOpenLoan
                            )
                            section(
                                title = "You borrowed",
                                loans = lists.activeBorrowed,
                                myEmail = user.email.orEmpty(),
                                onOpen = onOpenLoan
                            )
                        }
                    }
                } else {
                    val pending = if (selected == 0) lists.receivedPending else lists.sentPending
                    val other = if (selected == 0) lists.receivedOther else lists.sentOther
                    val sentTab = selected == 1

                    if (pending.isEmpty() && other.isEmpty()) {
                        EmptyState(
                            icon = if (sentTab) Icons.Filled.ArrowOutward else Icons.Outlined.Handshake,
                            title = if (sentTab) "Nothing sent yet" else "Nothing received yet",
                            message = if (sentTab)
                                "Offer a loan or request one with \"New loan\" —\nyou'll both see the same terms."
                            else
                                "Loan offers and requests sent to\nyour email will show up here."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            section(
                                title = if (sentTab) "Awaiting acceptance" else "Needs your response",
                                loans = pending,
                                myEmail = user.email.orEmpty(),
                                onOpen = onOpenLoan
                            )
                            section(
                                title = "Active & history",
                                loans = other,
                                myEmail = user.email.orEmpty(),
                                onOpen = onOpenLoan
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    loans: List<Loan>,
    myEmail: String,
    onOpen: (String) -> Unit
) {
    if (loans.isEmpty()) return
    item(key = "h-$title") { SectionHeader(title) }
    items(loans, key = { it.id }) { loan ->
        LoanRow(
            loan = loan,
            // Money direction, not tab: a request you sent is still money coming in.
            youAreLender = loan.lenderEmail.equals(myEmail, ignoreCase = true),
            onClick = { onOpen(loan.id) },
            modifier = Modifier.animateItem()
        )
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
                accent = SummaryAccent.Attention,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private enum class SummaryAccent { Positive, Attention }

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
            bg = MaterialTheme.colorScheme.primaryContainer
            fg = MaterialTheme.colorScheme.onPrimaryContainer
        }
        SummaryAccent.Attention -> {
            bg = MaterialTheme.colorScheme.tertiaryContainer
            fg = MaterialTheme.colorScheme.onTertiaryContainer
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
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = fg
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
                    text = "NEXT PAYMENT",
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
                val urgency = dueUrgency(next.dueAt)
                Text(
                    text = "${formatDate(next.dueAt)} · ${humanizeUntil(next.dueAt)}" +
                        if (next.count > 1) " · ${next.count} loans" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (urgency == DueUrgency.Overdue) MaterialTheme.colorScheme.error
                            else fg.copy(alpha = 0.85f),
                    fontWeight = if (urgency != DueUrgency.Normal) FontWeight.SemiBold else null
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LoanRow(
    loan: Loan,
    youAreLender: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CounterpartyAvatar(loan = loan, youAreLender = youAreLender)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (youAreLender) "To ${loan.borrowerEmail}" else "From ${loan.lenderEmail}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                // Active installment loans count down to the next payment step,
                // not to the far-away final deadline.
                val next = loan.nextPayment()
                val stepDate = if (next != null && next.isInstallment) next.dueAt else loan.dueAt
                val stepLabel = if (next != null && next.isInstallment)
                    "Next ${formatMoney(next.amount, loan.currency)} · ${formatDate(stepDate)} · ${humanizeUntil(stepDate)}"
                else
                    "Due ${formatDate(stepDate)} · ${humanizeUntil(stepDate)}"
                val urgency = dueUrgency(stepDate)
                val deadlineMatters = loan.status == LoanStatus.ACTIVE || loan.status == LoanStatus.PENDING
                Text(
                    text = stepLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (deadlineMatters && urgency != DueUrgency.Normal)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (deadlineMatters && urgency != DueUrgency.Normal)
                        FontWeight.SemiBold
                    else null
                )
                Spacer(Modifier.height(6.dp))
                StatusChip(loan.status, loan.kind)
            }
            Spacer(Modifier.size(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                val showRemaining = loan.status == LoanStatus.ACTIVE && loan.paidSoFar > 0
                Text(
                    text = formatMoney(
                        if (showRemaining) loan.remainingDue else loan.totalDue,
                        loan.currency
                    ),
                    style = MaterialTheme.typography.headlineSmall
                )
                if (showRemaining) {
                    Text(
                        text = "of ${formatMoney(loan.totalDue, loan.currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (loan.repaymentInterval != RepaymentInterval.NONE) {
                    Text(
                        text = "${loan.installmentCount} × ${formatMoney(loan.installmentAmount, loan.currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (loan.interestMultiplier != 1.0) {
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
private fun CounterpartyAvatar(loan: Loan, youAreLender: Boolean) {
    val counterpartyName = if (youAreLender) {
        loan.borrowerName.ifBlank { loan.borrowerEmail }
    } else {
        loan.lenderName.ifBlank { loan.lenderEmail }
    }
    val initial = counterpartyName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val bg = if (youAreLender) MaterialTheme.colorScheme.primaryContainer
             else MaterialTheme.colorScheme.tertiaryContainer
    val fg = if (youAreLender) MaterialTheme.colorScheme.onPrimaryContainer
             else MaterialTheme.colorScheme.onTertiaryContainer
    Box(modifier = Modifier.size(44.dp)) {
        Surface(
            modifier = Modifier.size(44.dp).clip(CircleShape),
            color = bg
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = LedgerSerif,
                    color = fg
                )
            }
        }
        Surface(
            modifier = Modifier.size(18.dp).align(Alignment.BottomEnd),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (youAreLender) Icons.Filled.ArrowOutward
                                  else Icons.AutoMirrored.Filled.CallReceived,
                    contentDescription = if (youAreLender) "Lent" else "Borrowed",
                    tint = fg,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

@Composable
fun StatusChip(status: LoanStatus, kind: LoanKind? = null) {
    val pendingLabel = when (kind) {
        LoanKind.OFFER -> "Offer"
        LoanKind.REQUEST -> "Request"
        null -> "Pending"
    }
    val (label, bg, fg) = when (status) {
        LoanStatus.PENDING -> Triple(pendingLabel, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        LoanStatus.ACTIVE -> Triple("Active", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        LoanStatus.PAID -> Triple("Paid", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        LoanStatus.OVERDUE -> Triple("Overdue", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        LoanStatus.DECLINED -> Triple("Declined", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        LoanStatus.DELETED -> Triple("Deleted", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(fg.copy(alpha = 0.8f))
        )
        Spacer(Modifier.size(6.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}
