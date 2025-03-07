package org.multipaz.testapp

import androidx.navigation.NavType
import androidx.navigation.navArgument
import identitycredential.samples.testapp.generated.resources.Res
import identitycredential.samples.testapp.generated.resources.about_screen_title
import identitycredential.samples.testapp.generated.resources.android_keystore_secure_area_screen_title
import identitycredential.samples.testapp.generated.resources.certificate_viewer_examples_title
import identitycredential.samples.testapp.generated.resources.cloud_secure_area_screen_title
import identitycredential.samples.testapp.generated.resources.consent_modal_bottom_sheet_list_screen_title
import identitycredential.samples.testapp.generated.resources.consent_modal_bottom_sheet_screen_title
import identitycredential.samples.testapp.generated.resources.iso_mdoc_multi_device_testing_title
import identitycredential.samples.testapp.generated.resources.iso_mdoc_proximity_reading_title
import identitycredential.samples.testapp.generated.resources.iso_mdoc_proximity_sharing_title
import identitycredential.samples.testapp.generated.resources.provisioning_test_title
import identitycredential.samples.testapp.generated.resources.passphrase_entry_field_screen_title
import identitycredential.samples.testapp.generated.resources.qr_codes_screen_title
import identitycredential.samples.testapp.generated.resources.nfc_screen_title
import identitycredential.samples.testapp.generated.resources.presentment_title
import identitycredential.samples.testapp.generated.resources.secure_enclave_secure_area_screen_title
import identitycredential.samples.testapp.generated.resources.software_secure_area_screen_title
import identitycredential.samples.testapp.generated.resources.start_screen_title
import identitycredential.samples.testapp.generated.resources.certificate_viewer_title
import identitycredential.samples.testapp.generated.resources.credential_claims_viewer_title
import identitycredential.samples.testapp.generated.resources.credential_viewer_title
import identitycredential.samples.testapp.generated.resources.document_store_screen_title
import identitycredential.samples.testapp.generated.resources.notifications_title
import identitycredential.samples.testapp.generated.resources.document_viewer_title
import identitycredential.samples.testapp.generated.resources.passphrase_prompt_screen_title
import identitycredential.samples.testapp.generated.resources.rich_text_title
import identitycredential.samples.testapp.generated.resources.screen_lock_title
import identitycredential.samples.testapp.generated.resources.settings_screen_title
import org.jetbrains.compose.resources.StringResource

sealed interface Destination {
    val route: String
    val title: StringResource
}

data object StartDestination : Destination {
    override val route = "start"
    override val title = Res.string.start_screen_title
}

data object SettingsDestination : Destination {
    override val route = "settings"
    override val title = Res.string.settings_screen_title
}

data object AboutDestination : Destination {
    override val route = "about"
    override val title = Res.string.about_screen_title
}

data object DocumentStoreDestination : Destination {
    override val route = "document_store"
    override val title = Res.string.document_store_screen_title
}

data object DocumentViewerDestination : Destination {
    override val route = "document_viewer"
    override val title = Res.string.document_viewer_title
    const val DOCUMENT_ID = "document_id_arg"
    val routeWithArgs = "$route/{$DOCUMENT_ID}"
    val arguments = listOf(
        navArgument(DOCUMENT_ID) { type = NavType.StringType },
    )
}

data object CredentialViewerDestination : Destination {
    override val route = "credential_viewer"
    override val title = Res.string.credential_viewer_title
    const val DOCUMENT_ID = "document_id_arg"
    const val CREDENTIAL_ID = "credential_id_arg"
    val routeWithArgs = "$route/{$DOCUMENT_ID}/{$CREDENTIAL_ID}"
    val arguments = listOf(
        navArgument(DOCUMENT_ID) { type = NavType.StringType },
        navArgument(CREDENTIAL_ID) { type = NavType.StringType },
    )
}

data object CredentialClaimsViewerDestination : Destination {
    override val route = "claims_viewer"
    override val title = Res.string.credential_claims_viewer_title
    const val DOCUMENT_ID = "document_id_arg"
    const val CREDENTIAL_ID = "credential_id_arg"
    val routeWithArgs = "$route/{$DOCUMENT_ID}/{$CREDENTIAL_ID}"
    val arguments = listOf(
        navArgument(DOCUMENT_ID) { type = NavType.StringType },
        navArgument(CREDENTIAL_ID) { type = NavType.StringType },
    )
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

data object PassphrasePromptDestination : Destination {
    override val route = "passphrase_prompt"
    override val title = Res.string.passphrase_prompt_screen_title
}

data object ProvisioningTestDestination : Destination {
    override val route: String = "provisioning_test"
    override val title = Res.string.provisioning_test_title
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

data object NfcDestination : Destination {
    override val route = "nfc"
    override val title = Res.string.nfc_screen_title
}

data object IsoMdocProximitySharingDestination : Destination {
    override val route = "iso_mdoc_proximity_sharing"
    override val title = Res.string.iso_mdoc_proximity_sharing_title
}

data object IsoMdocProximityReadingDestination : Destination {
    override val route = "iso_mdoc_proximity_reading"
    override val title = Res.string.iso_mdoc_proximity_reading_title
}

data object IsoMdocMultiDeviceTestingDestination : Destination {
    override val route = "iso_mdoc_multi_device_testing"
    override val title = Res.string.iso_mdoc_multi_device_testing_title
}

data object PresentmentDestination : Destination {
    override val route = "presentment"
    override val title = Res.string.presentment_title
}

data object CertificatesViewerExamplesDestination : Destination {
    override val route = "certificates_viewer"
    override val title = Res.string.certificate_viewer_examples_title
}

data object CertificateViewerDestination : Destination {
    override val route = "certificate_details_viewer"
    override val title = Res.string.certificate_viewer_title
    const val CERTIFICATE_DATA = "certificate_data_arg"
    val routeWithArgs = "$route/{$CERTIFICATE_DATA}"
    val arguments = listOf(
        navArgument(CERTIFICATE_DATA) { type = NavType.StringType },
    )
}

data object RichTextDestination : Destination {
    override val route = "rich_text"
    override val title = Res.string.rich_text_title
}

data object NotificationsDestination : Destination {
    override val route = "notifications"
    override val title = Res.string.notifications_title
}

data object ScreenLockDestination : Destination {
    override val route = "screen_lock"
    override val title = Res.string.screen_lock_title
}

val appDestinations = listOf(
    StartDestination,
    SettingsDestination,
    AboutDestination,
    DocumentStoreDestination,
    DocumentViewerDestination,
    CredentialViewerDestination,
    CredentialClaimsViewerDestination,
    SoftwareSecureAreaDestination,
    AndroidKeystoreSecureAreaDestination,
    SecureEnclaveSecureAreaDestination,
    CloudSecureAreaDestination,
    PassphraseEntryFieldDestination,
    PassphrasePromptDestination,
    ProvisioningTestDestination,
    ConsentModalBottomSheetListDestination,
    ConsentModalBottomSheetDestination,
    QrCodesDestination,
    NfcDestination,
    IsoMdocProximitySharingDestination,
    IsoMdocProximityReadingDestination,
    IsoMdocMultiDeviceTestingDestination,
    PresentmentDestination,
    CertificatesViewerExamplesDestination,
    CertificateViewerDestination,
    RichTextDestination,
    NotificationsDestination,
    ScreenLockDestination,
)