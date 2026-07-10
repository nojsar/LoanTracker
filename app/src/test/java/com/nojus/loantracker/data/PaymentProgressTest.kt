package com.nojus.loantracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentProgressTest {

    private val now = 1_750_000_000_000L
    private fun daysFromNow(days: Int): Long = now + days * 24L * 60 * 60 * 1000

    private fun confirmed(amount: Double) = LoanPayment(
        id = "p-$amount", amount = amount, requestedAt = now, status = PaymentStatus.CONFIRMED
    )

    // --- schedule anchored on a chosen first-payment day ---

    @Test
    fun installmentCountBetweenCountsFirstPaymentDay() {
        // Payments on day 0, 7, 14, 21, 28.
        assertEquals(5, installmentCountBetween(RepaymentInterval.WEEKLY, now, daysFromNow(28)))
        // Due date between steps: payments on day 0, 7, 14 only.
        assertEquals(3, installmentCountBetween(RepaymentInterval.WEEKLY, now, daysFromNow(18)))
        assertEquals(1, installmentCountBetween(RepaymentInterval.WEEKLY, now, now))
        assertEquals(1, installmentCountBetween(RepaymentInterval.WEEKLY, daysFromNow(5), now))
        assertEquals(1, installmentCountBetween(RepaymentInterval.NONE, now, daysFromNow(28)))
    }

    @Test
    fun scheduleStartsOnChosenDayAndStaysBeforeDueDate() {
        val start = daysFromNow(3)
        val due = daysFromNow(30)
        val loan = Loan(
            principal = 100.0,
            dueAt = due,
            repaymentInterval = RepaymentInterval.WEEKLY,
            repaymentStartAt = start,
            installmentCount = installmentCountBetween(RepaymentInterval.WEEKLY, start, due),
            status = LoanStatus.ACTIVE
        )
        val schedule = loan.paymentSchedule()

        assertEquals(4, schedule.size)
        assertEquals(start, schedule.first().dueAt)
        schedule.zipWithNext().forEach { (a, b) ->
            assertEquals(7L * 24 * 60 * 60 * 1000, b.dueAt - a.dueAt)
        }
        assertTrue(schedule.last().dueAt <= due)
        assertEquals(loan.totalDue, schedule.sumOf { it.amount }, 1e-9)
    }

    @Test
    fun legacyLoansWithoutStartDateStillEndOnDueDate() {
        val due = daysFromNow(30)
        val loan = Loan(
            principal = 100.0,
            dueAt = due,
            repaymentInterval = RepaymentInterval.WEEKLY,
            installmentCount = 5
        )
        assertEquals(due, loan.paymentSchedule().last().dueAt)
    }

    // --- confirmed payments pour into installments in order ---

    @Test
    fun overpayRollsIntoTheNextInstallment() {
        val loan = Loan(
            principal = 100.0,
            dueAt = daysFromNow(30),
            repaymentInterval = RepaymentInterval.BIWEEKLY,
            installmentCount = 3,
            status = LoanStatus.ACTIVE,
            payments = listOf(confirmed(40.0))
        )
        val schedule = loan.paymentSchedule()

        // 33.33 + 33.33 + 33.34; paying 40 settles #1 and covers 6.67 of #2.
        assertTrue(schedule[0].settled)
        assertFalse(schedule[1].settled)
        assertEquals(6.67, schedule[1].covered, 1e-6)
        assertEquals(26.66, schedule[1].remaining, 1e-6)
        assertEquals(0.0, schedule[2].covered, 1e-6)

        val next = loan.nextPayment()!!
        assertEquals(schedule[1].dueAt, next.dueAt)
        assertEquals(26.66, next.amount, 1e-6)
        assertTrue(next.isInstallment)
    }

    @Test
    fun requestedAndDeclinedPaymentsDoNotCount() {
        val loan = Loan(
            principal = 100.0,
            status = LoanStatus.ACTIVE,
            payments = listOf(
                LoanPayment(id = "a", amount = 30.0, status = PaymentStatus.REQUESTED),
                LoanPayment(id = "b", amount = 20.0, status = PaymentStatus.DECLINED),
                confirmed(10.0)
            )
        )
        assertEquals(10.0, loan.paidSoFar, 1e-9)
        assertEquals(90.0, loan.remainingDue, 1e-9)
        assertEquals("a", loan.pendingPayment?.id)
    }

    // --- next payment ---

    @Test
    fun oneTimeLoanNextPaymentIsTheRemainingBalance() {
        val due = daysFromNow(14)
        val loan = Loan(
            principal = 80.0,
            dueAt = due,
            status = LoanStatus.ACTIVE,
            payments = listOf(confirmed(30.0))
        )
        val next = loan.nextPayment()!!
        assertEquals(due, next.dueAt)
        assertEquals(50.0, next.amount, 1e-9)
        assertFalse(next.isInstallment)
    }

    @Test
    fun fullyCoveredOrInactiveLoansHaveNoNextPayment() {
        val paidUp = Loan(
            principal = 50.0,
            status = LoanStatus.ACTIVE,
            payments = listOf(confirmed(50.0))
        )
        assertNull(paidUp.nextPayment())
        assertNull(Loan(principal = 50.0, status = LoanStatus.PENDING).nextPayment())
        assertNull(Loan(principal = 50.0, status = LoanStatus.PAID).nextPayment())
    }
}
