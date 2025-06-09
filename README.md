# Multipaz

This repository contains libraries and applications for working with real-world
identity. The initial focus for this work was mdoc/mDL according to [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html)
and related standards but the current scope also include other credential formats and
presentment protocols.

## Multipaz Libraries

The project provides libraries written in [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html):

- `multipaz` provides the core building blocks it works on Android,
  iOS, and in server-side environments. The library includes support 
  for ISO mdoc and IETF SD-JWT VC credential formats and also implements
  proximity presentment using ISO/IEC 18013-5:2021 (for ISO mdoc credentials)
  and presentment to applications using the W3C Digital Credentials API.
- `multipaz-models` is a Kotlin Multiplatform library with stateful models
  with business logic intended to be used by graphical applications. The code
  is written in a way so it's not tied to a particular UI framework, for
  example Compose, SwiftUI, or other frameworks.
- `multipaz-compose` provides rich UI elements to be used in Compose
  applications.
- `multipaz-android-legacy` contains an older version of the APIs for
  applications not yet migrated to the newer libraries. At some point this
  library will be removed. Unlike the other libraries and applications, this
  library is in Java, not Kotlin, and only supports Android.
- `multipaz-doctypes` contains known credential document types (for example
  ISO/IEC 18013-5:2021 mDL and EU PID) along with human-readable descriptions
  of claims / data elements, sample data, and sample requests. This is
  packaged separately from the core `multipaz` library because its size is
  non-negligible and not all applications need this or they may bring their
  own.

A command-line tool `multipazctl` is also included which can be used to generate
ISO/IEC 18013-5:2021 IACA certificates among other things. Use
`./gradlew --quiet runMultipazCtl --args "help"` for documentation on supported
verbs and options.

## Library releases, Versioning, and Stability

Libraries are released on [GMaven](https://maven.google.com/) with a two-month cadence
and [Semantic Versioning](https://en.wikipedia.org/wiki/Software_versioning#Semantic_versioning)
is used. At this time we're in pre-1.0 territory but we expect to hit 1.0 around
early 2026.

At this point both API interfaces and data stored on disk is subject to change
but we expect to provide stability guarantees post 1.0. We only expect minor changes
for example conversion from `ByteArray` to `ByteString` and similar things.

## Getting involved

We have resources for people already involved and people wishing to contribute
to the Multipaz project
- [CONTRIBUTING.md](CONTRIBUTING.md) for how to get involved with the project and send PRs.
- [CODE-OF-CONDUCT.md](CODE-OF-CONDUCT.md) for the policies and rules around collaboration.
- [CODING-STYLE.md](CODING-STYLE.md) for guidelines on writing code to be included in the project.
- [TESTING.md](TESTING.md) explains our approach to unit and manual testing.
- [DEVELOPER-ENVIRONMENT.md](DEVELOPER-ENVIRONMENT.md) for how to set up your system for building Multipaz.

Note: If you're just looking to use the Multipaz libraries you do not need to build
the entire Multipaz project from source. Instead, just use our released libraries,
see the next section for an example of this.

## Examples / Samples

For a fully-fledged mDL wallet or reader application, the current answer is to use
the `samples/testapp` module which works on both Android and iOS. This application
is intended for developers and as such has a lot of options and settings. It's
intended to exercise all code in the libraries. Prebuilt APKs are available
from https://apps.multipaz.org.

To see how to use the Multipaz libraries from another project, see
[MpzSecureAreaSample](https://github.com/davidz25/MpzSecureAreaSample) for a
minimal Compose Multiplatform app using the SecureArea abstraction. This application
works on both Android and iOS.

For a sample using the legacy library, see
[SimpleVerifierStandalone](https://github.com/davidz25/SimpleVerifierStandalone)
which is a simple Android mDL reader application.

We intend to provide production-quality wallet and reader applications in separate
repositories later in 2025. This will be based on what is currently in the `wallet`
module, converted to Compose Multiplatform.

## Note

This is not an official or supported Google product.

