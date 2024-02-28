package com.android.identity.credentialtype

import com.android.identity.credentialtype.knowntypes.DrivingLicense
import org.junit.Test

class TestCredentialTypeRepository {

    @Test
    fun testCredentialTypeRepositoryDrivingLicense() {
        val credentialTypeRepository = CredentialTypeRepository()
        credentialTypeRepository.addCredentialType(DrivingLicense.getCredentialType())
        val credentialTypes = credentialTypeRepository.credentialTypes
        assert(credentialTypes.count() == 1)
        assert(credentialTypes[0].displayName == "Driving License")
        assert(credentialTypes[0].mdocCredentialType?.docType == "org.iso.18013.5.1.mDL")
        assert(credentialTypes[0].vcCredentialType?.type == "Iso18013DriversLicenseCredential")
        assert(
            credentialTypes[0].mdocCredentialType?.namespaces?.iterator()
                ?.next()?.key == "org.iso.18013.5.1"
        )
        assert(
            credentialTypes[0].mdocCredentialType?.namespaces?.iterator()
                ?.next()?.value?.namespace == "org.iso.18013.5.1"
        )
        assert(
            credentialTypes[0].mdocCredentialType?.namespaces?.get("org.iso.18013.5.1")?.dataElements?.get(
                "family_name"
            )?.attribute?.type == CredentialAttributeType.String
        )
        assert(
            credentialTypes[0].mdocCredentialType?.namespaces?.values?.toList()
                ?.last()?.namespace == "org.iso.18013.5.1.aamva"
        )
        assert(
            credentialTypes[0].mdocCredentialType?.namespaces?.get("org.iso.18013.5.1.aamva")?.dataElements?.get(
                "domestic_driving_privileges"
            )?.attribute?.type == CredentialAttributeType.ComplexType
        )


    }
}