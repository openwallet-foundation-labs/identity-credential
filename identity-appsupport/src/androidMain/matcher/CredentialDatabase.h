
#pragma once

#include <stdint.h>
#include <string>
#include <vector>
#include <map>

#include "MdocRequest.h"

struct MdocDataElement;

struct Credential {
    std::string title;
    std::string subtitle;
    std::vector<uint8_t> bitmap;

    // This is the empty string if not available as an ISO mdoc.
    std::string mdocId;
    std::string mdocDocType;
    std::map<std::string, MdocDataElement> dataElements;

    bool mdocMatchesRequest(const MdocRequest& request);

    void mdocAddCredentialToPicker(const MdocRequest& request);
};

struct MdocDataElement {
    ~MdocDataElement() {}

    std::string namespaceName;
    std::string dataElementName;
    std::string displayName;
    std::string value;
};

struct CredentialDatabase {
    CredentialDatabase(const uint8_t* encodedDatabase, size_t encodedDatabaseLength);
    std::vector<Credential> credentials;
};