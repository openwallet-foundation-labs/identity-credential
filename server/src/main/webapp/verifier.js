
// Keep in sync with verifier.html
var selectedProtocol = 'w3c_dc_preview'

var openid4vpUri = ''

function onLoad() {
    const protocolDropdown = document.getElementById('protocolDropdown')
    protocolDropdown.addEventListener('hide.bs.dropdown', event => {
        var target = event.clickEvent.target
        var selected = target.getAttribute('value')
        if (selected === 'w3c_dc_preview' ||
            selected === 'w3c_dc_arf' ||
            selected === 'openid4vp_plain' ||
            selected === 'openid4vp_eudi' ||
            selected === 'openid4vp_mdoc') {
            selectedProtocol = selected
            protocolDropdown.innerHTML = target.innerHTML
        }
    })
}

async function onLoadRedirect() {
    const urlParams = new URLSearchParams(location.search);
    const sessionId = urlParams.get('sessionId');
    const response = await callServer(
        'openid4vpGetData',
        {
            sessionId: sessionId,
        }
    )
    var tbodyRef = document.getElementById('resultTable').getElementsByTagName('tbody')[0]
    for (const line of response.lines) {
        var newRow = tbodyRef.insertRow()
        var keyCell = newRow.insertCell()
        keyCell.appendChild(document.createTextNode(line.key))
        var valueCell = newRow.insertCell()
        valueCell.appendChild(document.createTextNode(line.value))
    }
    console.log(response)
}

function redirectClose() {
    console.log('redirectClose')
    window.close()
}

async function requestDocument(type) {
    console.log('requestMdoc, type=' + type + ' protocol=' + selectedProtocol)

    if (selectedProtocol.startsWith('openid4vp_')) {
        const response = await callServer(
            'openid4vpBegin',
            {
                requestType: type,
                protocol: selectedProtocol,
                origin: location.origin,
            }
        )
        console.log("URI " + response.uri)
        window.open(response.uri, '_blank').focus()
    } else if (selectedProtocol === "w3c_dc_preview") {
        try {
            const response = await callServer(
                'dcBegin',
                {
                    requestType: type,
                    protocol: selectedProtocol,
                    origin: location.origin,
                }
            )
            dcRequestCredential(response.sessionId, 'preview', JSON.parse(response.dcRequestString))
        } catch (err) {
            alert("Our implementation for W3C Digital Credentials protocol currently only supports mdoc.")
        }
    } else if (selectedProtocol === "w3c_dc_arf") {
        try {
            const response = await callServer(
                'dcBegin',
                {
                    requestType: type,
                    protocol: selectedProtocol,
                    origin: location.origin,
                }
            )
            dcRequestCredential(response.sessionId, 'austroads-request-forwarding-v2', JSON.parse(response.dcRequestString))
        } catch (err) {
            alert("Our implementation for W3C Digital Credentials protocol currently only supports mdoc.")
        }
    }
}

async function dcRequestCredential(sessionId, dcRequestProtocol, dcRequest) {
    try {
        const credentialResponse = await navigator.identity.get({
            digital: {
                providers: [{
                    protocol: dcRequestProtocol,
                    request: dcRequest
                }]
            },
            mediation: 'required',
          })
        dcProcessResponse(sessionId, credentialResponse)
    } catch (err) {
        alert(err)
    }
}

async function dcProcessResponse(sessionId, credentialResponse) {
    const response = await callServer(
        'dcGetData',
        {
            sessionId: sessionId,
            credentialResponse: credentialResponse.data
        }
    )
    var modalBody = document.getElementById('dcResultModal').querySelector('.list-group')
    modalBody.innerHTML = ''
    for (const line of response.lines) {
        modalBody.innerHTML += '<li class="list-group-item d-flex justify-content-between align-items-start"><div class="ms-2 me-auto"><div class="fw-bold">' + line.key + '</div>' + line.value + '</div></li>'
    }
    var modal = new bootstrap.Modal(document.getElementById('dcResultModal'), {})
    modal.show()
}

function openid4vpAuthenticateWithWallet() {
    console.log("Opening " + openid4vpUri)
    window.open(openid4vpUri)
}

async function callServer(command, params) {
    const response = await fetch(
        'verifier/' + command,
        {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(params)
        }
    )
    return await response.json()
}
