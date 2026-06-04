package com.nojus.loantracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nojus.loantracker.ui.AppNavigation
import com.nojus.loantracker.ui.theme.LoanTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LoanTrackerTheme {
                AppNavigation()
            }
        }
    }
}
