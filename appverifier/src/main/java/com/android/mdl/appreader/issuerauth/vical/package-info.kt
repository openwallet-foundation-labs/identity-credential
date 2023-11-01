/**
 * This library provides all necessary functionality to build, encode and decode a VICAL signed certificate lists.
 * It only provides the technical means to do so; it does not provide any of the system / infrastructure required to build a functional VICAL.
 * The library does support all of the fields defined in ISO/IEC FDIS 18013-5, 2021,
 *
 *
 * The VICAL consists of two main structures in classes with the same name:
 *
 *  1. The `Vical` itself, which contains the meta information about the VICAL
 *  1. The `CertificateInfo` which contains the meta information about the certificate as well as the certificate itself
 *
 * These classes both have three internal classes each:
 *
 *  1. A `Builder` class which can be used to create or copy and adjust an existing `Vical` or `CertificateInfo` instance.
 *  1. An `Encoder` class to encode the structures to a CBOR branch
 *  1. A `Decoder` class to decode the structures from a CBOR branch
 *
 *
 *
 * Notes:
 *
 *  * All the fields are present and can be retrieved from the structures. The structures themselves are immutable, you will need to use a `Builder` class to create an new Vical instance from an existing one. For this reason the `Vical.Builder` class has a copy constructor.
 *  * The library doesn't contain any functional components; it's a library, not a component. That also means that e.g. the increase of the optional `vicalIssueID` is not performed automatically.
 *
 */
package com.android.mdl.appreader.issuerauth.vical
