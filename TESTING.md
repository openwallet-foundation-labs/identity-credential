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
