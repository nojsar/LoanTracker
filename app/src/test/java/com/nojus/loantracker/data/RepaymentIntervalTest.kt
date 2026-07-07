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
}
