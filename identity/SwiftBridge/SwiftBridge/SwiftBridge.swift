import CryptoKit
import Foundation
import Security
import LocalAuthentication
import DeviceCheck

@objc public class SwiftBridge : NSObject {
    @objc(sha1:) public class func sha1(data: Data) -> Data {
        let hashed = Insecure.SHA1.hash(data: data)
        return Data(hashed)
    }

    @objc(sha256:) public class func sha256(data: Data) -> Data {
        let hashed = SHA256.hash(data: data)
        return Data(hashed)
    }
    
    @objc(sha384:) public class func sha384(data: Data) -> Data {
        let hashed = SHA384.hash(data: data)
        return Data(hashed)
    }
    
    @objc(sha512:) public class func sha512(data: Data) -> Data {
        let hashed = SHA512.hash(data: data)
        return Data(hashed)
    }
    
    @objc(hmacSha256: :) public class func hmacSha256(key: Data, data: Data) -> Data {
        let symmetricKey = SymmetricKey(data: key)
        let mac = HMAC<SHA256>.authenticationCode(for: data, using: symmetricKey)
        return Data(mac)
    }
    
    @objc(hmacSha384: :) public class func hmacSha384(key: Data, data: Data) -> Data {
        let symmetricKey = SymmetricKey(data: key)
        let mac = HMAC<SHA384>.authenticationCode(for: data, using: symmetricKey)
        return Data(mac)
    }
    
    @objc(hmacSha512: :) public class func hmacSha512(key: Data, data: Data) -> Data {
        let symmetricKey = SymmetricKey(data: key)
        let mac = HMAC<SHA512>.authenticationCode(for: data, using: symmetricKey)
        return Data(mac)
    }
    
    @objc(aesGcmEncrypt: : :) public class func aesGcmEncrypt(key: Data, plainText: Data, nonce: Data) -> Data {
        let symmetricKey = SymmetricKey(data: key)
        let sealedBox = try! AES.GCM.seal(plainText, using: symmetricKey, nonce: AES.GCM.Nonce(data: nonce))
        var ret = sealedBox.ciphertext
        ret.append(sealedBox.tag)
        return ret
    }
    
    @objc(aesGcmDecrypt: : :) public class func aesGcmDecrypt(key: Data, cipherText: Data, nonce: Data) -> Data? {
        let symmetricKey = SymmetricKey(data: key)
        var combined = nonce
        combined.append(cipherText)
        let sealedBox = try! AES.GCM.SealedBox(combined: combined)
        do {
            return try AES.GCM.open(sealedBox, using: symmetricKey)
        } catch {
            return nil
        }
    }
    
