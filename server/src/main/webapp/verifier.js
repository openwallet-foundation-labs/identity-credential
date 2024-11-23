
// Keep in sync with verifier.html
var selectedProtocol = 'w3c_dc_preview'
// If the user clicks on one of the protocol entries, that becomes both the selected and the
// preferred protocol. If the selected protocol is disabled (because, for instance, the user selects
// a document that doesn't support the selected protocol), the selected protocol will be updated but
// the preferred one will remain the same. Then if the preferred one is enabled again, the selection
// will change back to the preferred protocol.
var preferredProtocol = selectedProtocol

var openid4vpUri = ''

var selectedResponseMode = 'direct_post.jwt'

async function onLoad() {
    const protocolDropdown = document.getElementById('protocolDropdown')
    protocolDropdown.addEventListener('hide.bs.dropdown', event => {
        var target = event.clickEvent.target
        var selected = target.getAttribute('value')
        if (selected === 'w3c_dc_preview' ||
            selected === 'w3c_dc_arf' ||
            selected === 'openid4vp_plain' ||
            selected === 'openid4vp_eudi' ||
            selected === 'openid4vp_mdoc' ||
            selected === 'openid4vp_custom') {
            selectedProtocol = selected
            preferredProtocol = selectedProtocol
            protocolDropdown.innerHTML = target.innerHTML

            const scheme = document.getElementById("scheme-form");
            scheme.hidden = selected !== 'openid4vp_custom';
        }
    })
    const responseModeDropdown = document.getElementById('responseModeDropdown')
    responseModeDropdown.addEventListener('hide.bs.dropdown', event => {
        var target = event.clickEvent.target
        responseModeDropdown.innerHTML = target.innerHTML
        selectedResponseMode = target.innerHTML
    })

    // Ask server what document types / requests are available and use this to
    // dynamically generate the UI..
    //
    const response = await callServer(
        'getAvailableRequests', {}
    )
    var active = true
    for (const dtwr of response.documentTypesWithRequests) {
      if (dtwr.mdocDocType != null) {
          var tabId = "mdoc-" + dtwr.mdocDocType
          addTab(dtwr.documentDisplayName + " (mdoc)", "mdoc", dtwr.mdocDocType, dtwr.sampleRequests, active)
          active = false
      }
      if (dtwr.vcVct != null) {
          var tabId = "vc-" + dtwr.vcVct
          addTab(dtwr.documentDisplayName + " (VC)", "vc", dtwr.vcVct, dtwr.sampleRequests, active)
          active = false
      }
    }
}

function addTab(tabName, mdocOrVc, docTypeOrVct, sampleRequests, active) {
    // For the tab ID to be queryable using jQuery, we need to mask out special characters. Replace
    // anything that isn't a letter or number.
    var escapedDocTypeOrVct = docTypeOrVct.replace(/[^a-zA-Z0-9]/g,'_');
    var tabId = mdocOrVc + '-' + escapedDocTypeOrVct
    var activeStr = active ? "active" : ""
    $('<li class="nav-item" role="presentation">' +
    '<button class="nav-link ' + activeStr + '" data-bs-toggle="pill" id="pills-tab-' + tabId + '" data-bs-target="#pills-' + tabId + '" type="button" role="tab" aria-controls="pills-home" aria-selected="true">' +
      tabName +
    '</button>' +
    '</li>')
    .appendTo('#pills-tab')

    var str = '<div class="tab-pane fade show ' + activeStr + '" '
    str += 'id="pills-' + tabId + '" role="tabpanel" '
    str += 'aria-labelledby="pills-tab-' + tabId + '" tabindex="0"> '
    str += '  <div class="d-grid gap-2 mx-auto"> '
    for (sr of sampleRequests) {
        str += '    <button type="button" class="btn btn-primary btn-lg" '
        str += 'onclick="requestDocument(\'' + mdocOrVc + '\', \'' + docTypeOrVct + '\', \'' + sr.id + '\')" >'
        str += sr.displayName
        str += '    </button> '
    }
    str += '  </div> '
    str += '</div> '

    $(str).appendTo('#pills-tabContent')

    // When one of the document tabs is selected, update the available protocol dropdown options.
    $('#pills-tab-' + tabId).on('shown.bs.tab', function (e) {
        updateProtocolOptions(mdocOrVc);
    });
}

function updateProtocolOptions(mdocOrVc) {
    const protocolDropdown = document.getElementById('protocolDropdown')
    const w3cOptions = document.querySelectorAll('.w3c-option');

    if (mdocOrVc === 'mdoc') {
        // Enable W3C options for mdoc entries
        w3cOptions.forEach(option => {
            option.classList.remove('disabled');
            option.removeAttribute('disabled');
            // If the preferred protocol was just reenabled, set it as the selected protocol.
            if (preferredProtocol == option.getAttribute('value')) {
                selectedProtocol = preferredProtocol
                protocolDropdown.innerHTML = option.innerHTML;
            }
        });
    } else {
        // Disable W3C options for non-mdoc entries
        w3cOptions.forEach(option => {
            option.classList.add('disabled');
            option.setAttribute('disabled', 'disabled');
            if (selectedProtocol == option.getAttribute('value')) {
                selectedProtocol = null
            }
        });
        // If the selected protocol was disabled, select the next non-disabled protocol.
        if (selectedProtocol == null) {
            const firstEnabledOption = document.querySelector('.dropdown-item:not(.disabled)');
            selectedProtocol = firstEnabledOption.getAttribute('value');
            protocolDropdown.innerHTML = firstEnabledOption.innerHTML;
        }
    }
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

async function requestDocument(format, docType, requestId) {
    console.log('requestDocument, format=' + format + ' docType=' + docType + ' requestId=' + requestId + ' protocol=' + selectedProtocol)
    let scheme = ''
    if (selectedProtocol === 'openid4vp_custom') {
        if (document.getElementById("scheme-input").value === "") {
            alert("You must specify a non-empty scheme when performing a custom OpenID4VP request.")
            return
        }
        scheme = document.getElementById("scheme-input").value
    }

    if (selectedProtocol.startsWith('openid4vp_')) {
          const response = await callServer(
              'openid4vpBegin',
              {
                  format: format,
                  docType: docType,
                  requestId: requestId,
                  protocol: selectedProtocol,
                  origin: location.origin,
                  scheme: scheme,
                  responseMode: selectedResponseMode
              }
          )
          console.log("URI " + response.uri)
          window.open(response.uri, '_blank').focus()
    } else if (selectedProtocol === "w3c_dc_preview") {
        try {
            const response = await callServer(
                'dcBegin',
                {
                    format: format,
                    docType: docType,
                    requestId: requestId,
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
                    format: format,
                    docType: docType,
                    requestId: requestId,
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
    var dataStr
    if (typeof(credentialResponse.data) == 'string') {
	dataStr = credentialResponse.data
    } else {
	dataStr = JSON.stringify(credentialResponse.data)
    }
    const response = await callServer(
        'dcGetData',
        {
            sessionId: sessionId,
            credentialResponse: dataStr
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