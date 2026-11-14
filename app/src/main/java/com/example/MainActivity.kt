package com.example

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.child.ChildShellScreen
import com.example.child.ChildViewModel
import com.example.core.data.local.FamilyGuardDatabase
import com.example.core.data.local.LocalPreferencesRepository
import com.example.core.data.local.LocalPrefsState
import com.example.core.data.remote.FamilyGuardRepository
import com.example.core.domain.AppRole
import com.example.core.navigation.FamilyGuardRoute
import com.example.core.ui.AuthScreen
import com.example.core.ui.RolePickerScreen
import com.example.core.ui.attemptAutoSignIn
import com.example.core.ui.signOut
import com.example.core.utils.ConnectivityObserver
import com.example.parent.ParentShellScreen
import com.example.parent.ParentViewModel
import com.example.parent.PrivacyDataScreen
import com.example.ui.theme.FamilyGuardTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val prefs = remember { LocalPreferencesRepository(context.applicationContext) }
            val localPrefs by prefs.state.collectAsStateWithLifecycle(initialValue = LocalPrefsState())
            val scope = rememberCoroutineScope()

            val effectiveLocale = remember(localPrefs.languageOverride) {
                when (localPrefs.languageOverride) {
                    "ar" -> Locale("ar")
                    "en" -> Locale("en")
                    else -> Locale.getDefault()
                }
            }
            val isArabic = effectiveLocale.language == "ar"
            val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
            val localizedContext = remember(context, effectiveLocale) {
                createLocalizedContext(context, effectiveLocale)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalLayoutDirection provides layoutDirection
            ) {
                FamilyGuardTheme(isArabicLocale = isArabic) {
                    FamilyGuardRoot(
                        prefs = prefs,
                        localPrefs = localPrefs,
                        onLanguageSelected = { code ->
                            scope.launch { prefs.setLanguageOverride(code) }
                        }
                    )
                }
            }
        }
    }

    private fun createLocalizedContext(base: Context, locale: Locale): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}

@Composable
private fun FamilyGuardRoot(
    prefs: LocalPreferencesRepository,
    localPrefs: LocalPrefsState,
    onLanguageSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
            onUnauthenticated = { currentUser = Firebase.auth.currentUser },
            scope = scope
        )
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
            languageCode = localPrefs.languageOverride,
            onLanguageSelected = onLanguageSelected
        )
    } else {
        val uid = user.uid
        androidx.compose.runtime.key(uid) {
            AuthenticatedNavigation(
                currentUserId = uid,
                prefs = prefs,
                localPrefs = localPrefs,
                onLanguageSelected = onLanguageSelected,
                onSignOut = {
                    signOut(credentialManager, scope) {
                        currentUser = null
                    }
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedNavigation(
    currentUserId: String,
    prefs: LocalPreferencesRepository,
    localPrefs: LocalPrefsState,
    onLanguageSelected: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isOnline by ConnectivityObserver.observeOnline(context)
        .collectAsStateWithLifecycle(initialValue = ConnectivityObserver.isOnline(context))

    val startDestination: FamilyGuardRoute = remember(currentUserId) {
        when (localPrefs.activeRole) {
            AppRole.PARENT -> FamilyGuardRoute.ParentShell
            AppRole.CHILD -> FamilyGuardRoute.ChildShell
            null -> FamilyGuardRoute.RolePicker
        }
    }
    val navController = rememberNavController()

    val parentViewModel: ParentViewModel = viewModel(
        key = "parent_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(databaseId)
                ParentViewModel(
                    repository = FamilyGuardRepository(firestore),
                    prefs = LocalPreferencesRepository(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val childViewModel: ChildViewModel = viewModel(
        key = "child_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(databaseId)
                val roomDao = FamilyGuardDatabase.getInstance(app).familyGuardDao()
                ChildViewModel(
                    repository = FamilyGuardRepository(firestore),
                    dao = roomDao,
                    prefs = LocalPreferencesRepository(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable<FamilyGuardRoute.RolePicker> {
            RolePickerScreen(
                onSelectRole = { role ->
                    scope.launch { prefs.setActiveRole(role) }
                    val target = if (role == AppRole.PARENT) {
                        FamilyGuardRoute.ParentShell
                    } else {
                        FamilyGuardRoute.ChildShell
                    }
                    navController.navigate(target) {
                        popUpTo<FamilyGuardRoute.RolePicker> { inclusive = true }
                    }
                },
                languageCode = localPrefs.languageOverride,
                onLanguageSelected = onLanguageSelected
            )
        }

        composable<FamilyGuardRoute.ParentShell> {
            ParentShellScreen(
                viewModel = parentViewModel,
                isOnline = isOnline,
                languageCode = localPrefs.languageOverride,
                onLanguageSelected = onLanguageSelected,
                onOpenPrivacyData = { navController.navigate(FamilyGuardRoute.PrivacyData) },
                onSwitchToChildRole = {
                    scope.launch { prefs.setActiveRole(AppRole.CHILD) }
                    navController.navigate(FamilyGuardRoute.ChildShell) {
                        popUpTo<FamilyGuardRoute.ParentShell> { inclusive = true }
                    }
                },
                onSignOut = onSignOut
            )
        }

        composable<FamilyGuardRoute.PrivacyData> {
            PrivacyDataScreen(
                viewModel = parentViewModel,
                onBack = { navController.popBackStack() },
                onAccountDeleted = onSignOut
            )
        }

        composable<FamilyGuardRoute.ChildShell> {
            ChildShellScreen(
                viewModel = childViewModel,
                languageCode = localPrefs.languageOverride,
                onLanguageSelected = onLanguageSelected,
                onSwitchToParentRole = {
                    scope.launch { prefs.setActiveRole(AppRole.PARENT) }
                    navController.navigate(FamilyGuardRoute.ParentShell) {
                        popUpTo<FamilyGuardRoute.ChildShell> { inclusive = true }
                    }
                }
            )
        }
    }
}
