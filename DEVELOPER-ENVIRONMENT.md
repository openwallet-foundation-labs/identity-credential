# Developer Environment

We use Android Studio for development which can be downloaded from
https://developer.android.com/studio.

# Mac OS

The preferred developer environment is Mac OS since this also allows building the iOS variant
of our Kotlin Multiplatform codebase. You will need a recent version of XCode installed along
with XCode command-line tools. See https://developer.apple.com/xcode/resources/ for more
information. XCode is used mainly just to launch and debug Testapp, all development usually
happens in Android Studio.

To build Testapp in XCode you will need to create the `samples/testapp/iosApp/DeveloperConfig.xcconfig`
file with the Team ID assigned to you by Apple. The project includes a template which can
be used for this in the `samples/testapp/iosApp/DeveloperConfig.xcconfig.template`
file.

```shell
cp samples/testapp/iosApp/DeveloperConfig.xcconfig.template samples/testapp/iosApp/DeveloperConfig.xcconfig && \
$EDITOR samples/testapp/iosApp/DeveloperConfig.xcconfig
```

# Linux and Windows

We also support building on Linux and Windows. In this setup, iOS libraries are not built
but non-iOS artifacts for other platforms will work fine.