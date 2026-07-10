package com.nojus.loantracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nojus.loantracker.ui.screens.CreateLoanScreen
import com.nojus.loantracker.ui.screens.HomeScreen
import com.nojus.loantracker.ui.screens.LoanDetailScreen
import com.nojus.loantracker.ui.screens.SignInScreen

object Routes {
    const val SIGN_IN = "signin"
    const val HOME = "home"
    const val CREATE = "create"
    const val DETAIL = "detail/{loanId}"
    fun detail(loanId: String) = "detail/$loanId"
}

@Composable
fun AppNavigation(
    pendingLoanId: String? = null,
    onPendingLoanConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(context))
    val loanViewModel: LoanViewModel = viewModel(factory = LoanViewModel.factory(context))

    val user by authViewModel.currentUser.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    LaunchedEffect(user, pendingLoanId) {
        if (user == null) {
            navController.navigate(Routes.SIGN_IN) {
                popUpTo(0) { inclusive = true }
            }
        } else {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute == Routes.SIGN_IN || currentRoute == null) {
                navController.navigate(Routes.HOME) {
                    popUpTo(0) { inclusive = true }
                }
            }
            // A tapped notification lands on its loan, on top of Home.
            if (pendingLoanId != null) {
                navController.navigate(Routes.detail(pendingLoanId))
                onPendingLoanConsumed()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (user == null) Routes.SIGN_IN else Routes.HOME
    ) {
        composable(Routes.SIGN_IN) {
            SignInScreen(viewModel = authViewModel)
        }
        composable(Routes.HOME) {
            val u = user
            if (u != null) {
                HomeScreen(
                    user = u,
                    authViewModel = authViewModel,
                    loanViewModel = loanViewModel,
                    onCreateLoan = { navController.navigate(Routes.CREATE) },
                    onOpenLoan = { id -> navController.navigate(Routes.detail(id)) }
                )
            }
        }
        composable(Routes.CREATE) {
            CreateLoanScreen(
                viewModel = loanViewModel,
                onBack = { navController.popBackStack() },
                onCreated = { id ->
                    navController.popBackStack()
                    navController.navigate(Routes.detail(id))
                }
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("loanId") { type = NavType.StringType })
        ) { entry ->
            val loanId = entry.arguments?.getString("loanId").orEmpty()
            val u = user
            if (u != null) {
                LoanDetailScreen(
                    loanId = loanId,
                    user = u,
                    viewModel = loanViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
