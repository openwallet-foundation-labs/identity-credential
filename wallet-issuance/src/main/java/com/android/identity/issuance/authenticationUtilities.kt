package com.android.identity.issuance

import com.android.identity.crypto.Certificate
import com.android.identity.crypto.CertificateChain
import com.android.identity.crypto.javaX509Certificate
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.append
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence

private val salt = byteArrayOf((0xe7).toByte(), 0x7c, (0xf8).toByte(), (0xec).toByte())

const val KEY_DESCRIPTION_OID: String = "1.3.6.1.4.1.11129.2.1.17"

fun authenticationMessage(clientId: String, nonce: ByteString): ByteString {
    val buffer = ByteStringBuilder()
    buffer.append(salt)
    buffer.append(clientId.toByteArray())
    buffer.append(nonce)
    return buffer.toByteString()
}

fun extractAttestationSequence(chain: CertificateChain): ASN1Sequence {
    val extension = chain.certificates[0].javaX509Certificate.getExtensionValue(KEY_DESCRIPTION_OID)
    val asn1InputStream = ASN1InputStream(extension)
    val derSequenceBytes = (asn1InputStream.readObject() as ASN1OctetString).octets
    val seqInputStream = ASN1InputStream(derSequenceBytes)
    return seqInputStream.readObject() as ASN1Sequence
}

fun validateKeyAttestation(chain: CertificateChain, clientId: String) {
    // TODO: use AndroidAttestationExtensionParser.kt instead once it is available
    val last = chain.certificates.lastIndex - 1
    for (i in 1..last) {
        if (!chain.certificates[i - 1].verify((chain.certificates[i]))) {
            throw IllegalArgumentException("Key attestation error: invalid chain")
        }
    }
    if (!chain.certificates[last].verify(androidRootCertificate)) {
        throw IllegalArgumentException("Key attestation error: root")
    }
    val seq = extractAttestationSequence(chain)
    if (clientId != String(ASN1OctetString.getInstance(seq.getObjectAt(4)).octets)) {
        throw IllegalArgumentException("Key attestation error: clientId ")
    }
}

private val androidRootCertificate = Certificate.fromPem("""-----BEGIN CERTIFICATE-----
MIIFHDCCAwSgAwIBAgIJANUP8luj8tazMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNVBAUTEGY5MjAw
OWU4NTNiNmIwNDUwHhcNMTkxMTIyMjAzNzU4WhcNMzQxMTE4MjAzNzU4WjAbMRkwFwYDVQQFExBm
OTIwMDllODUzYjZiMDQ1MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHs
K7Qui8xUFmOr75gvMsd/dTEDDJdSSxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5jlRfd
nJLmN0pTy/4lj4/7tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL
/ggjnar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73XpXyTqRxB/M0n1n/W9nGqC4FSYa04
T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVWutHQoVJYnFPlXTcHYvASLu+R
hhsbDmxMgJJ0mcDpvsC4PjvB+TxywElgS70vE0XmLD+OJtvsBslHZvPBKCOdT0MS+tgSOIfga+z1
Z1g7+DVagf7quvmag8jfPioyKvxnK/EgsTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgp
Zrt3i5MIlCaY504LzSRiigHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6
tUXHI/+MRPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9EaDK8
Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5UmAGMCAwEAAaNjMGEw
HQYDVR0OBBYEFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMB8GA1UdIwQYMBaAFDZh4QB8iAUJUYtEbEf/
GkzJ6k8SMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgIEMA0GCSqGSIb3DQEBCwUAA4IC
AQBOMaBc8oumXb2voc7XCWnuXKhBBK3e2KMGz39t7lA3XXRe2ZLLAkLM5y3J7tURkf5a1SutfdOy
XAmeE6SRo83Uh6WszodmMkxK5GM4JGrnt4pBisu5igXEydaW7qq2CdC6DOGjG+mEkN8/TA6p3cno
L/sPyz6evdjLlSeJ8rFBH6xWyIZCbrcpYEJzXaUOEaxxXxgYz5/cTiVKN2M1G2okQBUIYSY6bjEL
4aUN5cfo7ogP3UvliEo3Eo0YgwuzR2v0KR6C1cZqZJSTnghIC/vAD32KdNQ+c3N+vl2OTsUVMC1G
iWkngNx1OO1+kXW+YTnnTUOtOIswUP/Vqd5SYgAImMAfY8U9/iIgkQj6T2W6FsScy94IN9fFhE1U
tzmLoBIuUFsVXJMTz+Jucth+IqoWFua9v1R93/k98p41pjtFX+H8DslVgfP097vju4KDlqN64xV1
grw3ZLl4CiOe/A91oeLm2UHOq6wn3esB4r2EIQKb6jTVGu5sYCcdWpXr0AUVqcABPdgL+H7qJguB
w09ojm6xNIrw2OocrDKsudk/okr/AwqEyPKw9WnMlQgLIKw1rODG2NvU9oR3GVGdMkUBZutL8VuF
kERQGt6vQ2OCw0sV47VMkuYbacK/xyZFiRcrPJPb41zgbQj9XAEyLKCHex0SdDrx+tWUDqG8At2J
HA==
-----END CERTIFICATE-----
""")