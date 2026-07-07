package com.nojus.loantracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nojus.loantracker.data.LoanKind
import com.nojus.loantracker.data.RepaymentInterval
import com.nojus.loantracker.data.SavedContact
import com.nojus.loantracker.data.installmentCountFor
import com.nojus.loantracker.ui.LoanViewModel
import com.nojus.loantracker.ui.formatDate
import com.nojus.loantracker.ui.formatMoney
import com.nojus.loantracker.ui.theme.LedgerSerif
import com.nojus.loantracker.ui.todayPlusDays

object CreateLoanTestTags {
    const val BorrowerEmailField = "create-loan-borrower-email"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateLoanScreen(
    viewModel: LoanViewModel,
    onBack: () -> Unit,
    onCreated: (String) -> Unit
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()

    var kind by remember { mutableStateOf(LoanKind.OFFER) }
    var borrowerEmail by remember { mutableStateOf("") }
    var borrowerName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var multiplierText by remember { mutableStateOf("1.0") }
    var dueAt by remember { mutableStateOf(todayPlusDays(30)) }
    var repaymentInterval by remember { mutableStateOf(RepaymentInterval.NONE) }
    var consequence by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showDate by remember { mutableStateOf(false) }
    var contactDropdownOpen by remember { mutableStateOf(false) }

    val emailInvalid = borrowerEmail.isNotBlank() && !borrowerEmail.contains("@")
    val amountInvalid = amountText.isNotBlank() && (amountText.toDoubleOrNull() ?: 0.0) <= 0.0
    val multiplierInvalid = multiplierText.isNotBlank() && (multiplierText.toDoubleOrNull() ?: 0.0) <= 0.0

    val canSubmit by remember {
        derivedStateOf {
            borrowerEmail.contains("@") &&
                (amountText.toDoubleOrNull() ?: 0.0) > 0.0 &&
                (multiplierText.toDoubleOrNull() ?: 0.0) > 0.0 &&
                dueAt > System.currentTimeMillis()
        }
    }

    LaunchedEffect(action) {
        if (action is LoanViewModel.ActionState.Created) {
            onCreated((action as LoanViewModel.ActionState.Created).loanId)
            viewModel.clearAction()
        }
    }

    val total = (amountText.toDoubleOrNull() ?: 0.0) * (multiplierText.toDoubleOrNull() ?: 1.0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New loan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            SubmitBar(
                kind = kind,
                total = total,
                dueAt = dueAt,
                repaymentInterval = repaymentInterval,
                working = action is LoanViewModel.ActionState.Working,
                enabled = canSubmit && action !is LoanViewModel.ActionState.Working,
                errorMessage = (action as? LoanViewModel.ActionState.Error)?.message,
                onSubmit = {
                    viewModel.createLoan(
                        kind = kind,
                        counterpartyEmail = borrowerEmail.trim(),
                        counterpartyName = borrowerName.trim(),
                        principal = amountText.toDoubleOrNull() ?: 0.0,
                        interestMultiplier = multiplierText.toDoubleOrNull() ?: 1.0,
                        dueAt = dueAt,
                        repaymentInterval = repaymentInterval,
                        defaultConsequence = consequence,
                        note = note
                    )
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = kind == LoanKind.OFFER,
                    onClick = { kind = LoanKind.OFFER },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("Offer a loan") }
                )
                SegmentedButton(
                    selected = kind == LoanKind.REQUEST,
                    onClick = { kind = LoanKind.REQUEST },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("Request a loan") }
                )
            }
            Text(
                if (kind == LoanKind.OFFER)
                    "You lend money — the borrower accepts your terms."
                else
                    "You ask to borrow — the lender accepts your terms.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            BorrowerInputFields(
                borrowerEmail = borrowerEmail,
                onBorrowerEmailChange = {
                    borrowerEmail = it
                    contactDropdownOpen = true
                },
                emailInvalid = emailInvalid,
                sectionLabel = if (kind == LoanKind.OFFER) "Borrower" else "Lender",
                nameLabel = if (kind == LoanKind.OFFER) "Borrower name (optional)" else "Lender name (optional)",
                borrowerName = borrowerName,
                onBorrowerNameChange = { borrowerName = it },
                contacts = contacts,
                contactDropdownOpen = contactDropdownOpen,
                onContactDropdownOpenChange = { contactDropdownOpen = it },
                onContactSelected = { contact ->
                    borrowerEmail = contact.email
                    if (borrowerName.isBlank()) borrowerName = contact.displayName
                    contactDropdownOpen = false
                }
            )

