
// Keep in sync with verifier.html
var selectedProtocol = 'w3c_dc_openid4vp'

// If the user clicks on one of the protocol entries, that becomes both the selected and the
// preferred protocol. If the selected protocol is disabled (because, for instance, the user selects
// a document that doesn't support the selected protocol), the selected protocol will be updated but
// the preferred one will remain the same. Then if the preferred one is enabled again, the selection
// will change back to the preferred protocol.
var preferredProtocol = selectedProtocol

var openid4vpUri = ''

async function onLoad() {
    const protocolDropdown = document.getElementById('protocolDropdown')
    protocolDropdown.addEventListener('hide.bs.dropdown', event => {
        var target = event.clickEvent.target
        var selected = target.getAttribute('value')
        if (selected === 'w3c_dc_preview' ||
            selected === 'w3c_dc_arf' ||
            selected === 'w3c_dc_mdoc_api' ||
            selected === 'w3c_dc_openid4vp' ||
            selected === 'openid4vp_plain' ||
            selected === 'openid4vp_eudi' ||
            selected === 'openid4vp_mdoc' ||
            selected === 'openid4vp_custom') {
            selectedProtocol = selected
            preferredProtocol = selectedProtocol
            protocolDropdown.innerHTML = target.innerHTML

            const scheme = document.getElementById("scheme-form")
            scheme.hidden = (selected !== 'openid4vp_custom')

            const openid4vp_sign_request_checkbox = document.getElementById("openid4vp-sign-request")
            openid4vp_sign_request_checkbox.hidden = (selected !== 'w3c_dc_openid4vp')

            const openid4vp_encrypt_response_checkbox = document.getElementById("openid4vp-encrypt-response")
            openid4vp_encrypt_response_checkbox.hidden = (selected !== 'w3c_dc_openid4vp')
        }
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
    addTab("Raw DCQL", "rawDcql", "any", null, false)
    rawDcqlReset_mdl1()
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
    if (sampleRequests == null) {
        // Raw DCQL box
        str += '  <div class="d-grid gap-2 mx-auto"> '
        str += '    <textarea class="form-control" id="rawDclqTextArea" rows="12">'
        str += '</textarea>'
        str += '<div class="d-grid gap-2 mx-auto">'
        str += '<button type="button" class="btn btn-secondary btn-sm" onclick="rawDcqlReset_mdl1()">Reset (mDL, age_over_21 + portrait)</button> '
        str += '<button type="button" class="btn btn-secondary btn-sm" onclick="rawDcqlReset_sdjwt1()">Reset (SD-JWT VC EU PID, age_equals_or_over.18 + picture)</button> '
        str += '    <button type="button" class="btn btn-primary btn-sm" onclick="requestDocumentRawDcql()" >'
        str += 'Request'
        str += '    </button> '
        str += '</div>'
        str += '  </div> '
    } else {
        str += '  <div class="d-grid gap-2 mx-auto"> '
        for (sr of sampleRequests) {
            str += '    <button type="button" class="btn btn-primary btn-lg" '
            str += 'onclick="requestDocument(\'' + mdocOrVc + '\', \'' + docTypeOrVct + '\', \'' + sr.id + '\')" >'
            str += sr.displayName
            str += '    </button> '
        }
        str += '  </div> '
    }
    str += '</div> '

    $(str).appendTo('#pills-tabContent')

    // When one of the document tabs is selected, update the available protocol dropdown options.
    $('#pills-tab-' + tabId).on('shown.bs.tab', function (e) {
        updateProtocolOptions(mdocOrVc);
    });
}

function rawDcqlReset_mdl1() {
  const textArea = document.getElementById('rawDclqTextArea')
  textArea.value =
    '{\n' +
    '  "credentials": [\n' +
    '    {\n' +
    '      "id": "mdoc",\n' +
    '      "format": "mso_mdoc",\n' +
    '      "meta": {\n' +
    '        "doctype_value": "org.iso.18013.5.1.mDL"\n' +
    '      },\n' +
    '      "claims": [\n' +
    '        {\n' +
    '          "path": [\n' +
    '            "org.iso.18013.5.1",\n' +
    '            "age_over_21"\n' +
    '          ]\n' +
    '        },\n' +
    '        {\n' +
    '          "path": [\n' +
    '            "org.iso.18013.5.1",\n' +
    '            "portrait"\n' +
    '          ]\n' +
    '        }\n' +
    '      ]\n' +
    '    }\n' +
    '  ]\n' +
    '}\n';
}

function rawDcqlReset_sdjwt1() {
  const textArea = document.getElementById('rawDclqTextArea')
  textArea.value =
    '{\n' +
    '  "credentials": [\n' +
    '    {\n' +
    '      "id": "pid",\n' +
    '      "format": "dc+sd-jwt",\n' +
    '      "meta": {\n' +
    '        "vct_values": [\n' +
    '          "urn:eudi:pid:1"\n' +
    '        ]\n' +
    '      },\n' +
    '      "claims": [\n' +
    '        {\n' +
    '          "path": [\n' +
    '            "age_equal_or_over",\n' +
    '            "18"\n' +
    '          ]\n' +
    '        },\n' +
    '        {\n' +
    '          "path": [\n' +
    '            "picture"\n' +
    '          ]\n' +
    '        }\n' +
    '      ]\n' +
    '    }\n' +
    '  ]\n' +
    '}\n';
}

function updateProtocolOptions(mdocOrVc) {
    const protocolDropdown = document.getElementById('protocolDropdown')
    const mdocOnly = document.querySelectorAll('.mdoc-only');

    if (mdocOrVc === 'mdoc') {
        // Enable mdoc-only options for mdoc entries
        mdocOnly.forEach(option => {
            option.classList.remove('disabled');
            option.removeAttribute('disabled');
            // If the preferred protocol was just reenabled, set it as the selected protocol.
            if (preferredProtocol == option.getAttribute('value')) {
                selectedProtocol = preferredProtocol
                protocolDropdown.innerHTML = option.innerHTML;
            }
        });
    } else {
        // Disable mdoc-only options for non-mdoc entries
        mdocOnly.forEach(option => {
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

    const openid4vp_sign_request_checkbox = document.getElementById("openid4vp-sign-request")
    openid4vp_sign_request_checkbox.hidden = (selectedProtocol !== 'w3c_dc_openid4vp')
    const openid4vp_encrypt_response_checkbox = document.getElementById("openid4vp-encrypt-response")
    openid4vp_encrypt_response_checkbox.hidden = (selectedProtocol !== 'w3c_dc_openid4vp')
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

async function requestDocumentRawDcql() {
    const textArea = document.getElementById('rawDclqTextArea')
    const rawDcql = textArea.value
    console.log('requestDocumentRawDcql, rawDcql=' + rawDcql)
    if (selectedProtocol != "w3c_dc_openid4vp") {
        alert("Only OpenID4VP supports Raw DCQL.")
        return
    }
    try {
        const response = await callServer(
            'dcBeginRawDcql',
            {
                rawDcql: rawDcql,
                origin: location.origin,
                host: location.host,
                signRequest: document.getElementById("openid4vp-sign-request-input").checked,
                encryptResponse: document.getElementById("openid4vp-encrypt-response-input").checked
            }
        )
        dcRequestCredential(response.sessionId, 'openid4vp', JSON.parse(response.dcRequestString))
    } catch (err) {
        alert("Something went wrong: " + err)
    }
}

async function requestDocument(format, docType, requestId) {
    console.log('requestDocument, format=' + format + ' docType=' + docType + ' requestId=' + requestId + ' protocol=' + selectedProtocol)
    if (selectedProtocol === 'openid4vp_custom') {
        if (document.getElementById("scheme-input").value === "") {
            alert("You must specify a non-empty scheme when performing a custom OpenID4VP request.")
            return
        }
        const response = await callServer(
            'openid4vpBegin',
            {
                format: format,
                docType: docType,
                requestId: requestId,
                protocol: selectedProtocol,
                origin: location.origin,
                host: location.host,
                scheme: document.getElementById("scheme-input").value
            }
        )
        console.log("URI " + response.uri)
        window.open(response.uri, '_blank').focus()
    } else if (selectedProtocol.startsWith('openid4vp_')) {
              const response = await callServer(
                  'openid4vpBegin',
                  {
                      format: format,
                      docType: docType,
                      requestId: requestId,
                      protocol: selectedProtocol,
                      origin: location.origin,
                      host: location.host,
                      scheme: ""
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
                    host: location.host,
                    signRequest: false,
                    encryptResponse: true
                }
            )
            dcRequestCredential(response.sessionId, 'preview', JSON.parse(response.dcRequestString))
        } catch (err) {
            alert("Something went wrong: " + err)
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
                    host: location.host,
                    signRequest: true,
                    encryptResponse: true
                }
            )
            dcRequestCredential(response.sessionId, 'austroads-request-forwarding-v2', JSON.parse(response.dcRequestString))
        } catch (err) {
            alert("Something went wrong: " + err)
        }
    } else if (selectedProtocol === "w3c_dc_mdoc_api") {
        try {
            const response = await callServer(
                'dcBegin',
                {
                    format: format,
                    docType: docType,
                    requestId: requestId,
                    protocol: selectedProtocol,
                    origin: location.origin,
                    host: location.host,
                    signRequest: true,
                    encryptResponse: true
                }
            )
            dcRequestCredential(response.sessionId, 'org-iso-mdoc', JSON.parse(response.dcRequestString))
        } catch (err) {
            alert("Something went wrong: " + err)
        }
    } else if (selectedProtocol === "w3c_dc_openid4vp") {
        try {
            const response = await callServer(
                'dcBegin',
                {
                    format: format,
                    docType: docType,
                    requestId: requestId,
                    protocol: selectedProtocol,
                    origin: location.origin,
                    host: location.host,
                    signRequest: document.getElementById("openid4vp-sign-request-input").checked,
                    encryptResponse: document.getElementById("openid4vp-encrypt-response-input").checked
                }
            )
            dcRequestCredential(response.sessionId, 'openid4vp', JSON.parse(response.dcRequestString))
        } catch (err) {
            alert("Something went wrong: " + err)
        }
    }
}

async function dcRequestCredential(sessionId, dcRequestProtocol, dcRequest) {
    if (!navigator.credentials || !navigator.credentials.get) {
        alert("Digital Credentials API is not available. Please enable it via chrome://flags#web-identity-digital-credentials.");
        return;
    }
    try {
        console.log('protocol: ', dcRequestProtocol)
        console.log('request: ', dcRequest)
        const credentialResponse = await navigator.credentials.get({
            digital: {
                requests: [{
                    protocol: dcRequestProtocol,
                    data: dcRequest
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