    @objc(hkdf: : : : :) public class func hkdf(hashLen: Int, ikm: Data, salt: Data, info: Data, size: Int) -> Data? {
        guard #available(iOS 14.0, *) else {
            return nil
        }
        let inputKeyMaterial = SymmetricKey(data: ikm)
        let res: SymmetricKey
        switch (hashLen) {
        case 32:
            res = HKDF<SHA256>.deriveKey(
                inputKeyMaterial: inputKeyMaterial,
                salt: salt,
                info: info,
                outputByteCount: size
            )
            break
        case 48:
            res = HKDF<SHA384>.deriveKey(
                inputKeyMaterial: inputKeyMaterial,
                salt: salt,
                info: info,
                outputByteCount: size
            )
            break
        case 64:
            res = HKDF<SHA512>.deriveKey(
                inputKeyMaterial: inputKeyMaterial,
                salt: salt,
                info: info,
                outputByteCount: size
            )
            break
        default:
            return nil
        }
        return res.withUnsafeBytes {
            return Data(Array($0))
        }
    }
    
    static let CURVE_P256 = 1
    static let CURVE_P384 = 2
    static let CURVE_P521 = 3
    
    @objc(createEcPrivateKey:) public class func createEcPrivateKey(curve: Int) -> Array<Data> {
        switch (curve) {
        case CURVE_P256:
            let key = P256.Signing.PrivateKey.init(compactRepresentable: false)
            return [Data(key.rawRepresentation), Data(key.publicKey.rawRepresentation)]
        case CURVE_P384:
            let key = P384.Signing.PrivateKey.init(compactRepresentable: false)
            return [Data(key.rawRepresentation), Data(key.publicKey.rawRepresentation)]
        case CURVE_P521:
            let key = P521.Signing.PrivateKey.init(compactRepresentable: false)
            return [Data(key.rawRepresentation), Data(key.publicKey.rawRepresentation)]
        default:
            return []
        }
    }
    
    static let PURPOSE_SIGNING = 1
    static let PURPOSE_KEY_AGREEMENT = 2
    
    static let ACCESS_CONTROL_DEVICE_PASSCODE = 1
    static let ACCESS_CONTROL_BIOMETRY_CURRENT_SET = 2
    static let ACCESS_CONTROL_BIOMETRY_ANY = 4
    static let ACCESS_CONTROL_USER_PRESENCE = 8
    
    @objc(secureEnclaveCreateEcPrivateKey: :) public class func secureEnclaveCreateEcPrivateKey(purposes: Int, accessControlCreateFlags: Int) -> Array<Data> {
        
        let authContext = LAContext()
        
        var flags = SecAccessControlCreateFlags([.privateKeyUsage])
        if (accessControlCreateFlags & ACCESS_CONTROL_DEVICE_PASSCODE != 0) {
            flags.insert(.devicePasscode)
        }
        if (accessControlCreateFlags & ACCESS_CONTROL_BIOMETRY_CURRENT_SET != 0) {
            flags.insert(.biometryCurrentSet)
        }
        if (accessControlCreateFlags & ACCESS_CONTROL_USER_PRESENCE != 0) {
            flags.insert(.userPresence)
        }
        
        var error: Unmanaged<CFError>?
        guard let accessControl = SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            flags,
            &error
        ) else {
            return []
        }
        
        if (purposes & PURPOSE_SIGNING != 0) {
            guard let key = try? SecureEnclave.P256.Signing.PrivateKey.init(
                accessControl: accessControl,
                authenticationContext: authContext
            ) else {
                return []
            }
            return [key.dataRepresentation, Data(key.publicKey.rawRepresentation)]
        } else {
            guard let key = try? SecureEnclave.P256.KeyAgreement.PrivateKey.init(
                accessControl: accessControl,
                authenticationContext: authContext
            ) else {
                return []
            }
            return [key.dataRepresentation, Data(key.publicKey.rawRepresentation)]
        }
    }
    
    @objc(secureEnclaveEcSign: : :) public class func secureEnclaveEcSign(
        keyBlob: Data, dataToSign: Data, authContext: LAContext?) -> Data? {
            
            let key = try! SecureEnclave.P256.Signing.PrivateKey(
                dataRepresentation: keyBlob,
                authenticationContext: authContext)
            do {
                let signature = try key.signature(for: dataToSign)
                return Data(signature.rawRepresentation)
            } catch {
                return nil
            }
        }
    
    @objc(secureEnclaveEcKeyAgreement: : :) public class func secureEnclaveEcKeyAgreement(
        keyBlob: Data, otherPublicKeyRepresentation: Data, authContext: LAContext?) -> Data? {
            
        let key = try! SecureEnclave.P256.KeyAgreement.PrivateKey(
            dataRepresentation: keyBlob,
            authenticationContext: authContext)
        let otherKey = try! P256.KeyAgreement.PublicKey(rawRepresentation: otherPublicKeyRepresentation)
        do {
            let sharedSecret = try key.sharedSecretFromKeyAgreement(with: otherKey)
            return sharedSecret.withUnsafeBytes { return Data(Array($0)) }
        } catch {
            return nil
        }
    }

    @objc(ecPublicKeyToPem: :) public class func ecPublicKeyToPem(curve: Int, rawRepresentation: Data) -> String? {
        guard #available(iOS 14.0, *) else {
            return nil
        }
        switch (curve) {
        case CURVE_P256:
            let key = try! P256.Signing.PublicKey(rawRepresentation: rawRepresentation)
            return key.pemRepresentation
        case CURVE_P384:
            let key = try! P384.Signing.PublicKey(rawRepresentation: rawRepresentation)
            return key.pemRepresentation
        case CURVE_P521:
            let key = try! P521.Signing.PublicKey(rawRepresentation: rawRepresentation)
            return key.pemRepresentation
        default:
            return ""
        }
    }

    @objc(ecPublicKeyFromPem: :) public class func ecPublicKeyFromPem(curve: Int, pemRepresentation: String) -> Data? {
        guard #available(iOS 14.0, *) else {
            return nil
        }
        switch (curve) {
        case CURVE_P256:
            let key = try! P256.Signing.PublicKey(pemRepresentation: pemRepresentation)
            return Data(key.rawRepresentation)
        case CURVE_P384:
            let key = try! P384.Signing.PublicKey(pemRepresentation: pemRepresentation)
            return Data(key.rawRepresentation)
        case CURVE_P521:
            let key = try! P521.Signing.PublicKey(pemRepresentation: pemRepresentation)
            return Data(key.rawRepresentation)
        default:
            return nil
        }
    }

    @objc(ecPrivateKeyToPem: :) public class func ecPrivateKeyToPem(curve: Int, rawRepresentation: Data) -> String? {
        guard #available(iOS 14.0, *) else {
            return nil
        }
        switch (curve) {
        case CURVE_P256:
            let key = try! P256.Signing.PrivateKey(rawRepresentation: rawRepresentation)
            return key.pemRepresentation
        case CURVE_P384:
            let key = try! P384.Signing.PrivateKey(rawRepresentation: rawRepresentation)
            return key.pemRepresentation
        case CURVE_P521:
            let key = try! P521.Signing.PrivateKey(rawRepresentation: rawRepresentation)
            return key.pemRepresentation
        default:
            return ""
        }
    }

    @objc(ecPrivateKeyFromPem: :) public class func ecPrivateKeyFromPem(curve: Int, pemRepresentation: String) -> Data? {
        guard #available(iOS 14.0, *) else {
            return nil
        }
        switch (curve) {
        case CURVE_P256:
            let key = try! P256.Signing.PrivateKey(pemRepresentation: pemRepresentation)
            return Data(key.rawRepresentation)
        case CURVE_P384:
            let key = try! P384.Signing.PrivateKey(pemRepresentation: pemRepresentation)
            return Data(key.rawRepresentation)
        case CURVE_P521:
            let key = try! P521.Signing.PrivateKey(pemRepresentation: pemRepresentation)
            return Data(key.rawRepresentation)
        default:
            return nil
        }
    }

    @objc(ecSign: : :) public class func ecSign(privateKeyCurve: Int, privateKeyRepresentation: Data, dataToSign: Data) -> Data? {
        switch (privateKeyCurve) {
        case CURVE_P256:
            let key = try! P256.Signing.PrivateKey(rawRepresentation: privateKeyRepresentation)
            let signature = try! key.signature(for: dataToSign)
            return Data(signature.rawRepresentation)
        case CURVE_P384:
            let key = try! P384.Signing.PrivateKey(rawRepresentation: privateKeyRepresentation)
            let signature = try! key.signature(for: dataToSign)
            return Data(signature.rawRepresentation)
        case CURVE_P521:
            let key = try! P521.Signing.PrivateKey(rawRepresentation: privateKeyRepresentation)
            let signature = try! key.signature(for: dataToSign)
            return Data(signature.rawRepresentation)
        default:
            return nil
        }
    }

    @objc(ecVerifySignature: : : :) public class func ecVerifySignature(publicKeyCurve: Int, publicKeyRepresentation: Data, dataThatWasSigned: Data, signature: Data) -> Bool {
        switch (publicKeyCurve) {
        case CURVE_P256:
            let key = try! P256.Signing.PublicKey(rawRepresentation: publicKeyRepresentation)
            let ecdsaSignature = try! P256.Signing.ECDSASignature(rawRepresentation: signature)
            return key.isValidSignature(ecdsaSignature, for: dataThatWasSigned)
        case CURVE_P384:
            let key = try! P384.Signing.PublicKey(rawRepresentation: publicKeyRepresentation)
            let ecdsaSignature = try! P384.Signing.ECDSASignature(rawRepresentation: signature)
            return key.isValidSignature(ecdsaSignature, for: dataThatWasSigned)
        case CURVE_P521:
            let key = try! P521.Signing.PublicKey(rawRepresentation: publicKeyRepresentation)
            let ecdsaSignature = try! P521.Signing.ECDSASignature(rawRepresentation: signature)
            return key.isValidSignature(ecdsaSignature, for: dataThatWasSigned)
        default:
            return false
        }
    }

    @objc(ecKeyAgreement: : :) public class func ecKeyAgreement(privateKeyCurve: Int, privateKeyRepresentation: Data, otherPublicKeyRepresentation: Data) -> Data? {
        switch (privateKeyCurve) {
        case CURVE_P256:
            let key = try! P256.KeyAgreement.PrivateKey(rawRepresentation: privateKeyRepresentation)
            let otherKey = try! P256.KeyAgreement.PublicKey(rawRepresentation: otherPublicKeyRepresentation)
            let sharedSecret = try! key.sharedSecretFromKeyAgreement(with: otherKey)
            return sharedSecret.withUnsafeBytes { return Data(Array($0)) }
        case CURVE_P384:
            let key = try! P384.KeyAgreement.PrivateKey(rawRepresentation: privateKeyRepresentation)
            let otherKey = try! P384.KeyAgreement.PublicKey(rawRepresentation: otherPublicKeyRepresentation)
            let sharedSecret = try! key.sharedSecretFromKeyAgreement(with: otherKey)
            return sharedSecret.withUnsafeBytes { return Data(Array($0)) }
        case CURVE_P521:
            let key = try! P521.KeyAgreement.PrivateKey(rawRepresentation: privateKeyRepresentation)
            let otherKey = try! P521.KeyAgreement.PublicKey(rawRepresentation: otherPublicKeyRepresentation)
            let sharedSecret = try! key.sharedSecretFromKeyAgreement(with: otherKey)
            return sharedSecret.withUnsafeBytes { return Data(Array($0)) }
        default:
            return nil
        }
    }

    @objc(hpkeEncrypt: : :) public class func hpkeEncrypt(receiverPublicKeyRepresentation: Data, plainText: Data, aad: Data) -> Array<Data> {
        guard #available(iOS 17.0, *) else {
            return []
        }
        let receiverKey = try! P256.KeyAgreement.PublicKey(rawRepresentation: receiverPublicKeyRepresentation)
        var sender = try! HPKE.Sender(recipientKey: receiverKey, ciphersuite: HPKE.Ciphersuite.P256_SHA256_AES_GCM_256, info: Data())
        let cipherText = try! sender.seal(plainText, authenticating: aad)
        return [sender.encapsulatedKey, cipherText]
    }

    @objc(hpkeDecrypt: : : :) public class func hpkeDecrypt(receiverPrivateKeyRepresentation: Data, cipherText: Data, aad: Data, encapsulatedPublicKey: Data) -> Data? {
        guard #available(iOS 17.0, *) else {
            return nil
        }
        let receiverKey = try! P256.KeyAgreement.PrivateKey(rawRepresentation: receiverPrivateKeyRepresentation)
        var receiver = try! HPKE.Recipient(privateKey: receiverKey, ciphersuite: HPKE.Ciphersuite.P256_SHA256_AES_GCM_256, info: Data(), encapsulatedKey: encapsulatedPublicKey)
        let plainText = try! receiver.open(cipherText, authenticating: aad)
        return plainText
    }

    @objc(x509CertGetKey:) public class func x509CertGetKey(encodedX509Cert: Data) -> Data? {
        let certificate = SecCertificateCreateWithData(nil, encodedX509Cert as CFData)
        if (certificate == nil) {
            return nil
        }
        let key = SecCertificateCopyKey(certificate!)
        if (key == nil) {
            return nil
        }
        let data = SecKeyCopyExternalRepresentation(key!, nil)
        return data as Data?
    }
    
    @objc(generateDeviceAttestation::) public class func generateDeviceAttestation(
        dataHash: Data,
        completionHandler: @escaping (String?, Data?, Error?) -> Void
    ) -> Void {
        let attestService = DCAppAttestService.shared
        guard attestService.isSupported == true else {
            completionHandler(nil, nil, NSError(domain: "com.android.identity", code: 1, userInfo: [
                "message": "This device does not support attestation"
            ]))
            return
        }
        
        guard dataHash.count == 32 else {
            print("Invalid dataHash size")
            completionHandler(nil, nil, NSError(domain: "com.android.identity", code: 2, userInfo: [
                "message": "dataHash length must be 32 bytes"
            ]))
            return
        }
        
        attestService.generateKey { keyId, err in
            guard err == nil else {
                completionHandler(nil, nil, err)
                return
            }
                        
            attestService.attestKey(keyId!, clientDataHash: dataHash) { attestation, err in
                guard err == nil else {
                    completionHandler(nil, nil, err)
                    return
                }
                completionHandler(keyId!, attestation, nil)
            }
        }
    }
    
    @objc(generateDeviceAssertion:::) public class func generateDeviceAssertion(
        keyId: String,
        dataHash: Data,
        completionHandler: @escaping (Data?, Error?) -> Void
    ) -> Void {
        DCAppAttestService.shared.generateAssertion(
            keyId,
            clientDataHash: dataHash,
            completionHandler: completionHandler
        )
    }

    @objc(verifySignature::::) public class func verifySignature(
        encodedCertificate: Data,
        message: Data,
        signatureAlgorithmOid: String,
        signature: Data
    ) -> Error? {
        let certificate = SecCertificateCreateWithData(nil, encodedCertificate as CFData)
        if (certificate == nil) {
            return NSError(domain: "com.android.identity", code: 2, userInfo: [
                "message": "error parsing certificate"
            ])
        }
        let key = SecCertificateCopyKey(certificate!)
        if (key == nil) {
            return NSError(domain: "com.android.identity", code: 2, userInfo: [
                "message": "error extracting certificate key"
            ])
        }
        // TODO: add mappings for more signature algorithms
        var algorithm: SecKeyAlgorithm
        switch (signatureAlgorithmOid) {
        case "1.2.840.10045.4.3.2":
            algorithm = SecKeyAlgorithm.ecdsaSignatureMessageX962SHA256
        case "1.2.840.10045.4.3.3":
            algorithm = SecKeyAlgorithm.ecdsaSignatureMessageX962SHA384
        case "1.2.840.10045.4.3.4":
            algorithm = SecKeyAlgorithm.ecdsaSignatureMessageX962SHA512
        case "1.2.840.113549.1.1.11":
            algorithm = SecKeyAlgorithm.rsaSignatureMessagePKCS1v15SHA256
        default:
            return NSError(domain: "com.android.identity", code: 2, userInfo: [
                "message": "unknown signature algorithm: " + signatureAlgorithmOid
            ])
        }
        var error: Unmanaged<CFError>? = nil
        guard SecKeyVerifySignature(key!, algorithm, message as CFData, signature as CFData, &error) else {
            return error!.takeRetainedValue() as Error
        }
        return nil
    }
}

