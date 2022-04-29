# Android mdoc/mDL Reference Applications

This repository contains two applications, a prover app ("mdoc") and a
verifier app ("mdoc reader"). These applications are not meant to be
production quality and are provided only to demonstrate how these APIs
work and best practices. The applications implement the published version of the
[ISO/IEC 18013-5 standard](https://www.iso.org/standard/69084.html).

Currently hard-coded data is used - the mdoc application contains an mDL
(document type org.iso.18013.5.1.mDL), a vaccination certificate (document
type micov.1), and a vehicle registration (document type nl.rdw.mekb.1).
The code also has experimental support for provisioning, including dynamically
obtaining MSOs, PII updates, de-provisioning, server protocols, and an
experimental provisioning server.

The applications are written to use the [Android Identity Credential Jetpack](https://developer.android.com/reference/androidx/security/identity/package-summary.html)
but currently use a local copy of the Jetpack with additional functionality.
This additional functionality will will be merged into the Jetpack
repository in the near future.

This is not an officially supported Google product.
