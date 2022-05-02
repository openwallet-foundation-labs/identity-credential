# Identity Credential

This repository contains libraries and applications related to the
[Android Identity Credential API](https://developer.android.com/reference/android/security/identity/IdentityCredentialStore)
provided in the Android Framework as of Android 11 as well as
[ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html)
and related standards.

## Android Identity Credential Library

This library has two goals. The first goal is to provide a compatibility-layer for the
Android Identity Credential API when running on a device where this API is not implemented (for
example, a device running an older Android version). This is achieved by using
[Android Keystore APIs](https://developer.android.com/training/articles/keystore)
when the hardware-backed Identity Credential APIs are not available.

The other goal of the library is to provide high-level primitives that any *mdoc* or
*mdoc reader* application is anticipated to need.

### Versioning and releases

TODO: Write me.

### API Stability

TODO: Write me.

### Getting involved

TODO: Write me.

## Reference Applications

This repository also contains two applications to show how to use the library.
This includes a prover app (*mdoc*) and a reader app (*mdoc reader*). These
applications are not meant to be production quality and are provided only to
demonstrate how the library APIs work and best practices. The applications
implement the published version of ISO/IEC 18013-5:2021.

Currently hard-coded data is used -- the *mdoc* application contains an mDL
(document type `org.iso.18013.5.1.mDL`), a vaccination certificate (document
type `org.micov.1`), and a vehicle registration (document type `nl.rdw.mekb.1`).
The code also has experimental support for provisioning, including dynamically
obtaining MSOs, PII updates, de-provisioning, server protocols, and an
experimental provisioning server.

# Support

This is not an officially supported Google product.
