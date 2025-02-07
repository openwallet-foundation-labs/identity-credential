
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

        std::string mdocId = "";
        std::string mdocDoctype = "";
        std::map mdocDataElements = std::map<std::string, MdocDataElement>();

        auto& mdocPtr = cred->get("mdoc");
        if (mdocPtr != nullptr) {
            auto mdoc = mdocPtr->asMap();
            mdocId = mdoc->get("id")->asTstr()->value();
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

        credentials.push_back(Credential(title, subtitle, bitmap, mdocId, mdocDoctype, mdocDataElements));
    }
}

bool Credential::mdocMatchesRequest(const MdocRequest& request) {
    if (mdocDocType != request.docType) {
        return false;
    }
    // Semantics is that we match if at least one of the requested data elements
    // exist in the credential
    for (const auto& requestedDataElement : request.dataElements) {
        auto combinedName = requestedDataElement.namespaceName + "." + requestedDataElement.dataElementName;
        if (dataElements.find(combinedName) != dataElements.end()) {
            return true;
        }
    }
    return false;
}

void Credential::mdocAddCredentialToPicker(const MdocRequest& request) {
    char* cred_id = (char*) mdocId.c_str();
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

    for (const auto& requestedDataElement : request.dataElements) {
        auto combinedName = requestedDataElement.namespaceName + "." + requestedDataElement.dataElementName;
        auto it = dataElements.find(combinedName);
        if (it != dataElements.end()) {
            const auto& dataElement = it->second;
            ::AddFieldForStringIdEntry(cred_id,
                                       strdup(dataElement.displayName.c_str()),
                                       strdup(dataElement.value.c_str())
            );
        }
    }
}

