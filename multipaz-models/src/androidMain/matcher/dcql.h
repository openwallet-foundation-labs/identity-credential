
#pragma once

#include <string>
#include <vector>

#include "CredentialDatabase.h"

extern "C" {
#include "cJSON.h"
}

struct DcqlRequestedClaim {
    std::string id;
    std::vector<std::string> values;
    std::vector<std::string> path;

    // Only relevant for ISO mdoc
    bool intentToRetain;
};

struct DcqlClaimSet {
    std::vector<std::string> claimIdentifiers;
};

struct DcqlCredentialQuery {
    std::string id;
    std::string format;

    std::string mdocDocType;
    std::vector<std::string> vctValues;

    std::vector<DcqlRequestedClaim> requestedClaims;
    std::vector<DcqlClaimSet> claimSets;
};

struct DcqlCredentialSetOption {
    std::vector<std::string> credentialIds;
};

struct DcqlCredentialSetQuery {
    bool required;
    std::vector<DcqlCredentialSetOption> options;
};

struct DcqlCredentialQueryResponse;

struct DcqlQuery {
    std::vector<DcqlCredentialQuery> dcqlCredentialQueries;
    std::vector<DcqlCredentialSetQuery> dcqlCredentialSetQueries;

    void log();

    std::vector<DcqlCredentialQueryResponse> execute(CredentialDatabase* credentialDatabase);

    static DcqlQuery parse(cJSON* dcqlQuery);
};

struct DcqlCredentialQueryResponseMatch {
    Credential* credential;

    std::vector<Claim*> matchedClaims;
};

struct DcqlCredentialQueryResponse {
    std::vector<DcqlCredentialQueryResponseMatch> matches;
};

// ----

