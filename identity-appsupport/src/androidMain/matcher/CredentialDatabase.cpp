
#include "CredentialDatabase.h"

extern "C" {
#include "credentialmanager.h"
}

#include "cppbor.h"
#include "cppbor_parse.h"

CredentialDatabase::CredentialDatabase(const uint8_t* encodedDatabase, size_t encodedDatabaseLength) {
    auto [item, pos, message] = cppbor::parse(encodedDatabase, encodedDatabaseLength);
    if (item == nullptr) {
        printf("Error parsing CBOR: %s\n", message.c_str());
        return;
    }
    auto creds = item->asArray();
    for (auto i = creds->begin(); i != creds->end(); ++i) {
        auto cred = (*i)->asMap();
        auto title = cred->get("title")->asTstr()->value();
        auto subtitle = cred->get("subtitle")->asTstr()->value();
        auto bitmap = cred->get("bitmap")->asBstr()->value();

        std::string id = "";

        std::string mdocDoctype = "";
        std::map mdocDataElements = std::map<std::string, MdocDataElement>();

        std::string vcVct = "";
        std::map vcClaims = std::map<std::string, VcClaim>();

        auto& mdocPtr = cred->get("mdoc");
        if (mdocPtr != nullptr) {
            auto mdoc = mdocPtr->asMap();
            id = mdoc->get("id")->asTstr()->value();
            mdocDoctype = mdoc->get("docType")->asTstr()->value();

            auto namespaces = mdoc->get("namespaces")->asMap();
            for (auto j = namespaces->begin(); j != namespaces->end(); ++j) {
                auto namespaceName = j->first->asTstr()->value();
                auto dataElementsMap = j->second->asMap();

                for (auto k = dataElementsMap->begin(); k != dataElementsMap->end(); ++k) {
                    auto dataElementName = k->first->asTstr()->value();
                    auto dataElementDetailsArray = k->second->asArray();
                    auto displayName = dataElementDetailsArray->get(0)->asTstr()->value();
                    auto value = dataElementDetailsArray->get(1)->asTstr()->value();

                    auto combinedName = namespaceName + "." + dataElementName;
                    mdocDataElements[combinedName] = MdocDataElement(namespaceName, dataElementName, displayName, value);
                }
            }
        }

        auto& sdjwtPtr = cred->get("sdjwt");
        if (sdjwtPtr != nullptr) {
            auto sdjwt = sdjwtPtr->asMap();
            id = sdjwt->get("id")->asTstr()->value();
            vcVct = sdjwt->get("vct")->asTstr()->value();

            auto claims = sdjwt->get("claims")->asMap();
            for (auto j = claims->begin(); j != claims->end(); ++j) {
                auto claimName = j->first->asTstr()->value();
                auto claimDetailsArray = j->second->asArray();
                auto displayName = claimDetailsArray->get(0)->asTstr()->value();
                auto value = claimDetailsArray->get(1)->asTstr()->value();

                vcClaims[claimName] = VcClaim(claimName, displayName, value);
            }
        }

        credentials.push_back(
            Credential(
                title, subtitle, bitmap,id,
                mdocDoctype, mdocDataElements,
                vcVct, vcClaims
            )
        );
    }
}

bool Credential::matchesRequest(const Request& request) {
    if (request.docType.size() > 0 && request.docType == mdocDocType) {
        // Semantics is that we match if at least one of the requested data elements
        // exist in the credential
        for (const auto &requestedDataElement: request.dataElements) {
            auto combinedName =
                    requestedDataElement.namespaceName + "." + requestedDataElement.dataElementName;
            if (dataElements.find(combinedName) != dataElements.end()) {
                return true;
            }
        }
    } else if (request.vctValues.size() > 0 && vcVct.size() > 0) {
        if (std::find(request.vctValues.begin(), request.vctValues.end(), vcVct) != request.vctValues.end()) {
            // TODO: For now we use the semantics that at least one of the requested data elements must
            //   exist in the credential. When we merge the dcql-library branch this will change...
            for (const auto &requestedClaim : request.vcClaims) {
                if (vcClaims.find(requestedClaim.claimName) != vcClaims.end()) {
                    return true;
                }
            }
        }
    }
    return false;
}

void Credential::addCredentialToPicker(const Request& request) {
    char* cred_id = (char*) id.c_str();
    void* icon = malloc(bitmap.size());
    memcpy(icon, bitmap.data(), bitmap.size());
    ::AddStringIdEntry(
            cred_id,
            (char*) icon,
            bitmap.size(),
            (char*) strdup(title.c_str()),
            (char*) strdup(subtitle.c_str()),
            nullptr,
            nullptr
    );

    if (request.docType.size() > 0) {
        for (const auto &requestedDataElement: request.dataElements) {
            auto combinedName =
                    requestedDataElement.namespaceName + "." + requestedDataElement.dataElementName;
            auto it = dataElements.find(combinedName);
            if (it != dataElements.end()) {
                const auto &dataElement = it->second;
                ::AddFieldForStringIdEntry(cred_id,
                                           strdup(dataElement.displayName.c_str()),
                                           strdup(dataElement.value.c_str())
                );
            }
        }
    } else if (request.vctValues.size() > 0){
        for (const auto &requestedClaim: request.vcClaims) {
            auto it = vcClaims.find(requestedClaim.claimName);
            if (it != vcClaims.end()) {
                const auto &claim = it->second;
                ::AddFieldForStringIdEntry(cred_id,
                                           strdup(claim.displayName.c_str()),
                                           strdup(claim.value.c_str())
                );
            }
        }
    }
}

