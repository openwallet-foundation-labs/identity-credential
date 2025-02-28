
#pragma once

#include <stdint.h>
#include <string>
#include <vector>
#include <map>

#include "Request.h"

struct MdocDataElement;
struct VcClaim;

struct Credential {
    std::string title;
    std::string subtitle;
    std::vector<uint8_t> bitmap;

    std::string id;

    std::string mdocDocType;   // This is the empty string if not available as an ISO mdoc.
    std::map<std::string, MdocDataElement> dataElements;

    std::string vcVct;   // This is the empty string if not available as a VC.
    std::map<std::string, VcClaim> vcClaims;

    bool matchesRequest(const Request& request);

    void addCredentialToPicker(const Request& request);
};

struct MdocDataElement {
    ~MdocDataElement() {}

    std::string namespaceName;
    std::string dataElementName;
    std::string displayName;
    std::string value;
};

struct VcClaim {
    ~VcClaim() {}

    std::string claimName;
    std::string displayName;
    std::string value;
};

struct CredentialDatabase {
    CredentialDatabase(const uint8_t* encodedDatabase, size_t encodedDatabaseLength);
    std::vector<Credential> credentials;
};