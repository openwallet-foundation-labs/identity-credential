package com.android.identity.credentialtype

import org.junit.Test

class TestCredentialTypeRepository {

    @Test
    fun testDrivingLicense(){
        for (namespace in DrivingLicense.getCredentialType().mdocCredentialType?.namespaces!!){
            for (dataElement in namespace.dataElements){
                println("${dataElement.attribute.identifier}: ${dataElement.attribute.displayName}")
            }
        }
    }
}