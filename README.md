# Multipaz

This repository contains libraries and applications for working
with *Real-World Identity*. The initial focus for this work
was mdoc/mDL according to [ISO/IEC 18013-5:2021](https://www.iso.org/standard/69084.html)
and related standards (mainly ISO 23220 series and ISO 18013-7)
but the current scope also include other credential formats.

## Multipaz Libraries

The project includes libraries written in Kotlin:

- `multipaz` provides the core building blocks and which can also be used
   in server-side environments. It includes mdoc folders that provide
   data structures and routines for working with mdoc credentials. There are
   sdjwt folders that provide data structures and routines for working with
   [IETF SD-JWT](https://datatracker.ietf.org/doc/draft-ietf-oauth-selective-disclosure-jwt/). This library can also be used in server-side-environments.
   The library is written using Kotlin Multiplatform, so there are
   platform-specific folders that contain extensions specific to Android and
   iOS. The Android extensions are designed to
   run on Android (API 24 or later) and will take advantage of
   Android-specific features including hardware-backed Keystore, NFC, Bluetooth
   Low Energy, and so on.
- `multipaz-android-legacy` contains an older version of the APIs for
   applications not yet migrated to the newer libraries. At some point this
   library will be removed. Unlike the other libraries and applications, this
   library is in Java, not Kotlin.
- `multipaz-doctypes` contains known credential document types (for example
   ISO/IEC 18013-5:2021 mDL and EU PID) along with human-readable descriptions
   of claims / data elements and also sample data. This is packaged separately
   from the core `multipaz` library because its size is non-negligible and not
   all applications need this or they may bring their own.
- `multipaz-csa` contains library code for implementing a Cloud-based Secure
   Area. This is discussed more in-depth below.

These libraries are intended to be used by Wallet Applications (mobile
applications on the credential holder's device), Reader Applications
(applications operated on device controlled by the verifier), and Issuance
Systems (applications operated by the credential issuer or their agent). They
provide the following building blocks

- A light-weight _Secure Area_ abstraction for hardware-backed keystore
  - Applications can create hardware-backed Elliptic Curve Cryptography
    keys which can be used for creating Signatures or performing Key Agreement.
    Each key will have an attestation which can be used to prove to Relying
    Parties (such as a credential issuer) that the private part of the key
    only exists in a Secure Area.
  - The androidMain directory in `multipaz` includes an implementation based on
    [Android Keystore](https://developer.android.com/training/articles/keystore)
    with support for requiring user authentication (biometric or lock-screen
    knowledge factor, e.g. system PIN) for unlocking the key and also can use
    [StrongBox](https://source.android.com/docs/compatibility/13/android-13-cdd#9112_strongbox)
    if available on the device. This is appropriate to use in Android
    applications implementing ISO/IEC 18013-5:2021 for storing `DeviceKey`.
  - The `multipaz` library includes an implementation backed by BouncyCastle
    with support for passphrase-protected keys. This isn't suitable for use
    in Mobile Applications as its not backed by Secure Hardware.
  - A protocol for a Cloud Secure Area is provided along with production quality
    client-side implementation and a reference implementation of the server side
    in the `multipaz-csa` library. The provided server implementation isn't suitable
    for production use.
    - The point of this is to provide a secure and privacy-preserving protocol
      with end-to-end encryption directly from the app to a Secure Area
      in the server, with messages being exchanged via HTTPS. Consequently, this
      allows server implementations to terminate TLS outside the server's Secure Area
      without sacrificing privacy.
    - Key material created by applications is designed to never leave the Secure Area
      on the server.
    - The protocol has support for requiring user authentication (biometric or lock-screen
      knowledge factor, e.g. system PIN) and/or passphrase for unlocking the key material.
    - The way the protocol works is that the Cloud Secure Area learns very little about the
      user (only which Android or iOS application is requesting service) so if the Cloud
      Secure Area is run by another entity than the credential issuer (and no collusion
      exists between the two), this can provide the same level of privacy as when the
      Secure Area is local on the device.
    - This protocol is currently marked as experimental and may change in the future.
  - Applications can supply their own _Secure Area_ implementations for e.g.
    externally attached dongles, cloud based HSMs, or whatever the issuer
    deems appropriate to protect key material associated with their credential.
- A _Credential Store_ for storage of one or more _Credentials_
  - Each Credential has a _Credential Key_ which can be used by the issuer
    to bind a credential to a specific device which is useful when
    issuing updates or refreshing a credential.
  - Additionally, each Credential has one or more _Authentication Keys_ which
    can be endorsed by the issuer and used at presentation time.
  - Finally, namespaced data and arbitrary key/value pairs can be stored
    in a _Credential_ which can be used for credential data and claims. This
    data is stored encrypted at rest.
- Data structures and code for provisioning of mdoc/mDLs
  - This code can can be used both on the device and issuer side. No networking
    protocol is defined, the application has to define its own.
- Parsers and generators for all data structures used in ISO/IEC 18013-5:2021
  presentations, including `DeviceResponse`, `DeviceRequest`,  `MobileSecurityObject`
  and many other CBOR data structures.
- An implementation of the ISO/IEC 18013-5:2021 presentation flows including
  QR engagement, NFC engagement (both static and negotiated), device retrieval
  (BLE, Wifi Aware, and NFC)

Currently these libraries require a Java runtime environment but the plan is
to target [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
for the libraries and [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
for applications and samples.

### Customization

The `wallet` application is intended to be easily customizable by downstream
consumers and has built-in support for this via
[Android product flavors](https://developer.android.com/build/build-variants#product-flavors).
Downstreams are expected to change files under `wallet/src/customized` to
suit their configuration, including

- strings/icons (in particular the application name) and text in `about.md`
- configuration in `WalletApplicationConfiguration.kt` in particular change
  the wallet server address from `ws.example.com` to point to your own wallet server
- the `ws.example.com` domain in `network_security_config.xml`
- the `com.example.wallet.customized` applicationId in `wallet/build.gradle`

The `server` application can be customized using the `server/web.xml` file.

### Command-line tool

A command-line tool `multipazctl` is included which can be used to generate
ISO/IEC 18013-5:2021 IACA test certificates among other things. Use
`./gradlew --quiet runMultipazCtl --args "help"` for documentation on supported
verbs and options.

### Library releases, Versioning, and Documentation

Libraries are released on [GMaven](https://maven.google.com/) as needed and version
numbers are encoded as YYYYMMDD. With each release, we also publish documentation at
https://openwallet-foundation-labs.github.io/identity-credential/.

## Wallet and Reader Android applications

This repository also contains two Android applications using this library
in the `appholder` and `appverifier` modules. The Wallet application is a simple
self-contained application which allows  creating a number of mdoc credentials
using four different mdoc Document Types:

- `org.iso.18013.5.1.mDL`: Mobile Driving License
- `org.micov.1`: mdoc for eHealth ([link](https://github.com/18013-5/micov))
- `nl.rdw.mekb.1`: mdoc for Vehicle Registration ([link](https://github.com/18013-5/mVR))
- `eu.europa.ec.eudi.pid.1`: mdoc for Personal Identification

and their associated mdoc name spaces. The first one is defined in 
ISO/IEC 18013-5:2021 and the other three have been used at mdoc/mDL
test events organized by participants of the ISO/IEC JTC1 SC17 WG10
working group.

The `appholder` offers two flavors: `wallet` and `purse`. There is not much difference between
the two, except they have different application id, so they can coexist in a single device.
They also have different labels and icon color. To select the desired flavor when running the app
on a device/emulator, inside the Android Studio open the `Build Variants` panel. It should be easily 
reachable on the left side bar of the Android Studio, or by selecting: _View -> Tool Windows -> Build Variants_.
Inside the `Build Variants` panel, at the `appholder` row, the desired flavor can be chosen. Once a
flavor is selected, by running the app it will install it on the target device/emulator.

The `wallet` module is a rewrite of the `appholder` reference application
with an eye towards a production-quality and easily rebrandable identity
wallet application. Wallet app now attempts to connect to the wallet server on start-up, if
that fails it continues in the standalone matter.

The `multipaz-issuance` module contains code for server-based credential issuance. It defines
server/client interfaces as well as provides the implementation for them. Server environment
(such as settings, resources or persistent storage) is abstracted away, so the code can be run on
the client as well (only for development and demos).

The `server` module exposes server-side code (currently only from `multipaz-issuance`) as a
runnable servlet. It contains the servlet itself and implementations for the server environment
interfaces. Server configuration file, resources and database can be found in 
`server/environment` folder.

Use the following command to run the server locally for development:
`./gradlew server:tomcatRun`.

## Sample Applications

The `samples/` directory contain a number of sample applications, intended primarily
to show certain library features or assess performance or correctness. The following
samples are included

- `preconsent-mdl` - Simple mDL application without user consent authentication.
  - The main purpose of this sample is to assess performance of our libraries, Android,
    the device it's being run on, and the mDL reader requesting the mDL.
  - The application allows the user to easily configure which kind of data transfer
    method to use, including an idealized near-zero latency method (`DataTransportUdp`)
    to help pinpoint potential performance bottlenecks not related to data transfer.
- `age-verifier-mdl` - a simple mDL reader for age attestations.
  - This application is just requesting the `age_over_21` and `portrait`. It's intended
    to be used with the `preconsent-mdl` sample for performance evaluation.
- `simple-verifier` - a simple mDL reader for age attestations.
  - This application requests either {`age_over_21` and `portrait`} or 
    {`age_over_18` and `portrait`}. It's intended to demonstrate use of the 
    `MdocReaderPrompt` class, which allows any app to easily act as a reader app for 
    the common age-verification use case.
- `testapp` - a Compose Multiplatform application for manually testing elements of the
  project that aren't easily tested using unit tests.

## ISO 18013-7 Reader Website

TODO: This section is out of date. `wwwverifier` is no longer part of this
repository.

The `wwwverifier` module contains the source code for a website acting as an
mdoc reader according to the latest ISO 18013-7 working draft (as of Sep 2023)
and it's implementing  the so-called REST API. There is currently a test instance
of this application available at https://mdoc-reader-external.uc.r.appspot.com/.
The Wallet Android  application also has support for the REST API and registers
on Android for the `mdoc://` URI scheme. This can be tested end-to-end by going
to the reader  website (URL above) and clicking on one of the "Request" buttons,
and then  hitting the `mdoc://` link presented on the site. This will cause the
browser  to invoke the Wallet app which will then connect to the reader and send
the credential after user consent.

### Building and deploying the ISO 18013-7 Reader Website

First, a project must first be created at https://console.cloud.google.com. Afterwards,
navigate to Cloud Shell (https://shell.cloud.google.com), and clone the Multipaz
Library repository:

```
git clone https://github.com/google/identity-credential.git
```

Open the file `wwwverifier/build.gradle`, and set the property `projectId` to the
project ID that you used to create your Cloud project:

```
appengine {
    deploy {   // deploy configuration
      version = 'v1'
      projectId = '<YOUR_PROJECT_ID>'
      ...
    }
}
```
Grant Datastore Owner permissions to your AppEngine service account:
```
gcloud projects add-iam-policy-binding <YOUR_PROJECT_ID> \
    --member="serviceAccount:<YOUR_PROJECT_ID>@appspot.gserviceaccount.com" \
    --role="roles/datastore.owner"
```

Then, navigate to `wwwverifier`:

```
cd ~/identity-credential/wwwverifier
```

To run the website locally, execute the command:

```
gradle appengineRun
```

To deploy the website on a live server, execute the command:

```
gradle appengineDeploy
```

The above command will create a link to a live website. Then, navigate to the file 
`~/identity-credential/wwwverifier/src/main/java/com/android/identity/wwwreader/ServletConsts.java`,
and replace the following field with your website URL:

```
    public static final String BASE_URL = "<YOUR_WEBSITE_URL>";
```

# Name

The name of the project is currently "Multipaz" and it's using
`org.multipaz` as the Java package name.
