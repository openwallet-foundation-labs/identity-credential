package org.multipaz.zkp

import org.multipaz.zkp.mdoc.longfellow.VerifierCodeEnum
import kotlin.test.assertEquals
import org.junit.Test

class LongfellowNativesTest {
    @Test
    fun testFullVerificationFlow_success() {
        val verifierCodeEnum = LongfellowNativeTestsCommon.runFullVerification()
        assertEquals(VerifierCodeEnum.MDOC_VERIFIER_SUCCESS, verifierCodeEnum)
    }
}
