package com.nojus.loantracker.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nojus.loantracker.data.Loan
import com.nojus.loantracker.data.LoanKind
import com.nojus.loantracker.data.LoanRepository
import com.nojus.loantracker.data.LoanStatus
import com.nojus.loantracker.data.RepaymentInterval
import com.nojus.loantracker.data.SavedContact
import com.nojus.loantracker.data.SettingsRepository
import com.nojus.loantracker.data.installmentCountFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NextDeadline(val dueAt: Long, val total: Double, val count: Int)

/**
 * Sent/received is by who initiated the loan (offers you made + requests you
 * made are both "sent"). The money summary is by role instead — an active loan
 * you requested still counts toward what you owe.
 */
data class LoanLists(
    val sentPending: List<Loan> = emptyList(),
    val sentOther: List<Loan> = emptyList(),
    val receivedPending: List<Loan> = emptyList(),
    val receivedOther: List<Loan> = emptyList(),
    val activeLent: List<Loan> = emptyList(),
    val activeBorrowed: List<Loan> = emptyList()
) {
    val sentCount: Int get() = sentPending.size + sentOther.size
    val receivedCount: Int get() = receivedPending.size + receivedOther.size

    val lentPrincipal: Double get() = activeLent.sumOf { it.principal }
    val lentExpectedReturn: Double get() = activeLent.sumOf { it.totalDue }
    val owedPrincipal: Double get() = activeBorrowed.sumOf { it.principal }
    val owedAtDue: Double get() = activeBorrowed.sumOf { it.totalDue }
    val hasSummary: Boolean get() = activeLent.isNotEmpty() || activeBorrowed.isNotEmpty()

    val nextLentDeadline: NextDeadline? get() = nextDeadline(activeLent)
    val nextOwedDeadline: NextDeadline? get() = nextDeadline(activeBorrowed)

    private fun nextDeadline(loans: List<Loan>): NextDeadline? {
        val earliest = loans.minOfOrNull { it.dueAt } ?: return null
        val onThatDate = loans.filter { it.dueAt == earliest }
        return NextDeadline(
            dueAt = earliest,
            total = onThatDate.sumOf { it.totalDue },
            count = onThatDate.size
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoanViewModel(
    private val repo: LoanRepository = LoanRepository(),
    private val settings: SettingsRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val userFlow = MutableStateFlow(auth.currentUser)
    init {
        auth.addAuthStateListener { userFlow.value = it.currentUser }
    }

    val showHistory: StateFlow<Boolean> = settings.showHistory
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setShowHistory(value: Boolean) {
        settings.setShowHistory(value)
    }

    /** Surfaces the most recent Firestore error so the UI can show it. */
    private val _streamError = MutableStateFlow<String?>(null)
    val streamError: StateFlow<String?> = _streamError.asStateFlow()
    fun clearStreamError() { _streamError.value = null }

    val loans: StateFlow<LoanLists> = userFlow.flatMapLatest { user ->
        if (user == null) flowOf(LoanLists())
        else {
            val email = user.email.orEmpty().lowercase()
            combine(
                repo.loansForUser(user.uid, email),
                showHistory
            ) { list, history ->
                _streamError.value = null
                val sorted = list.sortedByDescending { it.createdAt }
                val sent = sorted.filter { it.isCreator(email) }
                // Always hide DECLINED loans from the recipient — that's what
                // "decline" means for them. The creator keeps the record.
                val received = sorted
                    .filter { !it.isCreator(email) }
                    .filter { it.status != LoanStatus.DECLINED }

                // History off: only active/pending. DELETED only ever shows in history.
                val sentVisible = if (history) sent else sent.filter {
                    it.status == LoanStatus.PENDING || it.status == LoanStatus.ACTIVE
                }
                val receivedVisible = if (history) received else received.filter {
                    it.status == LoanStatus.PENDING || it.status == LoanStatus.ACTIVE
                }
                LoanLists(
                    sentPending = sentVisible.filter { it.status == LoanStatus.PENDING },
                    sentOther = sentVisible.filter { it.status != LoanStatus.PENDING },
                    receivedPending = receivedVisible.filter { it.status == LoanStatus.PENDING },
                    receivedOther = receivedVisible.filter { it.status != LoanStatus.PENDING },
                    activeLent = sorted.filter {
                        it.status == LoanStatus.ACTIVE && it.lenderEmail.lowercase() == email
                    },
                    activeBorrowed = sorted.filter {
                        it.status == LoanStatus.ACTIVE && it.borrowerEmail.lowercase() == email
                    }
                )
            }.catch { t ->
                Log.e("LoanVM", "loans flow error", t)
                _streamError.value = t.message ?: "Could not load loans"
                emit(LoanLists())
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoanLists())

    val contacts: StateFlow<List<SavedContact>> = userFlow.flatMapLatest { user ->
        if (user == null) emptyFlow()
        else repo.contactsForLender(user.uid)
            .catch { t ->
                Log.e("LoanVM", "contacts flow error", t)
                emit(emptyList())
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _action = MutableStateFlow<ActionState>(ActionState.Idle)
    val action: StateFlow<ActionState> = _action.asStateFlow()

    sealed interface ActionState {
        data object Idle : ActionState
        data object Working : ActionState
        data class Created(val loanId: String) : ActionState
        data class Error(val message: String) : ActionState
    }

    fun clearAction() { _action.value = ActionState.Idle }

    fun createLoan(
        kind: LoanKind,
        counterpartyEmail: String,
        counterpartyName: String,
        principal: Double,
        interestMultiplier: Double,
        dueAt: Long,
        repaymentInterval: RepaymentInterval,
        defaultConsequence: String,
        note: String
    ) {
        val user = auth.currentUser ?: run {
            _action.value = ActionState.Error("Not signed in"); return
        }
        if (counterpartyEmail.equals(user.email.orEmpty(), ignoreCase = true)) {
            _action.value = ActionState.Error("You can't send a loan to yourself"); return
        }
        val myEmail = user.email.orEmpty().lowercase()
        val myName = user.displayName.orEmpty()
        val otherEmail = counterpartyEmail.trim().lowercase()
        val otherName = counterpartyName.trim()
        val base = Loan(
            kind = kind,
            principal = principal,
            interestMultiplier = interestMultiplier,
            dueAt = dueAt,
            repaymentInterval = repaymentInterval,
            installmentCount = installmentCountFor(
                repaymentInterval, System.currentTimeMillis(), dueAt
            ),
            defaultConsequence = defaultConsequence.trim(),
            note = note.trim(),
            status = LoanStatus.PENDING
        )
        val loan = when (kind) {
            LoanKind.OFFER -> base.copy(
                lenderUid = user.uid,
                lenderEmail = myEmail,
                lenderName = myName,
                borrowerEmail = otherEmail,
                borrowerName = otherName
            )
            LoanKind.REQUEST -> base.copy(
                borrowerUid = user.uid,
                borrowerEmail = myEmail,
                borrowerName = myName,
                lenderEmail = otherEmail,
                lenderName = otherName
            )
        }
        _action.value = ActionState.Working
        viewModelScope.launch {
            runCatching { repo.createLoan(loan) }
                .onSuccess { _action.value = ActionState.Created(it) }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Failed") }
        }
    }

    fun acceptLoan(loan: Loan) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            runCatching {
                repo.acceptLoan(loan.id, loan.kind, user.uid, user.displayName.orEmpty())
            }.onFailure { _action.value = ActionState.Error(it.message ?: "Failed") }
        }
    }

    fun declineLoan(loanId: String) {
        viewModelScope.launch {
            runCatching { repo.declineLoan(loanId) }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Failed") }
        }
    }

    fun markPaid(loanId: String) {
        viewModelScope.launch {
            runCatching { repo.markPaid(loanId) }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Failed") }
        }
    }

    fun deleteLoan(loanId: String) {
        viewModelScope.launch {
            runCatching { repo.deleteLoan(loanId) }
                .onFailure { _action.value = ActionState.Error(it.message ?: "Failed") }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return LoanViewModel(
                        settings = SettingsRepository(context.applicationContext)
                    ) as T
                }
            }
    }
}
