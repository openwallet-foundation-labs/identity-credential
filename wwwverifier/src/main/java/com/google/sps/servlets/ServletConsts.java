package com.android.identity.wwwreader;

public class ServletConsts {
    // Datastore constants
    public static final String DEV_RESPONSE_PROP = "Device Response";
    public static final String ENTITY_TYPE = "Main";
    public static final String PUBKEY_PROP = "Reader Key Public";
    public static final String PRIVKEY_PROP = "Reader Key Private";
    public static final String RE_PROP = "Reader Engagement";
    public static final String TRANSCRIPT_PROP = "Session Transcript";
    public static final String DEVKEY_PROP = "Device Key";
    public static final String TIMESTAMP_PROP = "Time Created";
    public static final String NUM_POSTS_PROP = "Number of POST requests";
    public static final String OI_PROP = "Origin Info Status";

    // HTTP request constants
    public static final String SESSION_URL = "create-new-session";
    public static final String RESPONSE_URL = "display-response";
    public static final String DE_KEY = "deviceEngagementBytes";

    // website URL constants
    public static final String MDOC_PREFIX = "mdoc://";
    public static final String BASE_URL = "https://mdoc-reader-external.uc.r.appspot.com/";
    public static final String ABSOLUTE_URL = BASE_URL + "request-mdl";

    // key generation constants
    public static final String KEYGEN_CURVE = "secp256r1";
    public static final String KEYGEN_INSTANCE = "EC";

    // other constants
    public static final String SESSION_SEPARATOR = ",";
    public static final String CHECKMARK = "* ";
    public static final String CROSS = "+ ";
    public static final String BOLD = "#";

    // Origin Info messages
    public static final String OI_SUCCESS = CHECKMARK + "OriginInfo: Referrer info matches website";
    public static final String OI_FAILURE_START = CROSS + "OriginInfo: Referrer ";
    public static final String OI_FAILURE_END = " doesn't match website (possible relay attack)";

    // constants for device request generation
    public static String MDL_DOCTYPE = "org.iso.18013.5.1.mDL";
    public static String MDL_NAMESPACE = "org.iso.18013.5.1";
    public static String AAMVA_NAMESPACE = "org.iso.18013.5.1.aamva";
}
