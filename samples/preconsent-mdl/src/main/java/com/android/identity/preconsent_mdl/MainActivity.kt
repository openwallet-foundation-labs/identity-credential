/*
 * Copyright (C) 2023 Google LLC
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

package com.android.identity.preconsent_mdl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.android.identity.credential.NameSpacedData
import com.android.identity.internal.Util
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.mso.StaticAuthDataGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.preconsent_mdl.ui.theme.IdentityCredentialTheme
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.EcCurve
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date

class MainActivity : ComponentActivity() {

    companion object {
        private val TAG = "MainActivity"

        const val CREDENTIAL_ID = "mDL_Erika"
        const val AUTH_KEY_DOMAIN = "mdoc"

        const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        const val MDL_NAMESPACE = "org.iso.18013.5.1"
        const val AAMVA_NAMESPACE = "org.iso.18013.5.1.aamva"
    }

    private lateinit var transferHelper: TransferHelper

    private fun provisionCredentials() {
        if (transferHelper.credentialStore.lookupCredential(CREDENTIAL_ID) == null) {
            provisionCredential()
        } else {
            Logger.d(TAG, "Already have credential $CREDENTIAL_ID")
        }
    }

    private fun provisionCredential() {
        val credential = transferHelper.credentialStore.createCredential(CREDENTIAL_ID)

        val baos = ByteArrayOutputStream()
        BitmapFactory.decodeResource(applicationContext.resources, R.drawable.img_erika_portrait)
            .compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val portrait: ByteArray = baos.toByteArray()

        val now = Timestamp.now()
        val expiryDate = Timestamp.ofEpochMilli(now.toEpochMilli() + 5 * 365 * 24 * 3600 * 1000L)

        val credentialData = NameSpacedData.Builder()
            .putEntryString(MDL_NAMESPACE, "given_name", "Erika")
            .putEntryString(MDL_NAMESPACE, "family_name", "Mustermann")
            .putEntryByteString(MDL_NAMESPACE, "portrait", portrait)
            .putEntryNumber(MDL_NAMESPACE, "sex", 2)
            .putEntry(MDL_NAMESPACE, "issue_date", Util.cborEncodeDateTime(now))
            .putEntry(MDL_NAMESPACE, "expiry_date", Util.cborEncodeDateTime(expiryDate))
            .putEntryString(MDL_NAMESPACE, "document_number", "1234567890")
            .putEntryString(MDL_NAMESPACE, "issuing_authority", "State of Utopia")
            .putEntryString(AAMVA_NAMESPACE, "DHS_compliance", "F")
            .putEntryNumber(AAMVA_NAMESPACE, "EDL_credential", 1)
            .putEntryBoolean(MDL_NAMESPACE, "age_over_18", true)
            .putEntryBoolean(MDL_NAMESPACE, "age_over_21", true)
            .build()
        credential.applicationData.setNameSpacedData("credentialData", credentialData)
        credential.applicationData.setString("docType", MDL_DOCTYPE)

        // Create AuthKeys and MSOs, make sure they're valid for a long time
        val timeSigned = now
        val validFrom = now
        val validUntil = Timestamp.ofEpochMilli(validFrom.toEpochMilli() + 365 * 24 * 3600 * 1000L)

        // Create three authentication keys and certify them
        for (n in 0..2) {
            val pendingAuthKey = credential.createPendingAuthenticationKey(
                AUTH_KEY_DOMAIN,
                transferHelper.androidKeystoreSecureArea,
                CreateKeySettings("".toByteArray()),
                null
            )

            // Generate an MSO and issuer-signed data for this authentication key.
            val msoGenerator = MobileSecurityObjectGenerator(
                "SHA-256",
                MDL_DOCTYPE,
                pendingAuthKey.attestation[0].publicKey,
                EcCurve.P256
            )
            msoGenerator.setValidityInfo(timeSigned, validFrom, validUntil, null)
            val randomProvider = SecureRandom()
            val issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
                credentialData,
                randomProvider,
                16,
                null
            )
            for (nameSpaceName in issuerNameSpaces.keys) {
                val digests = MdocUtil.calculateDigestsForNameSpace(
                    nameSpaceName,
                    issuerNameSpaces,
                    "SHA-256"
                )
                msoGenerator.addDigestIdsForNamespace(nameSpaceName, digests)
            }
            val issuerKeyPair: KeyPair = generateIssuingAuthorityKeyPair()
            val issuerCert = getSelfSignedIssuerAuthorityCertificate(issuerKeyPair)

            val mso = msoGenerator.generate()
            val taggedEncodedMso = Util.cborEncode(Util.cborBuildTaggedByteString(mso))
            val issuerCertChain = listOf(issuerCert)
            val encodedIssuerAuth = Util.cborEncode(
                Util.coseSign1Sign(
                    issuerKeyPair.private,
                    "SHA256withECDSA", taggedEncodedMso,
                    null,
                    issuerCertChain
                )
            )

            val issuerProvidedAuthenticationData = StaticAuthDataGenerator(
                MdocUtil.stripIssuerNameSpaces(issuerNameSpaces, null),
                encodedIssuerAuth
            ).generate()

            pendingAuthKey.certify(issuerProvidedAuthenticationData, validFrom, validUntil)
        }
        Logger.d(TAG, "Created credential with name ${credential.name}")
    }

    private fun generateIssuingAuthorityKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec("secp256r1")
        kpg.initialize(ecSpec)
        return kpg.generateKeyPair()
    }

    private fun getSelfSignedIssuerAuthorityCertificate(
        issuerAuthorityKeyPair: KeyPair
    ): X509Certificate {
        val issuer: X500Name = X500Name("CN=State Of Utopia")
        val subject: X500Name = X500Name("CN=State Of Utopia Issuing Authority Signing Key")

        // Valid from now to five years from now.
        val now = Date()
        val kMilliSecsInOneYear = 365L * 24 * 60 * 60 * 1000
        val expirationDate = Date(now.time + 5 * kMilliSecsInOneYear)
        val serial = BigInteger("42")
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            now,
            expirationDate,
            subject,
            issuerAuthorityKeyPair.public
        )
        val signer: ContentSigner = JcaContentSignerBuilder("SHA256withECDSA")
            .build(issuerAuthorityKeyPair.private)
        val certHolder: X509CertificateHolder = builder.build(signer)
        return JcaX509CertificateConverter().getCertificate(certHolder)
    }


    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Logger.d(TAG, "permissionsLauncher ${it.key} = ${it.value}")
                if (!it.value) {
                    Toast.makeText(
                        this,
                        "The ${it.key} permission is required for BLE",
                        Toast.LENGTH_LONG
                    ).show()
                    return@registerForActivityResult
                }
            }
        }

    private val appPermissions: Array<String> =
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        transferHelper = TransferHelper.getInstance(applicationContext)
        provisionCredentials()

        val permissionsNeeded = appPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(
                applicationContext,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            permissionsLauncher.launch(
                permissionsNeeded.toTypedArray()
            )
        }

        setContent {
            IdentityCredentialTheme {
                MainScreen(applicationContext)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(context: Context) {
    val transferHelper = remember { TransferHelper.getInstance(context) }
    val nfcStaticHandoverEnabled =
        remember { mutableStateOf(transferHelper.getNfcStaticHandoverEnabled()) }
    val nfcNegotiatedHandoverEnabled =
        remember { mutableStateOf(transferHelper.getNfcNegotiatedHandoverEnabled()) }
    val bleCentralClientDataTransferEnabled =
        remember { mutableStateOf(transferHelper.getBleCentralClientDataTransferEnabled()) }
    val blePeripheralServerDataTransferEnabled =
        remember { mutableStateOf(transferHelper.getBlePeripheralServerDataTransferEnabled()) }
    val wifiAwareDataTransferEnabled =
        remember { mutableStateOf(transferHelper.getWifiAwareDataTransferEnabled()) }
    val nfcDataTransferEnabled =
        remember { mutableStateOf(transferHelper.getNfcDataTransferEnabled()) }
    val tcpDataTransferEnabled =
        remember { mutableStateOf(transferHelper.getTcpDataTransferEnabled()) }
    val udpDataTransferEnabled =
        remember { mutableStateOf(transferHelper.getUdpDataTransferEnabled()) }
    val l2capEnabled = remember { mutableStateOf(transferHelper.getL2CapEnabled()) }
    val experimentalPsmEnabled =
        remember { mutableStateOf(transferHelper.getExperimentalPsmEnabled()) }
    val debugEnabled = remember { mutableStateOf(transferHelper.getDebugEnabled()) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),

        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                title = {
                    Text(
                        "mDL Preconsent Sample",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = "This app contains an mDL with Preconsent which " +
                                    "will be presented to any mdoc reader, without authentication " +
                                    "or consent. The main purpose of this application is " +
                                    "to evaluate performance.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    SettingSectionTitle(title = "Engagement Settings")
                    SettingToggle(
                        title = "NFC Static Handover",
                        subtitleOn = "NFC Static Handover enabled",
                        subtitleOff = "NFC Static Handover disabled",
                        isChecked = nfcStaticHandoverEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setNfcStaticHandoverEnabled(checked)
                            nfcStaticHandoverEnabled.value = checked
                            transferHelper.setNfcNegotiatedHandoverEnabled(!checked)
                            nfcNegotiatedHandoverEnabled.value = !checked
                        }
                    )
                    SettingToggle(
                        title = "NFC Negotiated Handover",
                        subtitleOn = "NFC Negotiated Handover enabled",
                        subtitleOff = "NFC Negotiated Handover disabled",
                        isChecked = nfcNegotiatedHandoverEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setNfcNegotiatedHandoverEnabled(checked)
                            nfcNegotiatedHandoverEnabled.value = checked
                            transferHelper.setNfcStaticHandoverEnabled(!checked)
                            nfcStaticHandoverEnabled.value = !checked
                        }
                    )
                    SettingSectionTitle(title = "Data Transfer Settings")
                    SettingToggle(
                        title = "BLE mdoc central client data retrieval",
                        subtitleOn = "BLE mdoc central client data retrieval enabled",
                        subtitleOff = "BLE mdoc central client data retrieval disabled",
                        isChecked = bleCentralClientDataTransferEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setBleCentralClientDataTransferEnabled(checked)
                            bleCentralClientDataTransferEnabled.value = checked
                        }
                    )
                    SettingToggle(
                        title = "BLE mdoc peripheral server data retrieval",
                        subtitleOn = "BLE mdoc peripheral server data retrieval enabled",
                        subtitleOff = "BLE mdoc peripheral server data retrieval disabled",
                        isChecked = blePeripheralServerDataTransferEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setBlePeripheralServerDataTransferEnabled(checked)
                            blePeripheralServerDataTransferEnabled.value = checked
                        }
                    )
                    SettingToggle(
                        title = "Wifi Aware data transfer",
                        subtitleOn = "Wifi Aware data transfer enabled",
                        subtitleOff = "Wifi Aware data transfer disabled",
                        isChecked = wifiAwareDataTransferEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setWifiAwareDataTransferEnabled(checked)
                            wifiAwareDataTransferEnabled.value = checked
                        }
                    )
                    SettingToggle(
                        title = "NFC data transfer",
                        subtitleOn = "NFC data transfer enabled",
                        subtitleOff = "NFC data transfer disabled",
                        isChecked = nfcDataTransferEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setNfcDataTransferEnabled(checked)
                            nfcDataTransferEnabled.value = checked
                        }
                    )
                    SettingToggle(
                        title = "TCP data transfer (proprietary)",
                        subtitleOn = "TCP data transfer enabled",
                        subtitleOff = "TCP data transfer disabled",
                        isChecked = tcpDataTransferEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setTcpDataTransferEnabled(checked)
                            tcpDataTransferEnabled.value = checked
                        }
                    )
                    SettingToggle(
                        title = "UDP data transfer (proprietary)",
                        subtitleOn = "UDP data transfer enabled",
                        subtitleOff = "UDP data transfer disabled",
                        isChecked = udpDataTransferEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setUdpDataTransferEnabled(checked)
                            udpDataTransferEnabled.value = checked
                        }
                    )
                    SettingSectionTitle(title = "Options")
                    SettingToggle(
                        title = "Use BLE L2CAP if available",
                        subtitleOn = "BLE L2CAP enabled",
                        subtitleOff = "BLE L2CAP disabled",
                        isChecked = l2capEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setL2CapEnabled(checked)
                            l2capEnabled.value = checked
                        }
                    )
                    SettingToggle(
                        title = "Experimental conveyance of L2CAP PSM",
                        subtitleOn = "Experimental PSM Conveyance enabled",
                        subtitleOff = "Experimental PSM Conveyance disabled",
                        isChecked = experimentalPsmEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setExperimentalPsmEnabled(checked)
                            experimentalPsmEnabled.value = checked
                        }
                    )
                    SettingSectionTitle(title = "Logging")
                    SettingToggle(
                        title = "Show Debug Messages",
                        subtitleOn = "Debug messages enabled",
                        subtitleOff = "Debug messages disabled",
                        isChecked = debugEnabled.value,
                        onCheckedChange = { checked ->
                            transferHelper.setDebugEnabled(checked)
                            debugEnabled.value = checked
                            Logger.isDebugEnabled = checked
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSectionTitle(
    modifier: Modifier = Modifier,
    title: String
) {
    Column(modifier = modifier) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingToggle(
    modifier: Modifier = Modifier,
    title: String,
    subtitleOn: String,
    subtitleOff: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val subtitle = if (isChecked) subtitleOn else subtitleOff
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Switch(
            checked = isChecked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}
