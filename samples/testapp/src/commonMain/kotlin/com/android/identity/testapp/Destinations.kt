package com.android.identity.testapp

import androidx.navigation.NavType
import androidx.navigation.navArgument
import identitycredential.samples.testapp.generated.resources.Res
import identitycredential.samples.testapp.generated.resources.about_screen_title
import identitycredential.samples.testapp.generated.resources.android_keystore_secure_area_screen_title
import identitycredential.samples.testapp.generated.resources.cloud_secure_area_screen_title
import identitycredential.samples.testapp.generated.resources.consent_modal_bottom_sheet_list_screen_title
import identitycredential.samples.testapp.generated.resources.consent_modal_bottom_sheet_screen_title
import identitycredential.samples.testapp.generated.resources.passphrase_entry_field_screen_title
import identitycredential.samples.testapp.generated.resources.qr_codes_screen_title
import identitycredential.samples.testapp.generated.resources.secure_enclave_secure_area_screen_title
import identitycredential.samples.testapp.generated.resources.software_secure_area_screen_title
import identitycredential.samples.testapp.generated.resources.start_screen_title
import org.jetbrains.compose.resources.StringResource

sealed interface Destination {
    val route: String
    val title: StringResource
}

data object StartDestination : Destination {
    override val route = "start"
    override val title = Res.string.start_screen_title
}

data object AboutDestination : Destination {
    override val route = "about"
    override val title = Res.string.about_screen_title
}

data object SoftwareSecureAreaDestination : Destination {
    override val route = "software_secure_area"
    override val title = Res.string.software_secure_area_screen_title
}

data object AndroidKeystoreSecureAreaDestination : Destination {
    override val route = "android_keystore_secure_area"
    override val title = Res.string.android_keystore_secure_area_screen_title
}

data object SecureEnclaveSecureAreaDestination : Destination {
    override val route = "secure_enclave_secure_area"
    override val title = Res.string.secure_enclave_secure_area_screen_title
}

data object CloudSecureAreaDestination : Destination {
    override val route = "cloud_secure_area"
    override val title = Res.string.cloud_secure_area_screen_title
}

data object PassphraseEntryFieldDestination : Destination {
    override val route = "passphrase_entry_field"
    override val title = Res.string.passphrase_entry_field_screen_title
}

data object ConsentModalBottomSheetListDestination : Destination {
    override val route = "consent_modal_bottom_sheet_list"
    override val title = Res.string.consent_modal_bottom_sheet_list_screen_title
}

data object ConsentModalBottomSheetDestination : Destination {
    override val route = "consent_modal_bottom_sheet"
    override val title = Res.string.consent_modal_bottom_sheet_screen_title
    const val mdlSampleRequestArg = "mdl_sample_request_arg"
    const val verifierTypeArg = "verifier_type"
    val routeWithArgs = "$route/{$mdlSampleRequestArg}/{$verifierTypeArg}"
    val arguments = listOf(
        navArgument(mdlSampleRequestArg) { type = NavType.StringType },
        navArgument(verifierTypeArg) { type = NavType.StringType },
    )
}

data object QrCodesDestination : Destination {
    override val route = "qr_codes"
    override val title = Res.string.qr_codes_screen_title
}

val appDestinations = listOf(
    StartDestination,
    AboutDestination,
    SoftwareSecureAreaDestination,
    AndroidKeystoreSecureAreaDestination,
    SecureEnclaveSecureAreaDestination,
    CloudSecureAreaDestination,
    PassphraseEntryFieldDestination,
    ConsentModalBottomSheetListDestination,
    ConsentModalBottomSheetDestination,
    QrCodesDestination,
)