            Spacer(Modifier.height(4.dp))
            SectionLabel("Amount")
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.') },
                label = { Text("Principal") },
                prefix = { Text("€") },
                isError = amountInvalid,
                supportingText = if (amountInvalid) {
                    { Text("Enter an amount above zero") }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            SectionLabel("One-time interest")
            OutlinedTextField(
                value = multiplierText,
                onValueChange = { multiplierText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.') },
                label = { Text("Multiplier — 1.10 means principal × 1.10") },
                isError = multiplierInvalid,
                supportingText = if (multiplierInvalid) {
                    { Text("Multiplier must be above zero") }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1.0 to "None", 1.05 to "+5%", 1.10 to "+10%", 1.25 to "+25%").forEach { (v, label) ->
                    FilterChip(
                        selected = multiplierText.toDoubleOrNull() == v,
                        onClick = { multiplierText = v.toString() },
                        label = { Text(label) }
                    )
                }
            }

            SectionLabel("Due date")
            OutlinedTextField(
                value = formatDate(dueAt),
                onValueChange = {},
                readOnly = true,
                shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                trailingIcon = {
                    TextButton(onClick = { showDate = true }) { Text("Change") }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 14, 30, 60, 90).forEach { d ->
                    FilterChip(
                        selected = dueAt == todayPlusDays(d),
                        onClick = { dueAt = todayPlusDays(d) },
                        label = { Text("$d d") }
                    )
                }
            }

            SectionLabel("Repayment")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepaymentInterval.entries.forEach { interval ->
                    FilterChip(
                        selected = repaymentInterval == interval,
                        onClick = { repaymentInterval = interval },
                        label = { Text(interval.label) }
                    )
                }
            }

            SectionLabel("If not paid by the due date")
            OutlinedTextField(
                value = consequence,
                onValueChange = { consequence = it },
                label = { Text("What happens?") },
                placeholder = { Text("e.g. owe an extra €20, lose access to my Xbox until paid…") },
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            SectionLabel("Note (optional)")
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("What is this loan for?") },
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dueAt)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dueAt = it }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

/**
 * Sticky footer: live loan summary + submit. Keeps the numbers in sight while
 * the form scrolls, so there are no surprises at the moment of sending.
 */
@Composable
private fun SubmitBar(
    kind: LoanKind,
    total: Double,
    dueAt: Long,
    repaymentInterval: RepaymentInterval,
    working: Boolean,
    enabled: Boolean,
    errorMessage: String?,
    onSubmit: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .animateContentSize()
        ) {
            AnimatedVisibility(visible = total > 0) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "TOTAL DUE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatMoney(total),
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = LedgerSerif
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "by ${formatDate(dueAt)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (repaymentInterval != RepaymentInterval.NONE) {
                                val installments = installmentCountFor(
                                    repaymentInterval, System.currentTimeMillis(), dueAt
                                )
                                Text(
                                    if (installments == 1)
                                        "single payment"
                                    else
                                        "$installments × ${formatMoney(total / installments)} per ${repaymentInterval.per}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                }
            }
            if (errorMessage != null) {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Button(
                onClick = onSubmit,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (kind == LoanKind.OFFER) "Send loan offer" else "Send loan request")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowerInputFields(
    borrowerEmail: String,
    onBorrowerEmailChange: (String) -> Unit,
    borrowerName: String,
    onBorrowerNameChange: (String) -> Unit,
    contacts: List<SavedContact>,
    contactDropdownOpen: Boolean,
    onContactDropdownOpenChange: (Boolean) -> Unit,
    onContactSelected: (SavedContact) -> Unit,
    modifier: Modifier = Modifier,
    emailInvalid: Boolean = false,
    sectionLabel: String = "Borrower",
    nameLabel: String = "Borrower name (optional)"
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var emailFieldFocused by remember { mutableStateOf(false) }
    val filtered = contacts.filter {
        borrowerEmail.isBlank() || it.email.contains(borrowerEmail, ignoreCase = true)
    }

    LaunchedEffect(emailFieldFocused) {
        if (emailFieldFocused) {
            keyboardController?.show()
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionLabel(sectionLabel)
        ExposedDropdownMenuBox(
            expanded = contactDropdownOpen && filtered.isNotEmpty(),
            onExpandedChange = { onContactDropdownOpenChange(it) }
        ) {
            OutlinedTextField(
                value = borrowerEmail,
                onValueChange = {
                    onBorrowerEmailChange(it)
                    onContactDropdownOpenChange(true)
                },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                    .onFocusChanged { focusState ->
                        emailFieldFocused = focusState.isFocused
                        if (focusState.isFocused) {
                            onContactDropdownOpenChange(true)
                        } else {
                            onContactDropdownOpenChange(false)
                        }
                    }
                    .testTag(CreateLoanTestTags.BorrowerEmailField)
                    .fillMaxWidth(),
                label = { Text("Email") },
                placeholder = { Text("name@example.com") },
                leadingIcon = { Icon(Icons.Filled.PersonOutline, contentDescription = null) },
                isError = emailInvalid,
                supportingText = if (emailInvalid) {
                    { Text("That doesn't look like an email address") }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Email
                )
            )
            if (filtered.isNotEmpty()) {
                DropdownMenu(
                    expanded = contactDropdownOpen,
                    onDismissRequest = { onContactDropdownOpenChange(false) },
                    properties = PopupProperties(focusable = false)
                ) {
                    filtered.take(6).forEach { contact ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(contact.email)
                                    if (contact.displayName.isNotBlank()) {
                                        Text(
                                            contact.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = { onContactSelected(contact) }
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = borrowerName,
            onValueChange = onBorrowerNameChange,
            label = { Text(nameLabel) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}
