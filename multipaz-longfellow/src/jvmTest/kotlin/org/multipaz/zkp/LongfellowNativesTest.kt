package org.multipaz.zkp

import org.multipaz.mdoc.zkp.longfellow.VerifierCodeEnum
import kotlin.test.assertEquals
import org.junit.Test
import org.multipaz.mdoc.zkp.longfellow.LongfellowNativeTestsCommon

class LongfellowNativesTest {
    @Test
    fun testFullVerificationFlow_success() {
        val verifierCodeEnum = LongfellowNativeTestsCommon.runFullVerification()
        assertEquals(VerifierCodeEnum.MDOC_VERIFIER_SUCCESS, verifierCodeEnum)
    }
}
