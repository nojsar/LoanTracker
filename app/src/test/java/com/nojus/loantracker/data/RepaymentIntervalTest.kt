package com.nojus.loantracker.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RepaymentIntervalTest {

    private val now = 1_750_000_000_000L
    private fun daysFromNow(days: Int): Long = now + days * 24L * 60 * 60 * 1000

    @Test
    fun noneIsAlwaysSinglePayment() {
        assertEquals(1, installmentCountFor(RepaymentInterval.NONE, now, daysFromNow(90)))
    }

    @Test
    fun weeklyOverThirtyDaysIsFivePayments() {
        assertEquals(5, installmentCountFor(RepaymentInterval.WEEKLY, now, daysFromNow(30)))
    }

    @Test
    fun weeklyWithinOneWeekIsSinglePayment() {
        assertEquals(1, installmentCountFor(RepaymentInterval.WEEKLY, now, daysFromNow(7)))
        assertEquals(1, installmentCountFor(RepaymentInterval.WEEKLY, now, daysFromNow(3)))
    }

    @Test
    fun biweeklyOverThirtyDaysIsThreePayments() {
        assertEquals(3, installmentCountFor(RepaymentInterval.BIWEEKLY, now, daysFromNow(30)))
    }

    @Test
    fun monthlyOverNinetyDaysIsThreePayments() {
        assertEquals(3, installmentCountFor(RepaymentInterval.MONTHLY, now, daysFromNow(90)))
    }

    @Test
    fun pastDueDateFallsBackToSinglePayment() {
        assertEquals(1, installmentCountFor(RepaymentInterval.WEEKLY, now, daysFromNow(-5)))
    }

    @Test
    fun installmentAmountSplitsTotalDueEvenly() {
        val loan = Loan(
            principal = 100.0,
            interestMultiplier = 1.10,
            repaymentInterval = RepaymentInterval.WEEKLY,
            installmentCount = 5
        )
        assertEquals(22.0, loan.installmentAmount, 1e-9)
    }

    @Test
    fun installmentAmountDefaultsToTotalDueForLegacyLoans() {
        val loan = Loan(principal = 50.0, installmentCount = 0)
        assertEquals(50.0, loan.installmentAmount, 1e-9)
    }

    @Test
    fun scheduleEndsOnDueDateAndIsSpacedByInterval() {
        val due = daysFromNow(30)
        val loan = Loan(
            principal = 100.0,
            dueAt = due,
            repaymentInterval = RepaymentInterval.WEEKLY,
            installmentCount = 5
        )
        val schedule = loan.paymentSchedule()

        assertEquals(5, schedule.size)
        assertEquals(due, schedule.last().dueAt)
        schedule.zipWithNext().forEach { (a, b) ->
            assertEquals(7L * 24 * 60 * 60 * 1000, b.dueAt - a.dueAt)
        }
    }

    @Test
    fun scheduleAmountsSumToTotalDueDespiteRounding() {
        // 100 / 3 doesn't divide evenly in cents; last payment takes the remainder.
        val loan = Loan(
            principal = 100.0,
            dueAt = daysFromNow(30),
            repaymentInterval = RepaymentInterval.BIWEEKLY,
            installmentCount = 3
        )
        val schedule = loan.paymentSchedule()

        assertEquals(33.33, schedule[0].amount, 1e-9)
        assertEquals(33.33, schedule[1].amount, 1e-9)
        assertEquals(loan.totalDue, schedule.sumOf { it.amount }, 1e-9)
    }

    @Test
    fun oneTimeLoanHasSingleScheduledPayment() {
        val due = daysFromNow(14)
        val loan = Loan(principal = 80.0, dueAt = due)
        val schedule = loan.paymentSchedule()

        assertEquals(1, schedule.size)
        assertEquals(due, schedule.single().dueAt)
        assertEquals(80.0, schedule.single().amount, 1e-9)
    }
}
