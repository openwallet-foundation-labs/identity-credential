package com.android.mdl.app.selfsigned

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.credentialtype.knowntypes.EUPersonalID
import com.android.identity.credentialtype.knowntypes.VehicleRegistration
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.wallet.document.DocumentColor
import com.android.identity.wallet.selfsigned.AddSelfSignedScreenState
import com.android.identity.wallet.selfsigned.AddSelfSignedViewModel
import com.android.identity.wallet.util.PreferencesHelper
import com.android.identity.wallet.util.ProvisioningUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelfSignedScreenStateTest {

    private val savedStateHandle = SavedStateHandle()
    private lateinit var repository: SecureAreaRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repository = ProvisioningUtil.getInstance(context).secureAreaRepository
        val storageDir = PreferencesHelper.getKeystoreBackedStorageLocation(context)
        val storageEngine = AndroidStorageEngine.Builder(context, storageDir).build()
        val androidKeystoreSecureArea = AndroidKeystoreSecureArea(context, storageEngine)
        val softwareSecureArea = SoftwareSecureArea(storageEngine)
        repository.addImplementation(androidKeystoreSecureArea)
        repository.addImplementation(softwareSecureArea)
    }

    @Test
    fun defaultScreenState() {
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState())
    }

    @Test
    fun updateDocumentType() {
        val personalId = EUPersonalID.getCredentialType().mdocCredentialType?.docType!!
        val name= EUPersonalID.getCredentialType().displayName
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateDocumentType(personalId, name)

        assertThat(viewModel.screenState.value).isEqualTo(
            AddSelfSignedScreenState(documentType = personalId, documentName = "EU Personal ID")
        )
    }

    @Test
    fun updateDocumentName() {
        val newName = ":irrelevant:"
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateDocumentName(newName)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState(documentName = newName))
    }

    @Test
    fun updateDocumentTypeAfterNameUpdate() {
        val registration = VehicleRegistration.getCredentialType().mdocCredentialType?.docType!!
        val name = VehicleRegistration.getCredentialType().displayName
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateDocumentName(":irrelevant:")
        viewModel.updateDocumentType(registration, name)

        assertThat(viewModel.screenState.value).isEqualTo(
            AddSelfSignedScreenState(
                documentType = registration,
                documentName = "Vehicle Registration"
            )
        )
    }

    @Test
    fun updateCardArt() {
        val blue = DocumentColor.Blue
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateCardArt(blue)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState(cardArt = blue))
    }

    @Test
    fun updateValidityInDays() {
        val newValue = 15
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateValidityInDays(newValue)

        assertThat(viewModel.screenState.value.validityInDays)
            .isEqualTo(newValue)
    }

    @Test
    fun updateValidityInDaysBelowMinValidityDays() {
        val defaultMinValidity = 10
        val belowMinValidity = 9
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateValidityInDays(defaultMinValidity)
        viewModel.updateValidityInDays(belowMinValidity)

        assertThat(viewModel.screenState.value.validityInDays)
            .isEqualTo(defaultMinValidity)
    }

    @Test
    fun updateMinValidityInDays() {
        val newMinValidity = 15
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateMinValidityInDays(newMinValidity)

        assertThat(viewModel.screenState.value.minValidityInDays)
            .isEqualTo(newMinValidity)
    }

    @Test
    fun updateMinValidityInDaysAboveValidityInDays() {
        val defaultValidityInDays = 30
        val minValidityInDays = defaultValidityInDays + 5
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateMinValidityInDays(minValidityInDays)

        assertThat(viewModel.screenState.value.validityInDays)
            .isEqualTo(minValidityInDays)
    }

    @Test
    fun updateNumberOfMso() {
        val msoCount = 2
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateNumberOfMso(msoCount)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState(numberOfMso = msoCount))
    }

    @Test
    fun updateNumberOfMsoInvalidValue() {
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateNumberOfMso(1)
        viewModel.updateNumberOfMso(0)
        viewModel.updateNumberOfMso(-1)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState(numberOfMso = 1))
    }

    @Test
    fun updateMaxUseOfMso() {
        val maxMsoUsages = 3
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateMaxUseOfMso(maxMsoUsages)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState(maxUseOfMso = maxMsoUsages))
    }

    @Test
    fun updateMaxUseOfMsoInvalidValue() {
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateMaxUseOfMso(1)
        viewModel.updateMaxUseOfMso(0)
        viewModel.updateMaxUseOfMso(-1)

        assertThat(viewModel.screenState.value)
            .isEqualTo(AddSelfSignedScreenState(maxUseOfMso = 1))
    }
}