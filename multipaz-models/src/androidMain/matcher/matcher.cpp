
extern "C" {
#include "credentialmanager.h"
#include "cJSON.h"
}

#include "CredentialDatabase.h"
#include "Request.h"
#include <set>

extern "C" void matcher(void) {
    CallingAppInfo* appInfo = (CallingAppInfo*) malloc(sizeof(CallingAppInfo));
    ::GetCallingAppInfo(appInfo);

    uint32_t credsBlobSize;
    ::GetCredentialsSize(&credsBlobSize);
    uint8_t* credsBlob = (uint8_t*) malloc(credsBlobSize);
    ::ReadCredentialsBuffer((void*) credsBlob, 0, credsBlobSize);
    CredentialDatabase* db = new CredentialDatabase(credsBlob, credsBlobSize);

    // This contains a set of the documentIds for which we already include entries
    // for the credential picker. We maintain this to ensure we only add a single
    // entry for a single document.
    //
    // This is needed b/c a document may have multiple credentials for different
    // formats (e.g. ISO mdoc and IETF SD-JWT VC) and may be requested using multiple
    // W3C DC protocols (e.g. 18013-7 Annex C and OpenID4VP 1.0). If we didn't do
    // this the user would get an entry for four credential for the Cartesian product
    // of those two sets: {Annex C, OpenID} x {ISO mdoc, IETF SD-JWT}
    //
    // This enables a first-requested, first-served policy so if e.g. a RP makes a
    // request for `org-iso-mdoc` then `openid4vp-v1-signed`, we'll pick the credential
    // for `org-iso-mdoc`. Similarly, if they do it the other way around we'll
    // pick the credential for `openid4vp-v1-signed`. Other wallets may have different
    // policies but this is the one we use.
    //
    std::set<std::string> documentIdsAlreadyUsed;

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
                r = std::move(Request::parseMdocApi(protocolValue, protocolData));
            } else if (protocolValue == "austroads-request-forwarding-v2") {
                // From a matcher point of view, ARFv2 is structurally equivalent to mdoc-api
                //
                r = std::move(Request::parseMdocApi(protocolValue, protocolData));
            }

            if (r) {
                if (std::find(db->protocols.begin(), db->protocols.end(), r->protocol) != db->protocols.end()) {
                    for (auto &credential: db->credentials) {
                        if (credential.matchesRequest(*r)) {
                            if (documentIdsAlreadyUsed.find(credential.documentId) != documentIdsAlreadyUsed.end()) {
                                // Already have a credential for this document, skip
                                continue;
                            }
                            documentIdsAlreadyUsed.insert(credential.documentId);
                            credential.addCredentialToPicker(*r);
                        }
                    }
                }
            }
        }
    }
}
