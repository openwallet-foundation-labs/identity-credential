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
    let code = button.dataset.code;
    let response = await fetch('credential_request', {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({code: code})
        })
    let credentialRequest = await response.json();
    button.disabled = false;
    button.addEventListener('click', async (evt) => {
        evt.preventDefault();  // avoid form submission from click
        const credentialResponse = await navigator.identity.get({digital: credentialRequest});
        pidData.value = credentialResponse.data;
        form.submit();
    });
}