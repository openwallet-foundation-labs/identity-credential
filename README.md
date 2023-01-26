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

### Running the MDL Reader Website

To run the MDL reader website (located at identity-credential/wwwverifier), a project must first be created at console.cloud.google.com. Afterwards, navigate to Cloud Shell (shell.cloud.google.com), and clone the Identity Credential Library repository:

```
git clone https://github.com/google/identity-credential.git
```

Open the file identity-credential/wwwverifier/build.gradle, and set the property ```projectId``` to the project ID that you used to create your Cloud project:

```
appengine {
    deploy {   // deploy configuration
      version = 'v1'
      projectId = '<YOUR_PROJECT_ID>'
      ...
    }
}
```

Then, navigate to wwwverifier:

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

The above command will create a link to a live website. Then, navigate to the file identity-credential/wwwverifier/src/main/java/com/google/sps/servlet/ServletConsts.java, and replace the following field with your website URL:

```
    public static final String BASE_URL = "<YOUR_WEBSITE_URL>";
```

There is currently a test instance of this application available at https://mdoc-reader-external.uc.r.appspot.com/.

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
