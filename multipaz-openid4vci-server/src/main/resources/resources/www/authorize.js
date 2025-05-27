init(
   document.getElementById("verify"),
   document.getElementById("pidData"),
   document.getElementById("form")
)

async function init(button, pidData, form) {
    button.disabled = true;
    if (!navigator.identity) {
        alert("This browser does not support digital credential reading");
        return;
    }
    const code = button.dataset.code;
    const response = await fetch('credential_request', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({code: code})
        })
    const credentialRequest =
        response.headers.get("Content-Type") == "application/json"
            ? await response.json()
            : await response.text();
    button.disabled = false;
    button.addEventListener('click', async (evt) => {
        evt.preventDefault();  // avoid form submission from click
        try {
            const credentialResponse = await navigator.identity.get({
                        digital: {
                           providers: [{
                               protocol: "openid4vp",
                               request: JSON.stringify({request: credentialRequest})
                           }]
                        },
                        mediation: 'required'
                    });
            const data = credentialResponse.data;
            if (typeof data == 'string') {
                pidData.value = JSON.parse(data).response;
            } else {
                pidData.value = data.response;
            }
            form.submit();
        } catch (err) {
            alert("Error presenting credentials: '" + err + "'")
        }
    });
}