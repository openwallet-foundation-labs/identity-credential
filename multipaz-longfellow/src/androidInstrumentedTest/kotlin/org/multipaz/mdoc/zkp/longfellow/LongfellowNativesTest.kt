package org.multipaz.mdoc.zkp.longfellow

import org.junit.Assert.assertEquals
import org.junit.Test

class LongfellowNativesTest {
    @Test
    fun testFullVerificationFlow_success() {
        val verifierCodeEnum = LongfellowNativeTestsCommon.runFullVerification(
            MdocTestDataProvider.getAgeOver18Attributes(true),
            MdocTestDataProvider.getAgeOver18Attributes(true)
        )
        assertEquals(VerifierCodeEnum.MDOC_VERIFIER_SUCCESS, verifierCodeEnum)
    }
}
