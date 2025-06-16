
extern "C" {
#include "credentialmanager.h"
#include "cJSON.h"
}

#include "CredentialDatabase.h"
#include "Request.h"

extern "C" int main() {
    CallingAppInfo* appInfo = (CallingAppInfo*) malloc(sizeof(CallingAppInfo));
    ::GetCallingAppInfo(appInfo);

    uint32_t credsBlobSize;
    ::GetCredentialsSize(&credsBlobSize);
    uint8_t* credsBlob = (uint8_t*) malloc(credsBlobSize);
    ::ReadCredentialsBuffer((void*) credsBlob, 0, credsBlobSize);
    CredentialDatabase* db = new CredentialDatabase(credsBlob, credsBlobSize);

    uint32_t requestSize;
    ::GetRequestSize(&requestSize);
    char* requestBlob = (char*) malloc(requestSize);
    ::GetRequestBuffer(requestBlob);
    cJSON* requestJson = cJSON_Parse(requestBlob);
    cJSON *requests = cJSON_GetObjectItemCaseSensitive(requestJson, "requests");
    if (cJSON_IsArray(requests)) {
        int numRequests = cJSON_GetArraySize(requests);
        for (int n = 0; n < numRequests; n++) {
            cJSON *request = cJSON_GetArrayItem(requests, n);
            if (!cJSON_IsObject(request)) {
                continue;
            }
            cJSON *protocol = cJSON_GetObjectItem(request, "protocol");
            std::string protocolValue = std::string(cJSON_GetStringValue(protocol));
            cJSON *protocolData = cJSON_GetObjectItem(request, "data");

            std::unique_ptr<Request> r;
            if (protocolValue == "preview") {
                // The OG "preview" protocol.
                //
                r = std::move(Request::parsePreview(protocolData));
            } else if (protocolValue == "openid4vp" ||
                       protocolValue == "openid4vp-v1-unsigned" ||
                       protocolValue == "openid4vp-v1-signed") {
                // OpenID4VP
                //
                r = std::move(Request::parseOpenID4VP(protocolData, protocolValue));
            } else if (protocolValue == "org.iso.mdoc" || protocolValue == "org-iso-mdoc") {
                // 18013-7 Annex C
                //
                r = std::move(Request::parseMdocApi(protocolData));
            } else if (protocolValue == "austroads-request-forwarding-v2") {
                // From a matcher point of view, ARFv2 is structurally equivalent to mdoc-api
                //
                r = std::move(Request::parseMdocApi(protocolData));
            }

            if (r) {
                for (auto& credential : db->credentials) {
                    if (credential.matchesRequest(*r)) {
                        credential.addCredentialToPicker(*r);
                    }
                }
            }
        }
    }
}
