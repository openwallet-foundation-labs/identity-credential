package com.google.sps.servlets;

public class ServletConsts {
    public static final String ENTITY_NAME = "Main";
    public static final String READER_ENGAGEMENT_PROP = "Reader Engagement";
    public static final String PUBLIC_KEY_PROP = "Reader Key Public";
    public static final String PRIVATE_KEY_PROP = "Reader Key Private";
    public static final String BOOLEAN_PROP = "Sent Device Request";
    public static final String SESSION_TRANS_PROP = "Session Transcript";
    public static final String DEVICE_RESPONSE_PROP = "Device Response";

    public static final String SESSION_ESTABLISHMENT_PARAM = "SessionEstablishment";
    public static final String DEVICE_RESPONSE_PARAM = "DeviceResponse";

    public static final String MDOC_URI_PREFIX = "mdoc://";
    public static final String WEBSITE_URL = "https://mdoc-reader-external.uc.r.appspot.com";
    public static final String WEBSITE_URL_SERVLET = WEBSITE_URL + "/request-mdl";

    public static final String KEY_GENERATION_INSTANCE = "EC";
    public static final String KEY_GENERATION_CURVE = "secp256r1";

    public static final int DEVICE_ENGAGEMENT_INDEX = 2;
}
