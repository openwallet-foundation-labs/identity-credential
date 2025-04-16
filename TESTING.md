# Testing

This file describes how to manually test the Multipaz project. As a baseline, we strive for unit
tests when it's possible but due to the nature of the project - involving QR, NFC, and Bluetooth
Low Energy - this is not always feasible.

It's important that the tree remains in a "green" state all the time, that is, before
merging any commit we must ensure that all unit tests pass, that manual tests pass,
and that any new code has unit tests or manual tests.

## How to test

### Unit tests

```shell
$ ./gradlew check               # Runs all unit tests on the server
$ ./gradlew connectedCheck      # Runs all unit tests on connected devices (e.g. Android)
```

Also make sure this is run on a system which can build the entire project which includes
libraries and applications for e.g. iOS. Practically speaking this may mean having to
run this on a Mac.

### Manual testing

Our main tool for manual testing is the `samples/testapp` project. Conceptually, you
want start at the main screen and do a depth-first search through and test everything.
That said, use common sense, for example if your PR only involves changing something not
related to proximity presentations, maybe skip this. That said, dependencies are not always
obvious and a quick tap for mDL presentations is not a lot of work.

There are elements in `samples/testapp` which aren't obvious:

- NFC engagement flows are invoked by just tapping the phone on a mDL reader.
  - This works even when the app is not running and even on the lock screen.
  - If changing some of the NFC engagement code also test when another wallet app claiming
    the NDEF AID is installed and that it works correctly with the NFC disambiguation dialog.
    There are some tricky parts involved here and it's easy to break.

- W3C Digital Credentials API presentment flows are invoked from a browser.
  - Launch Chrome (or other supported browser) and go to a verifier website
  - You should be able to use http://127.0.0.1/server/verifier.html - just make sure you
    are running the server locally via `./gradlew server:tomcatRun` and configured the
    server appropriately.

#### Setting up a local server
Some of the testing steps require running a local server to handle issuance and verification requests. There are several
possible approaches to do this. One of the simpler ones, if you're planning on running Testapp on a physical device:

1. Ensure the test device is running and is visible and authorized for ADB: `adb devices -l`
1. Forward the test device's port 8080 to the dev machine's port 8080: `adb reverse tcp:8080 tcp:8080`
1. Start the server: `./gradlew server:tomcatRun`
1. For verification (sharing a document), on the test device, navigate to: `http://localhost:8080/server/verifier.html`
1. Enable support for Web Identity Digital Credentials: In your Chrome browser, go to `chrome://flags#web-identity-digital-credentials`, enable the feature, and restart your browser.
   This is required to avoid errors like `TypeError: cannot read properties of undefined (reading 'get')`

#### Test Matrix
This is a more detailed checklist that needs to be tested before each release. This can serve as a guide for an
individual PR, but it's generally not necessary to test every single one of these for every PR (see above regarding
common sense):

- [ ] About
  - [ ] Version number matches what's expected
- [ ] Document Store
  - [ ] Able to create documents in the Platform Secure Area 
  - [ ] Delete All Documents works
  - [ ] Able to create documents in the Software Secure Area
  - [ ] Cloud Secure Area
    - [ ] Follow the steps above in "Setting up a local server"
    - [ ] Able to create documents in the Cloud Secure Area (with test server running on localhost)
- [ ] Software Secure Area
  - [ ] "ESP256 - Passphrase" requests a passphrase and requires the correct passphrase
  - [ ] Spot check other algorithms, with and without passphrases
- [ ] Android Keystore Secure Area
  - [ ] Versions and Capabilities dialog works. "User Auth" and "Secure Lock Screen" display correct values
  - [ ] Attestation screen shows at least one valid certificate, possibly multiple
  - [ ] Strongbox Attestation shows at least one valid certificate, possibly multiple
  - [ ] Spot check some different algorithms
  - [ ] An algorithm with "Auth" works
  - [ ] An algorithm with (LSKF Only) works
  - [ ] An algorithm with (Biometric Only) works
  - [ ] Strongbox ESP256 works
  - [ ] Strongbox ED25519 fails (unsupported)
- [ ] Cloud Secure Area
  - [ ] Follow the steps above in "Setting up a local server"
  - [ ] Connecting, selecting the localhost URL, works successfully
  - [ ] CSA Attestation shows a valid certificate (possibly more than one)
  - [ ] Spot check some different algorithms
  - [ ] An algorithm with "Auth" works
  - [ ] An algorithm with (PIN Only) works
  - [ ] An algorithm with (Biometric Only) works
  - [ ] An algorithm with (PIN or Biometric) works
- [ ] PassphraseEntryField use-cases (make sure to try both failing and successful cases for each)
  - [ ] 4-Digit PIN (checkWeak=false) 
  - [ ] 4-Digit PIN (checkWeak=true) with a weak pin (eg. 1111) and with a not-weak pin (eg. 1112)
  - [ ] Spot check other PIN lengths
  - [ ] Spot check passphrase options
  - [ ] No constraints (checkWeak=true)
- [ ] PassphrasePrompt use-cases (make sure to try both failing and successful cases for each)
  - [ ] Spot check several PIN and Passphrase options
  - [ ] No constraints
- [ ] ConsentModalBottomSheet use-cases
  - [ ] Spot check several options
  - [ ] Make sure "Cancel" shows a message that the sheet was dismissed
  - [ ] Make sure swiping down shows a message that the sheet was dismissed
  - [ ] Make sure "Unknown Verifier (Proximity)" options show a red warning
  - [ ] Make sure "Unknown Verifier (Website)" options show a URL in the title and a red warning
  - [ ] Make sure (Partially Stored) options show some claims that are stored and some that are only shared
