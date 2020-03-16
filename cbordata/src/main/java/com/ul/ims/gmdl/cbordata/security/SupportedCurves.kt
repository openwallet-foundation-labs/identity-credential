/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.ul.ims.gmdl.cbordata.security

import co.nstant.`in`.cbor.model.NegativeInteger
import co.nstant.`in`.cbor.model.UnsignedInteger
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.P256
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.P384
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.P521
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.brainpoolP256r1
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.brainpoolP320r1
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.brainpoolP384r1
import com.ul.ims.gmdl.cbordata.security.CoseKey.Companion.brainpoolP512r1
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.math.ec.ECCurve
import org.bouncycastle.math.ec.custom.sec.SecP256R1Curve
import org.bouncycastle.math.ec.custom.sec.SecP384R1Curve
import org.bouncycastle.math.ec.custom.sec.SecP521R1Curve

object SupportedCurves {

    private const val secP256R1Curve = "P-256"
    private const val secP384R1Curve = "P-384"
    private const val secP521R1Curve = "P-521"

    val curveMap1 = hashMapOf<UnsignedInteger, ECCurve>(
        P256 to SecP256R1Curve(),
        P384 to SecP384R1Curve(),
        P521 to SecP521R1Curve()
//        CoseKey.X25519 to X25519.precompute(),
//        CoseKey.X448 to X448.precompute(),
//        CoseKey.Ed25519 to Ed25519.precompute(),
//        CoseKey.Ed448 to Ed448.precompute()
    )

    val coseKeyCurveIdentifiers = hashMapOf<NegativeInteger, Any>(
        brainpoolP256r1 to ECNamedCurveTable.getParameterSpec("brainpoolP256r1"),
        brainpoolP320r1 to ECNamedCurveTable.getParameterSpec("brainpoolP320r1"),
        brainpoolP384r1 to ECNamedCurveTable.getParameterSpec("brainpoolP384r1"),
        brainpoolP512r1 to ECNamedCurveTable.getParameterSpec("brainpoolP512r1")
    )
}