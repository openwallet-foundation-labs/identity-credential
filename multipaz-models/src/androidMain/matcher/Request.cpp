
#include <map>

#include "base64.h"
#include "cppbor_parse.h"

#include "Request.h"

std::unique_ptr<Request> Request::parsePreview(cJSON* dataJson) {

    cJSON* selector = cJSON_GetObjectItem(dataJson, "selector");
    cJSON* docType = cJSON_GetObjectItem(selector, "doctype");
    std::string docTypeValue = std::string(cJSON_GetStringValue(docType));

    auto dataElements = std::vector<MdocRequestDataElement>();

    cJSON *fields = cJSON_GetObjectItem(selector, "fields");
    int numFields = cJSON_GetArraySize(fields);
    for (int n = 0; n < numFields; n++) {
        cJSON *field = cJSON_GetArrayItem(fields, n);
        cJSON *nameSpaceField =  cJSON_GetObjectItem(field, "namespace");
        std::string namespaceName = std::string(cJSON_GetStringValue(nameSpaceField));
        cJSON *nameField =  cJSON_GetObjectItem(field, "name");
        std::string dataElementName = std::string(cJSON_GetStringValue(nameField));
        cJSON *intentToRetainField =  cJSON_GetObjectItem(field, "intentToRetain");
        bool intentToRetain = cJSON_IsTrue(intentToRetainField);
        dataElements.push_back(MdocRequestDataElement(namespaceName, dataElementName, intentToRetain));
    }

    return std::unique_ptr<Request> { new Request("preview", docTypeValue, dataElements) };
}

std::string base64UrlDecode(const std::string& data) {
    // from_base64() doesn't handle strings without padding so we need
    // to manually add padding
    size_t len = data.size();
    std::string s = data;
    if (data[len - 1] == '=') {
        // already have padding
    } else {
        size_t rem = len & 3;
        if (rem == 2) {
            s = s + "==";
        } else if (rem == 3) {
            s = s + "=";
        } else {
            // no padding needed
        }
    }
    return from_base64(s);
}

std::unique_ptr<Request> Request::parseMdocApi(const std::string& protocolName, cJSON* dataJson) {
    cJSON* deviceRequestJson = cJSON_GetObjectItem(dataJson, "deviceRequest");
    std::string deviceRequestBase64 = std::string(cJSON_GetStringValue(deviceRequestJson));

    std::string docTypeValue;
    auto dataElements = std::vector<MdocRequestDataElement>();

    std::string deviceRequest = base64UrlDecode(deviceRequestBase64);
    auto [item, pos, message] =
            cppbor::parse((const uint8_t*) deviceRequest.data(), deviceRequest.size());

    auto map = item->asMap();
    auto doc_requests = map->get("docRequests")->asArray();

    // We only consider the first DocRequest...
    auto doc_request = doc_requests->get(0)->asMap();
    auto items_request_bytes = doc_request->get("itemsRequest")->asSemanticTag()->asBstr();
    auto [items_request, pos2, message2]
        = cppbor::parse(items_request_bytes->value());
    auto items_request_map = items_request->asMap();
    auto doc_type = items_request_map->get("docType")->asTstr();

    docTypeValue = doc_type->value();

    auto namespaces = items_request_map->get("nameSpaces")->asMap();
    for (auto j = namespaces->begin(); j != namespaces->end(); j++) {
        auto namespaceName = j->first->asTstr()->value();
        auto deMap = j->second->asMap();
        for (auto k = deMap->begin(); k != deMap->end(); k++) {
            auto dataElementName = k->first->asTstr()->value();
            auto intentToRetain = k->second->asBool()->value();
            dataElements.push_back(MdocRequestDataElement(namespaceName, dataElementName, intentToRetain));
        }
    }

    return std::unique_ptr<Request> { new Request(protocolName, docTypeValue, dataElements) };
}

std::unique_ptr<Request> Request::parseOpenID4VP(cJSON* dataJson, std::string protocolName) {
    std::string docTypeValue = "";
    auto dataElements = std::vector<MdocRequestDataElement>();
    std::vector<std::string> vctValues;
    auto vcClaims = std::vector<VcRequestedClaim>();

    // Handle signed request... the payload of the JWS is between the first and second '.' characters.
    cJSON* request = cJSON_GetObjectItem(dataJson, "request");
    if (request != nullptr) {
        std::string jwtStr = std::string(cJSON_GetStringValue(request));
        size_t firstDot = jwtStr.find(".");
        if (firstDot == std::string::npos) {
            return nullptr;
        }
        size_t secondDot = jwtStr.find(".", firstDot + 1);
        if (secondDot == std::string::npos) {
            return nullptr;
        }
        std::string payloadBase64 = jwtStr.substr(firstDot + 1, secondDot - firstDot - 1);
        std::string payload = base64UrlDecode(payloadBase64);
        dataJson = cJSON_Parse(payload.c_str());
    }

    // TODO: this is a simple minimal non-conforming implementation of DCQL. Will be adjusted to
    //   have the same features as our new DCQL library, see
    //   https://github.com/openwallet-foundation-labs/identity-credential/tree/dcql-library
    //

    cJSON* dcqlQuery = cJSON_GetObjectItem(dataJson, "dcql_query");
    cJSON* credentials = cJSON_GetObjectItem(dcqlQuery, "credentials");

    // TODO: for now we only consider the first credential request
    if (cJSON_GetArraySize(credentials) > 0) {
        cJSON *credential = cJSON_GetArrayItem(credentials, 0);
        auto format = std::string(cJSON_GetStringValue(cJSON_GetObjectItem(credential, "format")));
        if (format == "mso_mdoc" || format == "mso_mdoc_zk") {
            cJSON* meta = cJSON_GetObjectItem(credential, "meta");
            docTypeValue = std::string(cJSON_GetStringValue(cJSON_GetObjectItem(meta, "doctype_value")));
            cJSON* claim;
            cJSON* claims = cJSON_GetObjectItem(credential, "claims");
            cJSON_ArrayForEach(claim, claims) {
                cJSON* path = cJSON_GetObjectItem(claim, "path");
                auto namespaceName = std::string(cJSON_GetStringValue(cJSON_GetArrayItem(path, 0)));
                auto claimName = std::string(cJSON_GetStringValue(cJSON_GetArrayItem(path, 1)));
                // TODO: intent_to_retain
                dataElements.push_back(MdocRequestDataElement(namespaceName, claimName));
            }
        } else if (format == "dc+sd-jwt") {
            cJSON* meta = cJSON_GetObjectItem(credential, "meta");
            cJSON* vctValuesObj = cJSON_GetObjectItem(meta, "vct_values");
            cJSON* vctValueObj;
            cJSON_ArrayForEach(vctValueObj, vctValuesObj) {
                vctValues.push_back(std::string(cJSON_GetStringValue(vctValueObj)));
            }

            cJSON* claim;
            cJSON* claims = cJSON_GetObjectItem(credential, "claims");
            cJSON_ArrayForEach(claim, claims) {
                cJSON *path = cJSON_GetObjectItem(claim, "path");
                std::string claimName = "";
                for (int n = 0; n < cJSON_GetArraySize(path); n++) {
                    if (n > 0) {
                        claimName.append(".");
                    }
                    claimName.append(std::string(cJSON_GetStringValue(cJSON_GetArrayItem(path, n))));
                }
                vcClaims.push_back(VcRequestedClaim(claimName));
            }
        }
    }

    return std::unique_ptr<Request> { new Request(
            protocolName,
            docTypeValue,
            dataElements,
            vctValues,
            vcClaims
    ) };
}
