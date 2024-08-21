
// Keep in sync with verifier.html
var selectedProtocol = 'openid4vp_plain'

var openid4vpUri = ""

function onLoad() {
    const protocolDropdown = document.getElementById('protocolDropdown')
    protocolDropdown.addEventListener('hide.bs.dropdown', event => {
        var target = event.clickEvent.target
        var selected = target.getAttribute('value')
        if (selected === 'openid4vp_plain' ||
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

    const response = await callServer(
        'openid4vpBegin',
        {
            requestType: type,
            protocol: selectedProtocol,
        }
    )
    console.log("URI " + response.uri)
    window.open(response.uri, '_blank').focus()
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
