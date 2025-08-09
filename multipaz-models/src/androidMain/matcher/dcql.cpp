
#include "dcql.h"
#include "logger.h"

DcqlQuery DcqlQuery::parse(cJSON* dcqlQuery) {
    auto dcqlCredentialQueries = std::vector<DcqlCredentialQuery>();
    auto dcqlCredentialSetQueries = std::vector<DcqlCredentialSetQuery>();

    cJSON* query = cJSON_GetObjectItem(dcqlQuery, "dcql_query");
    cJSON* credentials = cJSON_GetObjectItem(query, "credentials");
    cJSON* credentialSets = cJSON_GetObjectItem(query, "credential_sets");

    cJSON* credential;
    cJSON_ArrayForEach(credential, credentials) {
        std::vector<std::string> vctValues;
        std::vector<DcqlRequestedClaim> requestedClaims;
        std::vector<DcqlClaimSet> claimSets;
        std::string mdocDocType;

        auto id = std::string(cJSON_GetStringValue(cJSON_GetObjectItem(credential, "id")));
        auto format = std::string(cJSON_GetStringValue(cJSON_GetObjectItem(credential, "format")));
        if (format == "mso_mdoc" || format == "mso_mdoc_zk") {
            cJSON* meta = cJSON_GetObjectItem(credential, "meta");
            mdocDocType = std::string(cJSON_GetStringValue(cJSON_GetObjectItem(meta, "doctype_value")));
        } else if (format == "dc+sd-jwt") {
            cJSON* meta = cJSON_GetObjectItem(credential, "meta");
            cJSON* vctValuesObj = cJSON_GetObjectItem(meta, "vct_values");
            cJSON* vctValueObj;
            cJSON_ArrayForEach(vctValueObj, vctValuesObj) {
                vctValues.push_back(std::string(cJSON_GetStringValue(vctValueObj)));
            }
        }

        cJSON* claim;
        cJSON_ArrayForEach(claim, cJSON_GetObjectItem(credential, "claims")) {
            std::string id = "";
            cJSON* idObj = cJSON_GetObjectItem(claim, "id");
            if (idObj != nullptr) {
                id = std::string(cJSON_GetStringValue(idObj));
            }
            std::vector<std::string> path;
            cJSON* pathElem;
            cJSON_ArrayForEach(pathElem, cJSON_GetObjectItem(claim, "path")) {
                path.push_back(std::string(cJSON_GetStringValue(pathElem)));
            }

            std::vector<std::string> values;
            cJSON* valuesObj = cJSON_GetObjectItem(claim, "values");
            if (valuesObj != nullptr) {
                cJSON* value;
                cJSON_ArrayForEach(value, valuesObj) {
                    values.push_back(std::string(cJSON_GetStringValue(value)));
                }
            }

            auto intentToRetain = cJSON_IsTrue(cJSON_GetObjectItem(claim, "required"));

            requestedClaims.push_back(DcqlRequestedClaim(
                    id,
                    values,
                    path,
                    intentToRetain
            ));
        }

        cJSON* claimSet;
        cJSON_ArrayForEach(claimSet, cJSON_GetObjectItem(credential, "claim_sets")) {
            std::vector<std::string> claimIdentifiers;
            cJSON* claimIdentifier;
            cJSON_ArrayForEach(claimIdentifier, claimSet) {
                claimIdentifiers.push_back(std::string(cJSON_GetStringValue(claimIdentifier)));
            }
            claimSets.push_back(DcqlClaimSet(claimIdentifiers));
        }

        dcqlCredentialQueries.push_back(DcqlCredentialQuery(
                id,
                format,
                mdocDocType,
                vctValues,
                requestedClaims,
                claimSets
        ));
    }

    cJSON* credentialSet;
    cJSON_ArrayForEach(credentialSet, credentialSets) {
        auto required = !cJSON_IsFalse(cJSON_GetObjectItem(credentialSet, "required"));
        auto options = std::vector<DcqlCredentialSetOption>();

        cJSON* option;
        cJSON_ArrayForEach(option, cJSON_GetObjectItem(credentialSet, "options")) {
            auto credentialIds = std::vector<std::string>();
            cJSON* id;
            cJSON_ArrayForEach(id, option) {
                credentialIds.push_back(cJSON_GetStringValue(id));
            }
            options.push_back(DcqlCredentialSetOption(credentialIds));
        }

        dcqlCredentialSetQueries.push_back(DcqlCredentialSetQuery(required, options));
    }

    return DcqlQuery(
            dcqlCredentialQueries,
            dcqlCredentialSetQueries
    );
}

void DcqlQuery::log() {
#ifdef MATCHER_TEST_BUILD
    LOG("credentials = [");
    for (auto& q: dcqlCredentialQueries) {
        LOG("  {");
        LOG("    id %s", q.id.c_str());
        LOG("    format %s", q.format.c_str());
        LOG("    mdocDocType %s", q.mdocDocType.c_str());
        LOG("    vctValues %s", joinStrings(q.vctValues).c_str());
        LOG("    claims = [");
        for (auto &c : q.requestedClaims) {
            LOG("      {");
            LOG("        id %s", c.id.c_str());
            LOG("        values %s", joinStrings(c.values).c_str());
            LOG("        path %s", joinStrings(c.path).c_str());
            LOG("        intentToRetain %d", c.intentToRetain);
            LOG("      },");
        }
        LOG("    ],");
        LOG("    claimSets = [");
        for (auto &cs : q.claimSets) {
            LOG("      {");
            LOG("        claimIdentifiers %s", joinStrings(cs.claimIdentifiers).c_str());
            LOG("      },");
        }
        LOG("    ],");
        LOG("  },");
    }
    LOG("]");
    LOG("");
    LOG("credential_sets = [");
    for (auto& csq: dcqlCredentialSetQueries) {
        LOG("  {");
        LOG("    required %d", csq.required);
        LOG("    options = [");
        for (auto &o : csq.options) {
            LOG("      {");
            LOG("        credentialIds %s", joinStrings(o.credentialIds).c_str());
            LOG("      },");
        }
        LOG("    ],");
        LOG("  },");
    }
    LOG("]");
#endif // MATCHER_TEST_BUILD
}

std::vector<DcqlCredentialQueryResponse> DcqlQuery::execute(CredentialDatabase* credentialDatabase) {
    std::vector<DcqlCredentialQueryResponse> responses;

    for (auto& query: dcqlCredentialQueries) {
        std::vector<Credential*> credsSatifyingMeta;
        for (auto& cred: credentialDatabase->credentials) {
            if (query.format == "mso_mdoc" || query.format == "mso_mdoc_zk") {
                if (cred.mdocDocType == query.mdocDocType) {
                    credsSatifyingMeta.push_back(&cred);
                }
            } else if (query.format == "dc+sd-jwt") {
                if (std::find(query.vctValues.begin(), query.vctValues.end(), cred.vcVct) != query.vctValues.end()) {
                    credsSatifyingMeta.push_back(&cred);
                }
            }
        }

        std::vector<DcqlCredentialQueryResponseMatch> matches;
        for (auto& cred : credsSatifyingMeta) {
            if (query.claimSets.size() == 0) {
                bool didNotMatch = false;
                for (auto& claim : query.requestedClaims) {
                }
            } else {

            }
        }
    }



    return responses;
}


