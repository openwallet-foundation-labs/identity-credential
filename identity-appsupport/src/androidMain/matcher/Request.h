
#pragma once

#include <string>
#include <vector>

extern "C" {
#include "cJSON.h"
}

struct MdocRequestDataElement {
    std::string namespaceName;
    std::string dataElementName;
    bool intentToRetain;
};

struct VcRequestedClaim {
    // TODO: support path
    std::string claimName;
};

struct Request {
    std::string docType;  // empty string if not for mdoc
    std::vector<MdocRequestDataElement> dataElements;

    std::vector<std::string> vctValues;  // empty array if not for VC
    std::vector<VcRequestedClaim> vcClaims;

    static std::unique_ptr<Request> parsePreview(cJSON *requestJson);
    static std::unique_ptr<Request> parseMdocApi(cJSON *requestJson);
    static std::unique_ptr<Request> parseOpenID4VP(cJSON *requestJson);
};
