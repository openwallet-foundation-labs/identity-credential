# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.93.0] - Not yet released, expected end of July 2025
- TODO: add items as needed

## [0.92.1] - 2025-06-27
Changes since Multipaz 0.92.1 include
- Fix BLE GATT on older Android devices.
- Pass the full JWS for OpenID4VCI key attestations, not just the body of the JWT.

## [0.92.0] - 2025-06-25
Changes since Multipaz 0.91 include:
- Face Detection routines and Selfie detection, using MLKit
- BLE GATT data race fixes.
- Update SD-JWT implementation to properly handle non-top-level disclosures.
- Update JWE, JWS routines to work on Kotlin Multiplatform and drop dependency on Nimbus library.
- Update to latest W3C Digital Credential API.
- OpenID4VP Draft 29 support.
- OpenID4VCI interoperability fixes.
- Removal of BouncyCastle Dependency.
- Consent Prompt enhancements.
- Exporting of Android services/activities to make W3C DC API and NFC Engagement easier to implement.
- Fix for Annex C implementation discovered during interop testing ("Response" -> "response").
- Migration of all server code to use Ktor environment, making it easier to deploy.
- Stand up at https://verifier.multipaz.org, https://issuer.multipaz.org, and https://csa.multipaz.org.
- New "Raw DCQL" box in online verifier code.
- Rework QR code generation and scanning and drop some dependencies.
- Generate and publish Multipaz.xcframework w/ Package.swift file for easy consumption in Swift projects.

## [0.91.0] - 2025-05-30
Changes since Multipaz 0.90 include
- Fix generated reader auth certificates to contain required extensions from 
  ISO/IEC 18013-5:2021 Annex B.1.7.
- Multiplatform Camera composable in `multipaz-compose` library.
- OpenID4VP draft 24 updates, based on interoperability testing.
- OpenID4VCI updates.
- Preliminary ZKP support.
- Preliminary support for scanning barcodes, using MLKit.
- Updated RPC schema hash calculations to better support with different Kotlin versions.
- Better support for semantic versioning.
- New icons.
- New https://apps.multipaz.org/ website with prebuilt APKs.

## [0.90] - 2025-03-26
This is the first release with the CHANGELOG.md file and also the first release using the new
Multipaz name. Main changes since the Identity Credential version 202411.1 include:
- The project has been renamed to Multipaz and all package names have been
  changed from `com.android.identity` to `org.multipaz`.
- New `multipaz-models` and `multipaz-compose` libraries
- Removal of `appholder` module.
- Conversion of DocumentStore, Document, Credential to use suspend methods.
- Conversion of SecureArea to use suspend methods.
- Introduction of new PromptModel to handle interaction with the user for
  asynchronous methods on SecureArea and NFC tag reading.
- New multiplatform ASN.1 and X.509 code.
- New multiplatform ISO/IEC 18013-5 proximity presentment stack.
- New multiplatform NFC stack and NFC tag reader abstraction.
- New multiplatform storage backend.
- New presentment model and composable.
- New HTML/Markdown, Notifications, LockScreen multiplatform composables.
