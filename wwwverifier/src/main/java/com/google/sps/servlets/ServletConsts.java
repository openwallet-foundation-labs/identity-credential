package com.google.sps.servlets;

public class ServletConsts {
    // Datastore constants
    public static final String DEVICE_RESPONSE_PROP = "Device Response";
    public static final String ENTITY_NAME = "Main";
    public static final String PUBLIC_KEY_PROP = "Reader Key Public";
    public static final String PRIVATE_KEY_PROP = "Reader Key Private";
    public static final String READER_ENGAGEMENT_PROP = "Reader Engagement";
    public static final String SESSION_TRANS_PROP = "Session Transcript";
    public static final String DEVICE_KEY_PROP = "Device Key";
    public static final String TIMESTAMP_PROP = "Time Created";
    public static final String NUM_REQUESTS_PROP = "Number of POST requests";

    // HTTP request constants
    public static final String NEW_SESSION_URL = "create-new-session";
    public static final String RESPONSE_URL = "display-response";
    public static final String DEVICE_ENGAGEMENT_KEY = "deviceEngagementBytes";

    // website URL constants
    public static final String MDOC_URI_PREFIX = "mdoc://";
    public static final String WEBSITE_URL = "https://mdoc-reader-external.uc.r.appspot.com/request-mdl";

    // key generation constants
    public static final String KEY_GENERATION_CURVE = "secp256r1";
    public static final String KEY_GENERATION_INSTANCE = "EC";

    // constants for device request generation
    public static String MDL_DOCTYPE = "org.iso.18013.5.1.mDL";
    public static String MDL_NAMESPACE = "org.iso.18013.5.1";
    public static String AAMVA_NAMESPACE = "org.aamva.18013.5.1";
}
