package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.BottomNavBar
import com.example.ui.components.GuestRestrictionDialog
import com.example.ui.screens.*
import com.example.ui.theme.HisabKhataTheme
import com.example.ui.viewmodel.HisabViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: HisabViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            HisabKhataTheme(themeMode = themeMode) {
                HisabKhataApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun HisabKhataApp(viewModel: HisabViewModel = viewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isGuestMode by viewModel.isGuestMode.collectAsStateWithLifecycle()
    val showGuestRestrictionDialog by viewModel.showGuestRestrictionDialog.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var showQuickAddMenu by remember { mutableStateOf(false) }

    val totalBalance by viewModel.totalBalance.collectAsStateWithLifecycle()
    val totalIncome by viewModel.totalIncome.collectAsStateWithLifecycle()
    val totalExpense by viewModel.totalExpense.collectAsStateWithLifecycle()
    val isBalanceVisible by viewModel.isBalanceVisible.collectAsStateWithLifecycle()

    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val loans by viewModel.loans.collectAsStateWithLifecycle()
    val duePayments by viewModel.duePayments.collectAsStateWithLifecycle()

    val totalLent by viewModel.totalLent.collectAsStateWithLifecycle()
    val totalOwed by viewModel.totalOwed.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    if (showGuestRestrictionDialog) {
        GuestRestrictionDialog(
            onDismissRequest = { viewModel.dismissGuestRestrictionDialog() },
            onLoginClick = {
                viewModel.dismissGuestRestrictionDialog()
                viewModel.exitGuestMode()
                navController.navigate("login")
            },
            onSignupClick = {
                viewModel.dismissGuestRestrictionDialog()
                viewModel.exitGuestMode()
                navController.navigate("signup")
            }
        )
    }

    val handleAddClick = {
        if (isGuestMode) {
            viewModel.triggerGuestRestriction()
        } else {
            showQuickAddMenu = true
        }
    }

    if (showQuickAddMenu) {
        val colors = com.example.ui.theme.LocalAppColors.current
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showQuickAddMenu = false },
            title = {
                androidx.compose.material3.Text(
                    text = "নতুন এন্ট্রি যোগ করুন",
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.Button(
                        onClick = {
                            showQuickAddMenu = false
                            navController.navigate("add_income")
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.IncomeGreen),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        androidx.compose.material3.Text("💚 আয় যোগ করুন", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }

                    androidx.compose.material3.Button(
                        onClick = {
                            showQuickAddMenu = false
                            navController.navigate("add_expense")
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.ExpenseRed),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        androidx.compose.material3.Text("🔴 ব্যয় যোগ করুন", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }

                    androidx.compose.material3.Button(
                        onClick = {
                            showQuickAddMenu = false
                            navController.navigate("loans")
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.PrimaryBlue),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        androidx.compose.material3.Text("👥 ঋণ / ধার হিসাব", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }

                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            showQuickAddMenu = false
                            navController.navigate("accounts")
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        androidx.compose.material3.Text("🏦 অ্যাকাউন্ট যোগ করুন", fontSize = 13.sp, color = colors.textPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }

                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            showQuickAddMenu = false
                            navController.navigate("budget")
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        androidx.compose.material3.Text("📊 নতুন বাজেট সেট করুন", fontSize = 13.sp, color = colors.textPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showQuickAddMenu = false }) {
                    androidx.compose.material3.Text("বাতিল", fontSize = 13.sp, color = colors.textMuted, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            containerColor = colors.dialogBackground,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
        )
    }

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(
                onTimeout = {
                    if (currentUser != null) {
                        navController.navigate("home") {
                            popUpTo("splash") { inclusive = true }
                        }
                    } else {
                        navController.navigate("welcome") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                }
            )
        }

        composable("welcome") {
            WelcomeScreen(
                onStartClick = {
                    navController.navigate("login")
                }
            )
        }

        composable("login") {
            LoginScreen(
                onLoginClick = { email, pass ->
                    viewModel.login(email, pass) {
                        navController.navigate("home") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    }
                },
                onGoogleLoginClick = {
                    viewModel.googleLogin {
                        navController.navigate("home") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    }
                },
                onFacebookLoginClick = {
                    viewModel.facebookLogin {
                        navController.navigate("home") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    }
                },
                onGuestLoginClick = {
                    viewModel.enterGuestMode()
                    navController.navigate("home") {
                        popUpTo("welcome") { inclusive = true }
                    }
                },
                onForgotPasswordClick = {
                    navController.navigate("forgot_password")
                },
                onSignupClick = {
                    navController.navigate("signup")
                },
                isLoading = isLoading
            )
        }

        composable("signup") {
            SignupScreen(
                onSignupClick = { name, email, pass, confirmPass ->
                    viewModel.signup(name, email, pass, confirmPass) {
                        navController.navigate("home") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    }
                },
                onGoogleSignupClick = {
                    viewModel.googleLogin {
                        navController.navigate("home") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    }
                },
                onLoginClick = {
                    navController.navigate("login")
                },
                onGuestClick = {
                    viewModel.enterGuestMode()
                    navController.navigate("home") {
                        popUpTo("welcome") { inclusive = true }
                    }
                },
                isLoading = isLoading
            )
        }

        composable("forgot_password") {
            ForgotPasswordScreen(
                onSendResetClick = { email ->
                    viewModel.sendPasswordReset(email) {
                        navController.navigate("login")
                    }
                },
                onLoginClick = {
                    navController.navigate("login")
                },
                isLoading = isLoading
            )
        }

        // Main Application Flow screens
        composable("home") {
            MainScaffold(
                selectedRoute = "home",
                onNavigate = { route ->
                    if (route != "home") navController.navigate(route)
                },
                onAddClick = handleAddClick
            ) { padding ->
                HomeScreen(
                    totalBalance = totalBalance,
                    totalIncome = totalIncome,
                    totalExpense = totalExpense,
                    isBalanceVisible = isBalanceVisible,
                    onToggleBalanceVisibility = { viewModel.toggleBalanceVisibility() },
                    recentTransactions = transactions,
                    onQuickActionClick = { route ->
                        when (route) {
                            "add_income" -> {
                                if (isGuestMode) viewModel.triggerGuestRestriction() else navController.navigate("add_income")
                            }
                            "add_expense" -> {
                                if (isGuestMode) viewModel.triggerGuestRestriction() else navController.navigate("add_expense")
                            }
                            "transactions" -> navController.navigate("transactions")
                            "budget" -> navController.navigate("budget")
                            "accounts" -> navController.navigate("accounts")
                            "reports" -> navController.navigate("reports")
                            "loans" -> navController.navigate("loans")
                            else -> navController.navigate("profile")
                        }
                    },
                    onSeeAllTransactionsClick = { navController.navigate("transactions") },
                    onAddIncomeClick = {
                        if (isGuestMode) viewModel.triggerGuestRestriction() else navController.navigate("add_income")
                    },
                    onAddExpenseClick = {
                        if (isGuestMode) viewModel.triggerGuestRestriction() else navController.navigate("add_expense")
                    },
                    modifier = Modifier.padding(padding)
                )
            }
        }

        composable("transactions") {
            MainScaffold(
                selectedRoute = "transactions",
                onNavigate = { route ->
                    if (route != "transactions") navController.navigate(route)
                },
                onAddClick = handleAddClick
            ) { padding ->
                TransactionsScreen(
                    transactions = transactions,
                    onDeleteTransaction = { id ->
                        if (isGuestMode) viewModel.triggerGuestRestriction() else viewModel.deleteTransaction(id)
                    },
                    onAddClick = {
                        if (isGuestMode) viewModel.triggerGuestRestriction() else navController.navigate("add_income")
                    },
                    modifier = Modifier.padding(padding)
                )
            }
        }

        composable("add_income") {
            AddTransactionScreen(
                initialType = "INCOME",
                onBackClick = { navController.popBackStack() },
                onSaveClick = { type, category, amount, date, time, desc, account ->
                    viewModel.addTransaction(type, category, amount, date, time, desc, account) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable("add_expense") {
            AddTransactionScreen(
                initialType = "EXPENSE",
                onBackClick = { navController.popBackStack() },
                onSaveClick = { type, category, amount, date, time, desc, account ->
                    viewModel.addTransaction(type, category, amount, date, time, desc, account) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable("reports") {
            MainScaffold(
                selectedRoute = "reports",
                onNavigate = { route ->
                    if (route != "reports") navController.navigate(route)
                },
                onAddClick = handleAddClick
            ) { padding ->
                ReportsScreen(
                    transactions = transactions,
                    modifier = Modifier.padding(padding)
                )
            }
        }

        composable("budget") {
            BudgetScreen(
                budgets = budgets,
                transactions = transactions,
                onAddBudget = { category, amount ->
                    viewModel.addBudget(category, amount) {}
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("accounts") {
            AccountsScreen(
                accounts = accounts,
                totalBalance = totalBalance,
                onAddAccount = { name, type, balance ->
                    viewModel.addAccount(name, type, balance) {}
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("loans") {
            LoanDueScreen(
                loans = loans,
                duePayments = duePayments,
                totalLent = totalLent,
                totalOwed = totalOwed,
                onAddLoan = { type, personName, amount, date, note, phone, dueDate, accountName ->
                    viewModel.addLoan(type, personName, amount, date, note, phone, dueDate, accountName) {}
                },
                onAddDuePayment = { loan, amount, method, note, onSuccess ->
                    viewModel.addDuePayment(loan, amount, method, note, onSuccess)
                },
                onToggleStatus = { loan ->
                    viewModel.toggleLoanPaidStatus(loan)
                },
                onDeleteLoan = { id ->
                    viewModel.deleteLoan(id)
                },
                onDeletePayment = { id ->
                    viewModel.deleteDuePayment(id)
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("profile") {
            MainScaffold(
                selectedRoute = "profile",
                onNavigate = { route ->
                    if (route != "profile") navController.navigate(route)
                },
                onAddClick = handleAddClick
            ) { padding ->
                ProfileScreen(
                    user = currentUser,
                    onLogoutClick = {
                        viewModel.logout {
                            navController.navigate("login") {
                                popUpTo("home") { inclusive = true }
                            }
                        }
                    },
                    onLoginClick = {
                        viewModel.exitGuestMode()
                        navController.navigate("login")
                    },
                    onSignupClick = {
                        viewModel.exitGuestMode()
                        navController.navigate("signup")
                    },
                    onRestrictedActionAttempt = {
                        viewModel.triggerGuestRestriction()
                    },
                    currentThemeMode = themeMode,
                    onThemeModeChange = { mode ->
                        viewModel.setThemeMode(mode)
                    },
                    onBackupClick = {
                        viewModel.backupToCloud()
                    },
                    onRestoreClick = {
                        viewModel.restoreFromCloud()
                    },
                    pendingSyncCount = pendingSyncCount,
                    isSyncing = isSyncing,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
fun MainScaffold(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    onAddClick: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        bottomBar = {
            BottomNavBar(
                selectedRoute = selectedRoute,
                onNavigate = onNavigate,
                onAddClick = onAddClick
            )
        },
        content = content
    )
}
