package com.nojus.loantracker.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class LoanRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val loans get() = firestore.collection("loans")
    private fun contactsFor(uid: String) =
        firestore.collection("users").document(uid).collection("contacts")

    /** Loans where the given email is lender or borrower. */
    fun loansForUser(uid: String, email: String): Flow<List<Loan>> = callbackFlow {
        val normalized = email.lowercase()
        if (normalized.isBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        // No server-side orderBy → avoids needing a composite index. Sort happens in the VM.
        val registration = loans
            .whereArrayContains("participants", normalized)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "loansForUser listener failed", err)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject(Loan::class.java) }.orEmpty()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    fun loanById(id: String): Flow<Loan?> = callbackFlow {
        val reg = loans.document(id).addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e(TAG, "loanById listener failed", err)
                close(err)
                return@addSnapshotListener
            }
            trySend(snap?.toObject(Loan::class.java))
        }
        awaitClose { reg.remove() }
    }

    suspend fun createLoan(loan: Loan): String {
        val ref = loans.document()
        val participants = listOf(loan.lenderEmail.lowercase(), loan.borrowerEmail.lowercase())
        val toSave = loan.copy(id = ref.id, participants = participants)
        ref.set(toSave).await()
        val creatorUid = if (loan.kind == LoanKind.REQUEST) loan.borrowerUid else loan.lenderUid
        val counterpartyEmail = if (loan.kind == LoanKind.REQUEST) loan.lenderEmail else loan.borrowerEmail
        val counterpartyName = if (loan.kind == LoanKind.REQUEST) loan.lenderName else loan.borrowerName
        // Best-effort; don't fail loan creation if contact write is denied.
        runCatching { rememberContact(creatorUid, counterpartyEmail, counterpartyName) }
            .onFailure { Log.w(TAG, "rememberContact failed", it) }
        return ref.id
    }

    /**
     * Accepting fills in the acceptor's identity: the borrower for offers,
     * the lender for requests.
     */
    suspend fun acceptLoan(loanId: String, kind: LoanKind, acceptorUid: String, acceptorName: String) {
        val identity = if (kind == LoanKind.REQUEST) {
            mapOf("lenderUid" to acceptorUid, "lenderName" to acceptorName)
        } else {
            mapOf("borrowerUid" to acceptorUid, "borrowerName" to acceptorName)
        }
        loans.document(loanId).update(
            identity + mapOf(
                "status" to LoanStatus.ACTIVE.name,
                "acceptedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    suspend fun declineLoan(loanId: String) {
        loans.document(loanId).update("status", LoanStatus.DECLINED.name).await()
    }

    suspend fun markPaid(loanId: String) {
        loans.document(loanId).update(
            mapOf(
                "status" to LoanStatus.PAID.name,
                "paidAt" to System.currentTimeMillis()
            )
        ).await()
    }

    /**
     * Borrower asks the lender to confirm a partial payment. Only one request can be
     * open at a time so the two sides never argue about which one was answered.
     */
    suspend fun requestPayment(loanId: String, amount: Double) {
        val ref = loans.document(loanId)
        firestore.runTransaction { tx ->
            val loan = tx.get(ref).toObject(Loan::class.java)
                ?: throw IllegalStateException("Loan not found")
            check(loan.status == LoanStatus.ACTIVE) { "Loan is not active" }
            check(loan.pendingPayment == null) { "A payment request is already waiting" }
            val payment = LoanPayment(
                id = loans.document().id,
                amount = amount,
                requestedAt = System.currentTimeMillis(),
                status = PaymentStatus.REQUESTED
            )
            tx.update(ref, "payments", loan.payments + payment)
        }.await()
    }

    /** Borrower withdraws their own unanswered payment request. */
    suspend fun cancelPaymentRequest(loanId: String, paymentId: String) {
        val ref = loans.document(loanId)
        firestore.runTransaction { tx ->
            val loan = tx.get(ref).toObject(Loan::class.java)
                ?: throw IllegalStateException("Loan not found")
            tx.update(ref, "payments", loan.payments.filterNot {
                it.id == paymentId && it.status == PaymentStatus.REQUESTED
            })
        }.await()
    }

    /**
     * Lender confirms or declines a payment request. Confirming enough to cover the
     * whole balance settles the loan as PAID in the same transaction.
     */
    suspend fun respondToPayment(loanId: String, paymentId: String, confirm: Boolean) {
        val ref = loans.document(loanId)
        firestore.runTransaction { tx ->
            val loan = tx.get(ref).toObject(Loan::class.java)
                ?: throw IllegalStateException("Loan not found")
            val now = System.currentTimeMillis()
            val updatedPayments = loan.payments.map {
                if (it.id == paymentId && it.status == PaymentStatus.REQUESTED) {
                    it.copy(
                        status = if (confirm) PaymentStatus.CONFIRMED else PaymentStatus.DECLINED,
                        respondedAt = now
                    )
                } else it
            }
            val updates = mutableMapOf<String, Any>("payments" to updatedPayments)
            val paid = updatedPayments
                .filter { it.status == PaymentStatus.CONFIRMED }
                .sumOf { it.amount }
            if (confirm && paid >= loan.totalDue - CENT_EPSILON) {
                updates["status"] = LoanStatus.PAID.name
                updates["paidAt"] = now
            }
            tx.update(ref, updates)
        }.await()
    }

    /**
     * Soft-delete: the loan stays in Firestore but is marked DELETED, so it shows up
     * under "Show loan history" with a Deleted tag for both sides.
     */
    suspend fun deleteLoan(loanId: String) {
        loans.document(loanId).update(
            mapOf(
                "status" to LoanStatus.DELETED.name,
                "deletedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    private suspend fun rememberContact(lenderUid: String, email: String, name: String) {
        if (lenderUid.isBlank() || email.isBlank()) return
        val docId = email.lowercase()
        val contact = SavedContact(
            email = docId,
            displayName = name,
            lastUsedAt = System.currentTimeMillis()
        )
        contactsFor(lenderUid).document(docId).set(contact, SetOptions.merge()).await()
    }

    fun contactsForLender(lenderUid: String): Flow<List<SavedContact>> = callbackFlow {
        val reg = contactsFor(lenderUid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "contactsForLender listener failed", err)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(SavedContact::class.java) }
                    .orEmpty()
                    .sortedByDescending { it.lastUsedAt }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    companion object { private const val TAG = "LoanRepository" }
}
