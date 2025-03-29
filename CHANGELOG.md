# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.91] - Not yet released, expected end of May 2025
- TODO: add items as needed

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
