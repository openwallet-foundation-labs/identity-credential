displayMetadata()

async function displayMetadata() {
    let body = document.body;
    let issuance = await (await fetch(".well-known/openid-credential-issuer")).json();
    let hi = document.createElement("img");
    hi.setAttribute("src", issuance.display[0].logo?.uri);
    hi.setAttribute("style", "width:20%;max-width:180px;float: right");
    body.appendChild(hi);
    let h1 = document.createElement("h1");
    h1.textContent = issuance.display[0].name;
    body.appendChild(h1);
    let configs = issuance.credential_configurations_supported;
    let h2 = document.createElement("h2");
    h2.textContent = "Credentials available from this server";
    body.appendChild(h2);
    for (let configId in configs) {
        let config = configs[configId];
        let item = document.createElement("div");
        item.setAttribute("style", "border-top: 2px solid black; margin: 1em")
        body.appendChild(item);
        let h3 = document.createElement("h3");
        item.appendChild(h3);
        let a = document.createElement("a");
        item.appendChild(a);
        h3.textContent = config.display[0].name;
        let img = document.createElement("img");
        img.setAttribute("src", config.display[0].logo?.uri);
        img.setAttribute("style", "width:80%;margin:1em;max-width:500px");
        a.appendChild(img);
        let url = location.href.substring(0, location.href.lastIndexOf("/"));
        let offer = {
            credential_issuer: url,
            credential_configuration_ids: [configId],
            grants: {
                authorization_code:{}
            }
        };
        let href = "openid-credential-offer://?credential_offer=" + encodeURIComponent(JSON.stringify(offer));
        a.href = href;
        let qr = document.createElement("img");
        qr.setAttribute("src", "qr?q=" + encodeURIComponent(href));
        qr.setAttribute("style", "width:70%;margin:1em;margin-left:2em;image-rendering: pixelated;max-width:450px");
        item.appendChild(qr);
    }
}