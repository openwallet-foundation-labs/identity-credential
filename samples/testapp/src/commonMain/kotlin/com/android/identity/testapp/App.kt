package com.android.identity.testapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.android.identity.testapp.ui.AboutScreen
import com.android.identity.testapp.ui.AndroidKeystoreSecureAreaScreen
import com.android.identity.testapp.ui.SecureEnclaveSecureAreaScreen
import com.android.identity.testapp.ui.SoftwareSecureAreaScreen
import com.android.identity.testapp.ui.StartScreen
import identitycredential.samples.testapp.generated.resources.Res
import identitycredential.samples.testapp.generated.resources.about_screen_title
import identitycredential.samples.testapp.generated.resources.android_keystore_secure_area_screen_title
import identitycredential.samples.testapp.generated.resources.back_button
import identitycredential.samples.testapp.generated.resources.secure_enclave_secure_area_screen_title
import identitycredential.samples.testapp.generated.resources.software_secure_area_screen_title
import identitycredential.samples.testapp.generated.resources.start_screen_title
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

enum class Screen(val title: StringResource) {
    About(title = Res.string.about_screen_title),
    Start(title = Res.string.start_screen_title),
    SoftwareSecureArea(title = Res.string.software_secure_area_screen_title),
    AndroidKeystoreSecureArea(title = Res.string.android_keystore_secure_area_screen_title),
    SecureEnclaveSecureArea(title = Res.string.secure_enclave_secure_area_screen_title),
}

class App {

    companion object {
        private const val TAG = "App"
    }

    private lateinit var snackbarHostState: SnackbarHostState

    @Composable
    @Preview
    fun Content(navController: NavHostController = rememberNavController()) {
        // Get current back stack entry
        val backStackEntry by navController.currentBackStackEntryAsState()
        // Get the name of the current screen
        val currentScreen = Screen.valueOf(
            backStackEntry?.destination?.route ?: Screen.Start.name
        )

        snackbarHostState = remember { SnackbarHostState() }
        MaterialTheme {
            // A surface container using the 'background' color from the theme
            Scaffold(
                topBar = {
                    AppBar(
                        currentScreen = currentScreen,
                        canNavigateBack = navController.previousBackStackEntry != null,
                        navigateUp = { navController.navigateUp() }
                    )
                },
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            ) { innerPadding ->


                NavHost(
                    navController = navController,
                    startDestination = Screen.Start.name,
                    modifier = Modifier
                        .fillMaxSize()
                        //.verticalScroll(rememberScrollState())
                        .padding(innerPadding)
                ) {
                    composable(route = Screen.About.name) {
                        AboutScreen()
                    }
                    composable(route = Screen.Start.name) {
                        StartScreen(navController)
                    }
                    composable(route = Screen.SoftwareSecureArea.name) {
                        SoftwareSecureAreaScreen(showToast = { message -> showToast(message) })
                    }
                    composable(route = Screen.AndroidKeystoreSecureArea.name) {
                       AndroidKeystoreSecureAreaScreen(showToast = { message -> showToast(message) })
                    }
                    composable(route = Screen.SecureEnclaveSecureArea.name) {
                        SecureEnclaveSecureAreaScreen(showToast = { message -> showToast(message) })
                    }
                }
            }
        }
    }

    private fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            when (snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "OK",
                duration = SnackbarDuration.Short,
            )) {
                SnackbarResult.Dismissed -> {
                }

                SnackbarResult.ActionPerformed -> {
                }
            }
        }
    }
}

/**
 * Composable that displays the topBar and displays back button if back navigation is possible.
 */
@Composable
fun AppBar(
    currentScreen: Screen,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(stringResource(currentScreen.title)) },
        //colors = TopAppBarDefaults.mediumTopAppBarColors(
        //    containerColor = MaterialTheme.colorScheme.primaryContainer
        //),
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.back_button)
                    )
                }
            }
        }
    )
}