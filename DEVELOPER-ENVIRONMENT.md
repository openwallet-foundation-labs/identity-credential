# Developer Environment

We use Android Studio for development which can be downloaded from
https://developer.android.com/studio.

# Mac OS

The preferred developer environment is Mac OS since this also allows building the iOS variant
of our Kotlin Multiplatform codebase. You will need a recent version of Xcode installed along
with Xcode command-line tools. See https://developer.apple.com/xcode/resources/ for more
information.

## Xcode

Xcode is used mainly just to launch and debug Testapp, all development usually happens
in Android Studio. If you're just making a library change in common code it's likely
enough to just rely on unit tests or the Android version of TestApp for testing. In other
words there is rarely a need to use Xcode at all.

To build Testapp in Xcode you will need to create the `samples/testapp/iosApp/DeveloperConfig.xcconfig`
file with the Team ID assigned to you by Apple. The project includes a template which can
be used for this in the `samples/testapp/iosApp/DeveloperConfig.xcconfig.template` file.

```shell
cp samples/testapp/iosApp/DeveloperConfig.xcconfig.template samples/testapp/iosApp/DeveloperConfig.xcconfig && \
$EDITOR samples/testapp/iosApp/DeveloperConfig.xcconfig
```

You also need to install [CocoaPods](https://cocoapods.org/) and set up your environment so
it's functional (e.g. the `pod` program is in PATH). Then you need to run

```shell
pod install
```

Once this is done, you can open `TestApp.xcworkspace` in Xcode, select TestApp and a target
(e.g. an iPhone), and run the app.

# Linux and Windows

We also support building on Linux and Windows. In this setup, iOS libraries are not built
but non-iOS artifacts for other platforms will work fine.