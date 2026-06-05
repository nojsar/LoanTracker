package com.nojus.loantracker.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nojus.loantracker.data.SavedContact
import com.nojus.loantracker.ui.theme.LoanTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateLoanScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun borrowerEmailKeepsFocusWhenContactSuggestionsOpen() {
        val contactEmail = "nojadrakonis@gmail.com"

        composeRule.setContent {
            var borrowerEmail by remember { mutableStateOf("") }
            var borrowerName by remember { mutableStateOf("") }
            var dropdownOpen by remember { mutableStateOf(false) }

            LoanTrackerTheme {
                BorrowerInputFields(
                    borrowerEmail = borrowerEmail,
                    onBorrowerEmailChange = { borrowerEmail = it },
                    borrowerName = borrowerName,
                    onBorrowerNameChange = { borrowerName = it },
                    contacts = listOf(SavedContact(email = contactEmail, displayName = "Nojus")),
                    contactDropdownOpen = dropdownOpen,
                    onContactDropdownOpenChange = { dropdownOpen = it },
                    onContactSelected = {
                        borrowerEmail = it.email
                        borrowerName = it.displayName
                        dropdownOpen = false
                    }
                )
            }
        }

        composeRule.onNodeWithTag(CreateLoanTestTags.BorrowerEmailField).performClick()

        composeRule.onNodeWithText(contactEmail).assertIsDisplayed()
        composeRule.onNodeWithTag(CreateLoanTestTags.BorrowerEmailField).assertIsFocused()
    }
}
