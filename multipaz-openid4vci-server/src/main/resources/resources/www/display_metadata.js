const configId = new URLSearchParams(window.location.search).get("config_id");
if (configId) {
    displayCredentialConfig(configId);
} else {
    displayCredentialList();
}

const urlSchema = "openid-credential-offer:"

async function displayCredentialConfig(configId) {
    let container = document.getElementById("main");
    let issuance = await fetchMetadata();
    addHeader(issuance);
    let config = issuance.credential_configurations_supported[configId];
    let item = document.createElement("div");
    item.className = "credential_container"
    container.appendChild(item);
    let h2 = document.createElement("h2");
    item.appendChild(h2);
    let display = config.credential_metadata.display[0];
    h2.textContent = display.name;
    let img = document.createElement("img");
    img.className = "credential_logo";
    img.setAttribute("src", display.logo?.uri);
    item.appendChild(img);
    let title = document.createElement("h3");
    title.textContent = "Authorization Code Flow";
    item.appendChild(title);
    let p1 = document.createElement("p");
    item.appendChild(p1);
    let a = document.createElement("a");
    p1.appendChild(a);
    a.textContent = "Credential offer using custom url schema"
    let url = location.href.substring(0, location.href.lastIndexOf("/"));
    let offer = {
        credential_issuer: url,
        credential_configuration_ids: [configId],
        grants: {
            authorization_code:{}
        }
    };
    let href = urlSchema + "//?credential_offer=" + encodeURIComponent(JSON.stringify(offer));
    a.href = href;
    let p2 = document.createElement("p");
    item.appendChild(p2);
    p2.textContent = "or scan the QR code below"
    let qr = document.createElement("img");
    qr.className = "qr"
    qr.setAttribute("src", "qr?q=" + encodeURIComponent(href));
    qr.setAttribute("style", "image-rendering: pixelated");
    p2.appendChild(qr);

    let certTitle = document.createElement("h3");
    certTitle.textContent = "Credential signing certificate";
    item.appendChild(certTitle);
    let pcert = document.createElement("p");
    item.appendChild(pcert);
    let acert = document.createElement("a");
    pcert.appendChild(acert);
    acert.textContent = "Root certificate in PEM format"
    let certUrl = location.href.substring(0, location.href.lastIndexOf("/")) + "/signing_certificate";
    acert.href = certUrl + "?credential_id=" + encodeURIComponent(configId);
}

async function displayCredentialList() {
    let container = document.getElementById("main");
    let issuance = await fetchMetadata();
    addHeader(issuance);
    let configs = issuance.credential_configurations_supported;
    let h2 = document.createElement("h2");
    h2.textContent = "Credentials available from this server";
    container.appendChild(h2);
    for (let configId in configs) {
        let config = configs[configId];
        let item = document.createElement("div");
        item.className = "credential_list_item";
        container.appendChild(item);
        let href = location.href + "?config_id=" + encodeURIComponent(configId);
        let display = config.credential_metadata.display[0];
        let a1 = document.createElement("a");
        a1.className = "credential_list_logo_container";
        let img = document.createElement("img");
        img.setAttribute("src", display.logo?.uri);
        img.className = "credential_list_logo";
        a1.href = href;
        a1.appendChild(img);
        item.appendChild(a1);
        item.appendChild(document.createTextNode(" "));
        a2 = document.createElement("a");
        a2.className = "credential_list_name";
        a2.textContent = display.name;
        a2.href = href;
        item.appendChild(a2);
    }
}

function addHeader(issuance) {
    const container = document.getElementById("main");
    const header = document.createElement("div");
    header.className = "header";
    container.appendChild(header);
    const hi = document.createElement("img");
    hi.className = "issuer_logo";
    hi.setAttribute("src", issuance.display[0].logo?.uri);
    header.appendChild(hi);
    const h1 = document.createElement("h1");
    h1.className = "issuer_name";
    h1.textContent = issuance.display[0].name;
    header.appendChild(h1);
}

async function fetchMetadata() {
    let path = location.pathname.substring(0, location.pathname.lastIndexOf("/"));
    return await (await fetch("/.well-known/openid-credential-issuer" + path)).json();
}