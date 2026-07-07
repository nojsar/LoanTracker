package com.nojus.loantracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nojus.loantracker.data.RepaymentInterval
import com.nojus.loantracker.data.SavedContact
import com.nojus.loantracker.data.installmentCountFor
import com.nojus.loantracker.ui.LoanViewModel
import com.nojus.loantracker.ui.formatDate
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
            BorrowerInputFields(
                borrowerEmail = borrowerEmail,
                onBorrowerEmailChange = {
                    borrowerEmail = it
                    contactDropdownOpen = true
                },
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
                label = { Text("Principal (EUR)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            SectionLabel("One-time interest multiplier")
            OutlinedTextField(
                value = multiplierText,
                onValueChange = { multiplierText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.') },
                label = { Text("e.g. 1.10 means principal × 1.10") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1.0 to "× 1.00", 1.05 to "+5%", 1.10 to "+10%", 1.25 to "+25%").forEach { (v, label) ->
                    AssistChip(onClick = { multiplierText = v.toString() }, label = { Text(label) })
                }
            }
            val total = (amountText.toDoubleOrNull() ?: 0.0) * (multiplierText.toDoubleOrNull() ?: 1.0)
            if (total > 0) {
                Text(
                    "Total due: " + com.nojus.loantracker.ui.formatMoney(total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    AssistChip(onClick = { dueAt = todayPlusDays(d) }, label = { Text("$d d") })
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
            if (repaymentInterval != RepaymentInterval.NONE && total > 0) {
                val installments = installmentCountFor(
                    repaymentInterval, System.currentTimeMillis(), dueAt
                )
                Text(
                    if (installments == 1)
                        "Due within one ${repaymentInterval.per} — a single payment of ${com.nojus.loantracker.ui.formatMoney(total)}"
                    else
                        "$installments payments of ${com.nojus.loantracker.ui.formatMoney(total / installments)} — one every ${repaymentInterval.per} until ${formatDate(dueAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            Button(
                onClick = {
                    viewModel.createLoan(
                        borrowerEmail = borrowerEmail.trim(),
                        borrowerName = borrowerName.trim(),
                        principal = amountText.toDoubleOrNull() ?: 0.0,
                        interestMultiplier = multiplierText.toDoubleOrNull() ?: 1.0,
                        dueAt = dueAt,
                        repaymentInterval = repaymentInterval,
                        defaultConsequence = consequence,
                        note = note
                    )
                },
                enabled = canSubmit && action !is LoanViewModel.ActionState.Working,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (action is LoanViewModel.ActionState.Working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Send loan offer")
                }
            }
            (action as? LoanViewModel.ActionState.Error)?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
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
    modifier: Modifier = Modifier
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
        SectionLabel("Borrower")
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
            label = { Text("Borrower name (optional)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground
    )
}
