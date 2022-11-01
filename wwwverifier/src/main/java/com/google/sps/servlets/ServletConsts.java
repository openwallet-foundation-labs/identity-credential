package com.google.sps.servlets;

public class ServletConsts {
    // Datastore constants
    public static final String BOOLEAN_PROP = "Sent Device Request";
    public static final String DEVICE_RESPONSE_PROP = "Device Response";
    public static final String ENTITY_NAME = "Main";
    public static final String PUBLIC_KEY_PROP = "Reader Key Public";
    public static final String PRIVATE_KEY_PROP = "Reader Key Private";
    public static final String READER_ENGAGEMENT_PROP = "Reader Engagement";
    public static final String SESSION_TRANS_PROP = "Session Transcript";
    public static final String DEVICE_KEY_PROP = "Device Key";

    // HTTP request constants
    public static final String GET_PARAM = "request-type";
    public static final String GET_PARAM_URI = "create-uri";
    public static final String GET_PARAM_RESPONSE = "display-response";
    public static final String DEVICE_RESPONSE_PARAM = "deviceResponse";
    public static final String DEVICE_ENGAGEMENT_KEY = "deviceEngagementBytes";
    public static final String SESSION_ESTABLISHMENT_PARAM = "sessionEstablishment";

    // website URL constants
    public static final String MDOC_URI_PREFIX = "mdoc://";
    public static final String WEBSITE_URL = "https://mdoc-reader-external.uc.r.appspot.com/request-mdl";

    // key generation constants
    public static final String KEY_GENERATION_CURVE = "secp256r1";
    public static final String KEY_GENERATION_INSTANCE = "EC";

    // other messages
    public static final String DEFAULT_RESPONSE_MSSG = "Device Response has not yet been generated.";
}
