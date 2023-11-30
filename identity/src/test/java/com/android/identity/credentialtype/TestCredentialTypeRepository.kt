package com.android.identity.credentialtype

import org.junit.Test

class TestCredentialTypeRepository {

    @Test
    fun testCredentialTypeRepositoryDrivingLicense() {
        CredentialTypeRepository.addCredentialType(DrivingLicense.getCredentialType())
        val credentialTypes = CredentialTypeRepository.getCredentialTypes()
        assert(credentialTypes.count() == 1)
        assert(credentialTypes[0].displayName == "Driving License")
        assert(credentialTypes[0].mdocCredentialType?.docType == "org.iso.18013.5.1.mDL")
        assert(credentialTypes[0].vcCredentialType?.type == "Iso18013DriversLicenseCredential")
        assert(credentialTypes[0].mdocCredentialType?.namespaces?.get(0)?.namespace == "org.iso.18013.5.1")
        credentialTypes[0].mdocCredentialType?.namespaces?.get(0)?.dataElements?.any {
            it.attribute.identifier == "family_name" &&
                    it.attribute.type == CredentialAttributeType.STRING
        }
            ?.let { assert(it) }
        assert(credentialTypes[0].mdocCredentialType?.namespaces?.get(1)?.namespace == "org.iso.18013.5.1.aamva")
        credentialTypes[0].mdocCredentialType?.namespaces?.get(1)?.dataElements?.any {
            it.attribute.identifier == "domestic_driving_privileges" &&
                    it.attribute.type == CredentialAttributeType.COMPLEXTYPE
        }
            ?.let { assert(it) }

    }
}