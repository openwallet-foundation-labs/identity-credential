import CryptoKit
import Foundation
import Security

import X509
import SwiftASN1

@objc public class SwiftBridge : NSObject {
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
}

