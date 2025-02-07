
extern "C" {
#include "credentialmanager.h"
#include "cJSON.h"
}

#include "CredentialDatabase.h"
#include "MdocRequest.h"

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
    cJSON *providers = cJSON_GetObjectItemCaseSensitive(requestJson, "providers");
    if (cJSON_IsArray(providers)) {
        int numProviders = cJSON_GetArraySize(providers);
        for (int n = 0; n < numProviders; n++) {
            cJSON *provider = cJSON_GetArrayItem(providers, n);
            if (!cJSON_IsObject(provider)) {
                continue;
            }
            cJSON *protocol = cJSON_GetObjectItem(provider, "protocol");
            std::string protocolValue = std::string(cJSON_GetStringValue(protocol));
            cJSON *protocolRequest = cJSON_GetObjectItem(provider, "request");
            const char* protocolRequestValue = cJSON_GetStringValue(protocolRequest);
            cJSON* protocolRequestJson = cJSON_Parse(protocolRequestValue);

            std::unique_ptr<MdocRequest> mdocRequest;
            if (protocolValue == "preview") {
                // The OG "preview" protocol.
                //
                mdocRequest = std::move(MdocRequest::parsePreview(protocolRequestJson));
            } else if (protocolValue == "openid4vp") {
                // 18013-7 Annex D
                //
                mdocRequest = std::move(MdocRequest::parseOpenID4VP(protocolRequestJson));
            } else if (protocolValue == "org.iso.mdoc") {
                // 18013-7 Annex C
                //
                mdocRequest = std::move(MdocRequest::parseMdocApi(protocolRequestJson));
            } else if (protocolValue == "austroads-request-forwarding-v2") {
                // From a matcher point of view, ARFv2 is structurally equivalent to mdoc-api
                //
                mdocRequest = std::move(MdocRequest::parseMdocApi(protocolRequestJson));
            }

            if (mdocRequest) {
                for (auto& credential : db->credentials) {
                    if (credential.mdocMatchesRequest(*mdocRequest)) {
                        credential.mdocAddCredentialToPicker(*mdocRequest);
                    }
                }
            }
        }
    }
}
