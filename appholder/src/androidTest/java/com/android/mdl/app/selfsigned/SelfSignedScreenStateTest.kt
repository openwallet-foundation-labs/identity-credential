package com.android.mdl.app.selfsigned

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.documenttype.knowntypes.EUPersonalID
import com.android.identity.documenttype.knowntypes.VehicleRegistration
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.wallet.document.DocumentColor
import com.android.identity.wallet.selfsigned.AddSelfSignedScreenState
import com.android.identity.wallet.selfsigned.AddSelfSignedViewModel
import com.android.identity.wallet.util.PreferencesHelper
import com.android.identity.wallet.util.ProvisioningUtil
import kotlinx.io.files.Path
import org.junit.Assert.assertEquals
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
        val storageFile = Path(PreferencesHelper.getKeystoreBackedStorageLocation(context).path)
        val storageEngine = AndroidStorageEngine.Builder(context, storageFile).build()
        val androidKeystoreSecureArea = AndroidKeystoreSecureArea(context, storageEngine)
        val softwareSecureArea = SoftwareSecureArea(storageEngine)
        repository.addImplementation(androidKeystoreSecureArea)
        repository.addImplementation(softwareSecureArea)
    }

    @Test
    fun defaultScreenState() {
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        assertEquals(viewModel.screenState.value, AddSelfSignedScreenState())
    }

    @Test
    fun updateDocumentType() {
        val personalId = EUPersonalID.getDocumentType().mdocDocumentType?.docType!!
        val name= EUPersonalID.getDocumentType().displayName
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateDocumentType(personalId, name)

        assertEquals(viewModel.screenState.value,
            AddSelfSignedScreenState(documentType = personalId, documentName = "EU Personal ID")
        )
    }

    @Test
    fun updateDocumentName() {
        val newName = ":irrelevant:"
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateDocumentName(newName)

        assertEquals(viewModel.screenState.value, AddSelfSignedScreenState(documentName = newName))
    }

    @Test
    fun updateDocumentTypeAfterNameUpdate() {
        val registration = VehicleRegistration.getDocumentType().mdocDocumentType?.docType!!
        val name = VehicleRegistration.getDocumentType().displayName
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateDocumentName(":irrelevant:")
        viewModel.updateDocumentType(registration, name)

        assertEquals(viewModel.screenState.value,
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

        assertEquals(viewModel.screenState.value, AddSelfSignedScreenState(cardArt = blue))
    }

    @Test
    fun updateValidityInDays() {
        val newValue = 15
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateValidityInDays(newValue)

        assertEquals(viewModel.screenState.value.validityInDays, newValue)
    }

    @Test
    fun updateValidityInDaysBelowMinValidityDays() {
        val defaultMinValidity = 10
        val belowMinValidity = 9
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateValidityInDays(defaultMinValidity)
        viewModel.updateValidityInDays(belowMinValidity)

        assertEquals(viewModel.screenState.value.validityInDays, defaultMinValidity)
    }

    @Test
    fun updateMinValidityInDays() {
        val newMinValidity = 15
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateMinValidityInDays(newMinValidity)

        assertEquals(viewModel.screenState.value.minValidityInDays, newMinValidity)
    }

    @Test
    fun updateMinValidityInDaysAboveValidityInDays() {
        val defaultValidityInDays = 30
        val minValidityInDays = defaultValidityInDays + 5
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateMinValidityInDays(minValidityInDays)

        assertEquals(viewModel.screenState.value.validityInDays, minValidityInDays)
    }

    @Test
    fun updateNumberOfMso() {
        val msoCount = 2
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateNumberOfMso(msoCount)

        assertEquals(viewModel.screenState.value, AddSelfSignedScreenState(numberOfMso = msoCount))
    }

    @Test
    fun updateNumberOfMsoInvalidValue() {
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateNumberOfMso(1)
        viewModel.updateNumberOfMso(0)
        viewModel.updateNumberOfMso(-1)

        assertEquals(viewModel.screenState.value, AddSelfSignedScreenState(numberOfMso = 1))
    }

    @Test
    fun updateMaxUseOfMso() {
        val maxMsoUsages = 3
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateMaxUseOfMso(maxMsoUsages)

        assertEquals(viewModel.screenState.value, AddSelfSignedScreenState(maxUseOfMso = maxMsoUsages))
    }

    @Test
    fun updateMaxUseOfMsoInvalidValue() {
        val viewModel = AddSelfSignedViewModel(savedStateHandle)

        viewModel.updateMaxUseOfMso(1)
        viewModel.updateMaxUseOfMso(0)
        viewModel.updateMaxUseOfMso(-1)

        assertEquals(viewModel.screenState.value, AddSelfSignedScreenState(maxUseOfMso = 1))
    }
}