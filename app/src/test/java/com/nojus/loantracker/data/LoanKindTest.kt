package com.nojus.loantracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanKindTest {

    private val offer = Loan(
        kind = LoanKind.OFFER,
        lenderEmail = "lender@example.com",
        borrowerEmail = "borrower@example.com"
    )

    private val request = Loan(
        kind = LoanKind.REQUEST,
        lenderEmail = "lender@example.com",
        borrowerEmail = "borrower@example.com"
    )

    @Test
    fun offerIsCreatedByLenderAndAnsweredByBorrower() {
        assertEquals("lender@example.com", offer.creatorEmail)
        assertEquals("borrower@example.com", offer.recipientEmail)
    }

    @Test
    fun requestIsCreatedByBorrowerAndAnsweredByLender() {
        assertEquals("borrower@example.com", request.creatorEmail)
        assertEquals("lender@example.com", request.recipientEmail)
    }

    @Test
    fun isCreatorIgnoresCase() {
        assertTrue(offer.isCreator("LENDER@example.com"))
        assertFalse(offer.isCreator("borrower@example.com"))
        assertTrue(request.isCreator("Borrower@Example.com"))
    }

    @Test
    fun legacyLoansDefaultToOffer() {
        val legacy = Loan(lenderEmail = "lender@example.com", borrowerEmail = "borrower@example.com")
        assertEquals(LoanKind.OFFER, legacy.kind)
        assertEquals("lender@example.com", legacy.creatorEmail)
    }
}