- [ ] QR code generation and scanning (you'll need two devices)
  - [ ] Show mdoc QR code shows a QR code
  - [ ] Show URL QR code shows a QR code
  - [ ] Scan mdoc QR code does not scan the "Show URL QR code" code
  - [ ] Scan mdoc QR code successfully scans the "Show mdoc QR code" code
- [ ] NFC sharing and scanning (you'll need two devices)
  - [ ] Scan for NDEF tag and read CC file successfully reads a nearby NFC device
- [ ] ISO mdoc Proximity Reading (on one device, with ISO mdoc Proximity Sharing on another)
  - [ ] On both devices, click the gear icon in top-right to go to Settings. Scroll down and press the "Reset Settings"
    button.
  - [ ] Request by QR code works
  - [ ] Request by NFC works
    - [ ] In Settings, ensure that "Use NFC Negotiated Handover" is checked and verify that NFC sharing works
      - [ ] In Settings, "ISO mdoc reader Transports (NFC Negotiated Handover)", check only Central client mode on both
        devices and verify NFC sharing works.
      - [ ] In Settings, "ISO mdoc reader Transports (NFC Negotiated Handover)", check only Peripheral server mode on
        both devices and verify NFC sharing works.
      - [ ] In Settings, "ISO mdoc reader Transports (NFC Negotiated Handover)", check only NFC Data Transfer on
        both devices and verify NFC sharing works. You'll need to keep the devices in NFC range while the data
        transfers.
      - [ ] In Settings, "ISO mdoc reader Transports (NFC Negotiated Handover)", check all options, including
        "Automatically select transport" and verify NFC sharing works.
      - [ ] In Settings, on both devices, Reset Settings, then under "ISO mdoc reader Transports (NFC Negotiated
        Handover)," uncheck "Use L2CAP if available" and verify NFC sharing works.
    - [ ] In Settings, check "Use NFC Static Handover" on both devices and verify NFC sharing works
      - [ ] In Settings, "ISO mdoc Transports (QR and NFC Static Handover)", check only Peripheral server mode on both
        devices and verify NFC sharing works.
      - [ ] In Settings, "ISO mdoc Transports (QR and NFC Static Handover)", check only NFC Data Transfer on both
        devices and verify NFC sharing works.
    - [ ] There are numerous other combinations in the Settings screen. Spot check a few other combinations. If any of
      them fail, ensure that they're added to this test matrix (and file bugs to fix them).
  - [ ] Spot check some of the different Data Elements that can be requested
    - [ ] Requesting a Driving License works
    - [ ] Requesting a Photo ID shows a disambiguation dialog
    - [ ] Requesting a Photo ID works
    - [ ] Requesting an EU Personal ID works
    - [ ] Requesting a Movie Ticket does not work for in-person presentation (this only works on a remote server)
- [ ] ISO mdoc Multi-Device Testing (Requires two devices)
  - [ ] Reset Bluetooth on both devices (turn Bluetooth off and then on again) 
  - [ ] Set up one device with "Happy Flow Short (20 iterations)"
  - [ ] Set up the other with "Run Multi-Device Tests as Client" and scan the QR code
  - [ ] The "Happy Flow Short (20 iterations)" tests pass
    - Note that instability in the BT stack can make these flaky, but ideally 100% of them should pass. 
  - [ ] The "All Tests & Corner-cases" tests pass
- [ ] CertificateViewer Examples
  - [ ] Expired Android Keystore Attestation chain shows expired certs
  - [ ] Maryland IACA certificate shows a cert from US-MD, from the Maryland MVA
  - [ ] "Certificate valid in the future..." Validity Info shows when the cert will become active
  - [ ] "Certificate valid in the future..." shows an Unknown OID
- [ ] Rich text
  - [ ] Correctly displays the title, list, subheading, circle, star, and link
  - [ ] Clicking the link works
- [ ] Notifications
  - [ ] Requests notification permission if it hasn't been granted
  - [ ] Post notification with image includes an image
  - [ ] Post notification without image shows only text
  - [ ] Update last notification changes the text of the last notification
  - [ ] Cancel last notification removes the last notification
  - [ ] Cancel all notifications removes all of them
- [ ] Screen lock
  - [ ] Indicates that the device has a screen lock, if it has one
  - [ ] Remove the screen lock from the device, then return to this screen
  - [ ] Shows a button to set a screen lock, if the device doesn't have one
- [ ] Testing against an online server
  - [ ] Follow the steps above in "Setting up a local server"
  - [ ] On the test device, navigate to: `http://localhost:8080/server/verifier.html`
  - [ ] Ensure that the Protocol is set to "W3C Digital Credentials API (OpenID4VP)"
    - [ ] Request a Driving License (mdoc), Mandatory Data Elements
    - [ ] Request an EU Personal ID (mdoc), All Data Elements
    - [ ] Request a Photo ID (mdoc), Age Over 18 + Portrait
    - [ ] Request a Movie Ticket (VC), All Data Elements

## Recording testing activity in commit messages

In order to have your PR approved, you have to convince the reviewer that it doesn't include any
regressions. This means that you need to pass all existing tests and also - when applicable - add
new tests. The reviewer should never have to ask about this so the way we do this is by
including `Test:` stanzas in the commit message conveying this important information.

This doesn't have to be machine-readable but it does have to make sense to the reviewer
and others reading the commit log. Use your best judgement and look at previous commit
messages for what others are doing. Here's a couple of examples:

```
Test: New unit tests.
Test: ./gradlew check && ./gradlew connectedCheck
Test: Carefully tested, during four days at two interoperability events.
```

```
Test: New unit tests and all unit tests pass.
Test: Manually tested (both samples/testapp and wallet module)
```

```
Test: Manually tested against 5+ different wallets and readers.
```
