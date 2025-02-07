
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

struct MdocRequest {
    std::string docType;
    std::vector<MdocRequestDataElement> dataElements;

    static std::unique_ptr<MdocRequest> parsePreview(cJSON *requestJson);
    static std::unique_ptr<MdocRequest> parseMdocApi(cJSON *requestJson);
    static std::unique_ptr<MdocRequest> parseOpenID4VP(cJSON *requestJson);
};
