package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.HisabActionEngine
import com.example.data.ai.HisabInsightEngine
import com.example.data.ai.InsightPriority
import com.example.data.ai.SmartInsight
import com.example.data.ai.StructuredHisabAction
import com.example.data.auth.AuthManager
import com.example.data.auth.User
import com.example.data.local.AccountEntity
import com.example.data.local.AppDatabase
import com.example.data.local.BudgetEntity
import com.example.data.local.LoanEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.SavingGoalEntity
import com.example.data.local.TransactionEntity
import com.example.data.repository.HisabRepository
import com.example.data.local.ThemePreferences
import com.example.utils.NetworkMonitor
import com.example.utils.ReminderScheduler
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class HisabViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = HisabRepository(db.hisabDao())
    val authManager = AuthManager(application)
    val networkMonitor = NetworkMonitor(application)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val themePreferences = ThemePreferences(application)

    private val _themeMode = MutableStateFlow(themePreferences.getThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        themePreferences.setThemeMode(mode)
    }

    val currentUser: StateFlow<User?> = authManager.currentUser

    private val _isGuestMode = MutableStateFlow(false)
    val isGuestMode: StateFlow<Boolean> = _isGuestMode.asStateFlow()

    private val _showGuestRestrictionDialog = MutableStateFlow(false)
    val showGuestRestrictionDialog: StateFlow<Boolean> = _showGuestRestrictionDialog.asStateFlow()

    fun enterGuestMode() {
        _isGuestMode.value = true
        _showGuestRestrictionDialog.value = false
    }

    fun exitGuestMode() {
        _isGuestMode.value = false
        _showGuestRestrictionDialog.value = false
    }

    fun triggerGuestRestriction() {
        _showGuestRestrictionDialog.value = true
    }

    fun dismissGuestRestrictionDialog() {
        _showGuestRestrictionDialog.value = false
    }

    private val _isBalanceVisible = MutableStateFlow(true)
    val isBalanceVisible: StateFlow<Boolean> = _isBalanceVisible.asStateFlow()

    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent: SharedFlow<String> = _uiEvent.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<TransactionEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getTransactions(user.uid)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val accounts: StateFlow<List<AccountEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getAccounts(user.uid)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val budgets: StateFlow<List<BudgetEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getBudgets(user.uid)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val loans: StateFlow<List<LoanEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getLoans(user.uid)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val duePayments: StateFlow<List<com.example.data.local.DuePaymentEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getDuePayments(user.uid)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val savingGoals: StateFlow<List<SavingGoalEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getSavingGoals(user.uid)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val reminders: StateFlow<List<ReminderEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getReminders(user.uid)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingSyncCount: StateFlow<Int> = currentUser
        .flatMapLatest { user ->
            if (user != null && !_isGuestMode.value) repository.observePendingSyncCount(user.uid)
            else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                if (isOnline) {
                    syncPendingQueueAutomatically()
                }
            }
        }
    }

    fun syncPendingQueueAutomatically() {
        val user = currentUser.value
        if (_isGuestMode.value || user == null) return
        if (!networkMonitor.isCurrentlyOnline()) return

        viewModelScope.launch {
            if (_isSyncing.value) return@launch
            _isSyncing.value = true
            try {
                repository.syncPendingQueue(user.uid)
            } catch (e: Exception) {
                // Keep pending in local queue safely
            } finally {
                _isSyncing.value = false
            }
        }
    }

    // Calculated stats
    val totalIncome: StateFlow<Double> = transactions
        .map { list -> list.filter { it.type == "INCOME" }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExpense: StateFlow<Double> = transactions
        .map { list -> list.filter { it.type == "EXPENSE" }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalBalance: StateFlow<Double> = transactions
        .map { list ->
            val income = list.filter { it.type == "INCOME" }.sumOf { it.amount }
            val expense = list.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            income - expense
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalLent: StateFlow<Double> = loans
        .map { list -> list.filter { (it.type == "LENT" || it.type == "RECEIVABLE") && !it.isPaid }.sumOf { it.currentBalance } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalOwed: StateFlow<Double> = loans
        .map { list -> list.filter { (it.type == "OWED" || it.type == "PAYABLE") && !it.isPaid }.sumOf { it.currentBalance } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    @Suppress("UNCHECKED_CAST")
    val smartInsights: StateFlow<List<SmartInsight>> = combine(
        listOf(transactions, accounts, loans, budgets, savingGoals, reminders, currentUser)
    ) { args ->
        val tx = args[0] as List<TransactionEntity>
        val acc = args[1] as List<AccountEntity>
        val ln = args[2] as List<LoanEntity>
        val bg = args[3] as List<BudgetEntity>
        val sg = args[4] as List<SavingGoalEntity>
        val rem = args[5] as List<ReminderEntity>
        val user = args[6] as? com.example.data.auth.User
        HisabInsightEngine.generateInsights(
            transactions = tx,
            accounts = acc,
            loans = ln,
            budgets = bg,
            savingGoals = sg,
            reminders = rem,
            activeUserId = user?.uid
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val notifiedInsightKeys = mutableSetOf<String>()

    fun checkAndNotifyProactiveInsights() {
        val user = currentUser.value ?: return
        val currentInsights = smartInsights.value
        val criticalOrHigh = currentInsights.filter { it.priority == InsightPriority.CRITICAL || it.priority == InsightPriority.HIGH }
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        for (insight in criticalOrHigh) {
            val key = "${user.uid}_${insight.type}_${insight.category ?: ""}_$dateStr"
            if (!notifiedInsightKeys.contains(key)) {
                notifiedInsightKeys.add(key)
                ReminderScheduler.scheduleReminder(
                    context = getApplication(),
                    reminderId = "INSIGHT_${insight.id.hashCode()}",
                    title = insight.title,
                    message = insight.messageBangla,
                    triggerTimeMillis = System.currentTimeMillis() + 1500L
                )
            }
        }
    }

    fun toggleBalanceVisibility() {
        _isBalanceVisible.value = !_isBalanceVisible.value
    }

    // Auth actions
    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = authManager.loginWithEmail(email, pass)
            _isLoading.value = false
            res.onSuccess {
                exitGuestMode()
                _uiEvent.emit("সফলভাবে লগইন করা হয়েছে")
                // Automatic restore on new login
                viewModelScope.launch {
                    repository.restoreFromCloud()
                }
                onSuccess()
            }.onFailure {
                _uiEvent.emit(it.message ?: "লগইন ব্যর্থ হয়েছে")
            }
        }
    }

    fun signup(name: String, email: String, pass: String, confirmPass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = authManager.signUpWithEmail(name, email, pass, confirmPass)
            _isLoading.value = false
            res.onSuccess {
                exitGuestMode()
                _uiEvent.emit("অ্যাকাউন্ট সফলভাবে তৈরি করা হয়েছে")
                onSuccess()
            }.onFailure {
                _uiEvent.emit(it.message ?: "অ্যাকাউন্ট তৈরি ব্যর্থ হয়েছে")
            }
        }
    }

    fun sendPasswordReset(email: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = authManager.sendPasswordReset(email)
            _isLoading.value = false
            res.onSuccess {
                _uiEvent.emit("আপনার ইমেইলে পাসওয়ার্ড রিসেট লিংক পাঠানো হয়েছে।")
                onSuccess()
            }.onFailure {
                _uiEvent.emit(it.message ?: "ইমেইল পাঠাতে ব্যর্থ হয়েছে")
            }
        }
    }

    fun googleLogin(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = authManager.signInWithGoogle()
            _isLoading.value = false
            res.onSuccess {
                exitGuestMode()
                _uiEvent.emit("Google অ্যাকাউন্ট দিয়ে সফলভাবে প্রবেশ করেছেন")
                viewModelScope.launch {
                    repository.restoreFromCloud()
                }
                onSuccess()
            }.onFailure {
                _uiEvent.emit(it.message ?: "Google লগইন ব্যর্থ হয়েছে")
            }
        }
    }

    fun facebookLogin(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = authManager.signInWithFacebook()
            _isLoading.value = false
            res.onSuccess {
                exitGuestMode()
                _uiEvent.emit("Facebook অ্যাকাউন্ট দিয়ে সফলভাবে প্রবেশ করেছেন")
                viewModelScope.launch {
                    repository.restoreFromCloud()
                }
                onSuccess()
            }.onFailure {
                _uiEvent.emit(it.message ?: "Facebook লগইন ব্যর্থ হয়েছে")
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        authManager.logout()
        exitGuestMode()
        onLoggedOut()
    }

    // Cloud Backup & Restore Actions
    fun backupToCloud(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val user = currentUser.value
        if (_isGuestMode.value || user == null) {
            triggerGuestRestriction()
            onComplete(false, "লগইন করা আবশ্যক")
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _isSyncing.value = true
            val result = repository.backupToCloud()
            _isLoading.value = false
            _isSyncing.value = false
            result.onSuccess { summary ->
                _uiEvent.emit(summary.message)
                onComplete(true, summary.message)
            }.onFailure { error ->
                val errMsg = error.message ?: "ক্লাউড ব্যাকআপ ব্যর্থ হয়েছে"
                _uiEvent.emit(errMsg)
                onComplete(false, errMsg)
            }
        }
    }

    fun restoreFromCloud(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val user = currentUser.value
        if (_isGuestMode.value || user == null) {
            triggerGuestRestriction()
            onComplete(false, "লগইন করা আবশ্যক")
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _isSyncing.value = true
            val result = repository.restoreFromCloud()
            _isLoading.value = false
            _isSyncing.value = false
            result.onSuccess { summary ->
                _uiEvent.emit(summary.message)
                onComplete(true, summary.message)
            }.onFailure { error ->
                val errMsg = error.message ?: "ক্লাউড রিস্টোর ব্যর্থ হয়েছে"
                _uiEvent.emit(errMsg)
                onComplete(false, errMsg)
            }
        }
    }

    // Business Logic Actions
    fun addTransaction(
        type: String,
        category: String,
        amount: Double,
        date: String,
        time: String,
        description: String,
        accountName: String,
        onSuccess: () -> Unit
    ) {
        val user = currentUser.value
        if (_isGuestMode.value || user == null) {
            triggerGuestRestriction()
            return
        }
        if (amount <= 0) {
            viewModelScope.launch { _uiEvent.emit("সঠিক পরিমাণ লিখুন") }
            return
        }

        val item = TransactionEntity(
            id = UUID.randomUUID().toString(),
            userId = user.uid,
            type = type,
            category = category,
            amount = amount,
            date = date,
            time = time,
            timestamp = System.currentTimeMillis(),
            description = description,
            accountName = accountName
        )

        viewModelScope.launch {
            repository.addTransaction(item)
            _uiEvent.emit(if (type == "INCOME") "আয় সংরক্ষণ করা হয়েছে" else "ব্যয় সংরক্ষণ করা হয়েছে")
            onSuccess()
        }
    }

    fun deleteTransaction(id: String) {
        val user = currentUser.value
        if (_isGuestMode.value || user == null) {
            triggerGuestRestriction()
            return
        }
        viewModelScope.launch {
            repository.deleteTransaction(id, user.uid)
            _uiEvent.emit("লেনদেন মুছে ফেলা হয়েছে")
        }
    }

    fun addAccount(name: String, type: String, balance: Double, onSuccess: () -> Unit) {
        val user = currentUser.value
        if (_isGuestMode.value || user == null) {
            triggerGuestRestriction()
            return
        }
        if (name.isBlank()) {
            viewModelScope.launch { _uiEvent.emit("অ্যাকাউন্টের নাম লিখুন") }
            return
        }
        val account = AccountEntity(
            id = UUID.randomUUID().toString(),
            userId = user.uid,
            name = name,
            accountType = type,
            balance = balance
        )
        viewModelScope.launch {
            repository.addAccount(account)
            _uiEvent.emit("নতুন অ্যাকাউন্ট যোগ করা হয়েছে")
            onSuccess()
        }
    }

    fun addBudget(category: String, amount: Double, onSuccess: () -> Unit) {
        val user = currentUser.value
        if (_isGuestMode.value || user == null) {
            triggerGuestRestriction()
            return
        }
        if (amount <= 0) {
            viewModelScope.launch { _uiEvent.emit("সঠিক বাজেট পরিমাণ লিখুন") }
            return
        }
        val budget = BudgetEntity(
            id = UUID.randomUUID().toString(),
            userId = user.uid,
            category = category,
            allocatedAmount = amount,
            monthYear = "2026-08"
        )
        viewModelScope.launch {
            repository.addBudget(budget)
            _uiEvent.emit("বাজেট সংরক্ষণ করা হয়েছে")
            onSuccess()
        }
    }

    fun addLoan(
        type: String,
        personName: String,
        amount: Double,
        date: String,
        note: String = "",
        phoneNumber: String = "",
        dueDate: String = "",
        accountName: String = "ক্যাশ",
        onSuccess: () -> Unit
    ) {
        val user = currentUser.value
        if (_isGuestMode.value || user == null) {
            triggerGuestRestriction()
            return
        }
        if (personName.isBlank() || amount <= 0) {
            viewModelScope.launch { _uiEvent.emit("ব্যক্তির নাম ও পরিমাণ লিখুন") }
            return
        }
        val normalizedDirection = if (type == "LENT" || type == "RECEIVABLE") "RECEIVABLE" else "PAYABLE"
        val loan = LoanEntity(
            id = UUID.randomUUID().toString(),
            userId = user.uid,
            type = normalizedDirection,
            personName = personName,
            phoneNumber = phoneNumber,
            amount = amount,
            currentBalance = amount,
            date = date,
            dueDate = dueDate,
            accountName = accountName,
            note = note,
            isPaid = false
        )
        viewModelScope.launch {
            repository.addLoan(loan)
            val msg = if (normalizedDirection == "RECEIVABLE") "নতুন পাওনা রেকর্ড যোগ করা হয়েছে" else "নতুন দেনা রেকর্ড যোগ করা হয়েছে"
            _uiEvent.emit(msg)
            onSuccess()
        }
    }

    fun addDuePayment(
        loan: LoanEntity,
        paymentAmount: Double,
        paymentMethod: String,
        note: String,
        onSuccess: (com.example.data.local.DuePaymentEntity) -> Unit
    ) {
        val user = currentUser.value
        if (_isGuestMode.value || user == null) {
            triggerGuestRestriction()
            return
        }
        if (paymentAmount <= 0) {
            viewModelScope.launch { _uiEvent.emit("সঠিক পরিশোধের পরিমাণ লিখুন") }
            return
        }
        val prevBalance = loan.currentBalance
        if (paymentAmount > prevBalance) {
            viewModelScope.launch { _uiEvent.emit("পরিশোধের পরিমাণ বকেয়ার চেয়ে বেশি হতে পারবে না") }
            return
        }

        val remaining = maxOf(0.0, prevBalance - paymentAmount)
        val isFull = remaining <= 0.0
        val statusText = if (isFull) "সম্পূর্ণ পরিশোধ" else "আংশিক পরিশোধ"

        val direction = if (loan.type == "LENT" || loan.type == "RECEIVABLE") "RECEIVABLE" else "PAYABLE"

        val sdfDate = java.text.SimpleDateFormat("dd MMM, yyyy", java.util.Locale("bn"))
        val sdfTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        val currentDateStr = sdfDate.format(java.util.Date())
        val currentTimeStr = sdfTime.format(java.util.Date())

        val recNumber = "REC-" + java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date()) + "-" + (1000..9999).random()

        val payment = com.example.data.local.DuePaymentEntity(
            id = UUID.randomUUID().toString(),
            dueId = loan.id,
            userId = user.uid,
            personName = loan.personName,
            direction = direction,
            receiptNumber = recNumber,
            previousBalance = prevBalance,
            paymentAmount = paymentAmount,
            remainingBalance = remaining,
            paymentMethod = paymentMethod,
            paymentDate = currentDateStr,
            paymentTime = currentTimeStr,
            status = statusText,
            note = note,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            repository.updateLoan(
                loan.copy(
                    currentBalance = remaining,
                    isPaid = isFull
                )
            )
            repository.addDuePayment(payment)

            val eventMsg = if (direction == "RECEIVABLE") "পাওনা পরিশোধ রসিদ তৈরি হয়েছে" else "দেনা পরিশোধ রসিদ তৈরি হয়েছে"
            _uiEvent.emit(eventMsg)
            onSuccess(payment)
        }
    }

    fun toggleLoanPaidStatus(loan: LoanEntity) {
        if (_isGuestMode.value || currentUser.value == null) {
            triggerGuestRestriction()
            return
        }
        viewModelScope.launch {
            val newIsPaid = !loan.isPaid
            val newBalance = if (newIsPaid) 0.0 else loan.amount
            repository.updateLoan(loan.copy(isPaid = newIsPaid, currentBalance = newBalance))
            _uiEvent.emit("স্ট্যাটাস পরিবর্তন করা হয়েছে")
        }
    }

    fun deleteLoan(id: String) {
        val user = currentUser.value
        if (_isGuestMode.value || user == null) {
            triggerGuestRestriction()
            return
        }
        viewModelScope.launch {
            repository.deleteLoan(id, user.uid)
            _uiEvent.emit("ধার/ঋণ রেকর্ড মুছে ফেলা হয়েছে")
        }
    }

    fun deleteDuePayment(id: String) {
        val user = currentUser.value
        if (_isGuestMode.value || user == null) {
            triggerGuestRestriction()
            return
        }
        viewModelScope.launch {
            repository.deleteDuePayment(id, user.uid)
            _uiEvent.emit("পেমেন্ট রসিদ রেকর্ড মুছে ফেলা হয়েছে")
        }
    }

    // Saving Goals Actions
    fun addSavingGoal(title: String, amount: Double, onSuccess: () -> Unit = {}) {
        val user = currentUser.value
        if (_isGuestMode.value || user == null) {
            triggerGuestRestriction()
            return
        }
        if (amount <= 0) {
            viewModelScope.launch { _uiEvent.emit("সঠিক সঞ্চয়ের পরিমাণ লিখুন") }
            return
        }
        val goal = SavingGoalEntity(
            id = UUID.randomUUID().toString(),
            userId = user.uid,
            title = if (title.isBlank()) "সঞ্চয়" else title,
            targetAmount = amount,
            savedAmount = 0.0
        )
        viewModelScope.launch {
            repository.addSavingGoal(goal)
            _uiEvent.emit("সঞ্চয় Goal সফলভাবে তৈরি হয়েছে")
            onSuccess()
        }
    }

    fun deleteSavingGoal(id: String) {
        val user = currentUser.value
        if (_isGuestMode.value || user == null) {
            triggerGuestRestriction()
            return
        }
        viewModelScope.launch {
            repository.deleteSavingGoal(id, user.uid)
            _uiEvent.emit("সঞ্চয় Goal মুছে ফেলা হয়েছে")
        }
    }

    // Reminder Actions
    fun addReminder(
        title: String,
        type: String = "REMINDER",
        amount: Double? = null,
        personName: String = "",
        date: String = "আগামীকাল",
        time: String = "সকাল ১০:০০",
        dueDay: Int? = null,
        recurrence: String = "ONCE",
        onSuccess: () -> Unit = {}
    ) {
        val user = currentUser.value
        if (_isGuestMode.value || user == null) {
            triggerGuestRestriction()
            return
        }
        val reminderId = UUID.randomUUID().toString()
        val reminder = ReminderEntity(
            id = reminderId,
            userId = user.uid,
            title = title,
            type = type,
            amount = amount,
            personName = personName,
            date = date,
            time = time,
            dueDay = dueDay,
            recurrence = recurrence
        )
        viewModelScope.launch {
            repository.addReminder(reminder)
            // Schedule notification
            if (type == "EMI" && dueDay != null) {
                val amtStr = if (amount != null && amount > 0) HisabActionEngine.formatBengaliCurrency(amount) else ""
                ReminderScheduler.scheduleMonthlyEmi(
                    context = getApplication(),
                    emiId = reminderId,
                    title = "🔔 EMI Reminder",
                    message = "আজ $title $amtStr দেওয়ার সময়।",
                    dueDay = dueDay
                )
            } else {
                val amtStr = if (amount != null && amount > 0) " (${HisabActionEngine.formatBengaliCurrency(amount)})" else ""
                ReminderScheduler.scheduleReminder(
                    context = getApplication(),
                    reminderId = reminderId,
                    title = "🔔 হিসাব খাতা রিমাইন্ডার",
                    message = "$title$amtStr - $date $time",
                    triggerTimeMillis = System.currentTimeMillis() + (60 * 1000) // Default active trigger or alarm
                )
            }
            _uiEvent.emit("রিমাইন্ডার সফলভাবে সেট করা হয়েছে")
            onSuccess()
        }
    }

    fun deleteReminder(id: String) {
        val user = currentUser.value
        if (_isGuestMode.value || user == null) {
            triggerGuestRestriction()
            return
        }
        viewModelScope.launch {
            repository.deleteReminder(id, user.uid)
            _uiEvent.emit("রিমাইন্ডার মুছে ফেলা হয়েছে")
        }
    }

    // Step 3: AI Action & Automation Execution
    fun executeAiAction(
        action: StructuredHisabAction,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = currentUser.value
        if (_isGuestMode.value || user == null) {
            triggerGuestRestriction()
            onError("অ্যাকশন সম্পাদনের জন্য লগইন করা আবশ্যক")
            return
        }

        val sdf = java.text.SimpleDateFormat("dd MMM, yyyy", java.util.Locale("bn"))
        val todayStr = sdf.format(java.util.Date())

        when (action.action) {
            "CREATE_SAVING_GOAL" -> {
                val amt = action.amount ?: 0.0
                if (amt <= 0) {
                    onError("সঞ্চয়ের সঠিক পরিমাণ উল্লেখ করুন")
                    return
                }
                val goalTitle = action.title ?: "সঞ্চয়"
                addSavingGoal(goalTitle, amt) {
                    val formatted = HisabActionEngine.formatBengaliCurrency(amt)
                    onSuccess("$formatted সঞ্চয় goal তৈরি করা হয়েছে।")
                }
            }
            "CREATE_BUDGET" -> {
                val amt = action.amount ?: 0.0
                if (amt <= 0) {
                    onError("বাজেটের সঠিক পরিমাণ উল্লেখ করুন")
                    return
                }
                val category = action.category ?: "মোট বাজেট"
                addBudget(category, amt) {
                    val formatted = HisabActionEngine.formatBengaliCurrency(amt)
                    val msg = if (action.category != null) {
                        "ঠিক আছে। ‘${action.category}’-এর জন্য $formatted budget তৈরি হয়েছে।"
                    } else {
                        "ঠিক আছে। এই মাসের জন্য $formatted budget তৈরি হয়েছে।"
                    }
                    onSuccess(msg)
                }
            }
            "CREATE_RECEIVABLE" -> {
                val amt = action.amount ?: 0.0
                val person = action.person ?: "অজ্ঞাত ব্যক্তি"
                if (amt <= 0) {
                    onError("পাওনার সঠিক পরিমাণ উল্লেখ করুন")
                    return
                }
                addLoan(
                    type = "RECEIVABLE",
                    personName = person,
                    amount = amt,
                    date = todayStr,
                    note = action.rawText
                ) {
                    val formatted = HisabActionEngine.formatBengaliCurrency(amt)
                    onSuccess("${person}-এর কাছে $formatted পাওনা যোগ করা হয়েছে।")
                }
            }
            "CREATE_PAYABLE" -> {
                val amt = action.amount ?: 0.0
                val person = action.person ?: "অজ্ঞাত ব্যক্তি"
                if (amt <= 0) {
                    onError("দেনার সঠিক পরিমাণ উল্লেখ করুন")
                    return
                }
                addLoan(
                    type = "PAYABLE",
                    personName = person,
                    amount = amt,
                    date = todayStr,
                    note = action.rawText
                ) {
                    val formatted = HisabActionEngine.formatBengaliCurrency(amt)
                    onSuccess("${person}-কে $formatted দেওয়ার দেনা যোগ করা হয়েছে।")
                }
            }
            "CREATE_EMI" -> {
                val amt = action.amount
                if (amt == null || amt <= 0) {
                    onError("EMI-এর পরিমাণ কত?")
                    return
                }
                val dueDay = action.dueDay ?: 5
                val title = action.title ?: "EMI"
                addReminder(
                    title = title,
                    type = "EMI",
                    amount = amt,
                    dueDay = dueDay,
                    recurrence = "MONTHLY"
                ) {
                    val formatted = HisabActionEngine.formatBengaliCurrency(amt)
                    val benDay = HisabActionEngine.convertToBengaliDigits(dueDay.toString())
                    onSuccess("প্রতি মাসের $benDay তারিখে $formatted টাকার Monthly EMI reminder সেট করা হয়েছে।")
                }
            }
            "CREATE_REMINDER" -> {
                val title = action.title ?: action.rawText
                val date = action.date ?: "আগামীকাল"
                val time = action.time ?: "সকাল ১০:০০"
                addReminder(
                    title = title,
                    type = "REMINDER",
                    amount = action.amount,
                    date = date,
                    time = time,
                    recurrence = "ONCE"
                ) {
                    onSuccess("$date $time-এর reminder সেট করা হয়েছে।")
                }
            }
            else -> {
                onError("অ্যাকশনটি বুঝতে পারিনি।")
            }
        }
    }
}
