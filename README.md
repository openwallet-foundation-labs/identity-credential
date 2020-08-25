# Android mDL Reference Applications

This repository contains Android mDL reference applications. There are
two applications, one for the prover ("mDL") and one for the verifier
("mDL Reader"). These applications are not production quality and are
provided only to demonstrate how the Android Identity Credential APIs
work. The applications implement a draft version of the
[ISO/IEC 18013-5 standard](https://www.iso.org/standard/69084.html).

Currently hard-coded data is used in the prover application. In a future
version, we'll add a way to dynamically provision, update, and
de-provision mDLs from a server-side application.

The prover uses the [Android Identity Credential Jetpack](https://developer.android.com/jetpack/androidx/releases/security#security-identity-credential-1.0.0-alpha01)
to store and present the mDL.

This is not an officially supported Google product.

