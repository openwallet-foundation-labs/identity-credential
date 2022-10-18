function requestMDL() {
    fetch('/request-mdl').then(response => response.text()).then((responseText) => {
        document.getElementById('request-confirmation').innerText = responseText;
        new QRious({
            element: document.getElementById('request-qrcode'),
            background: '#ffffff',
            backgroundAlpha: 1,
            foreground: '#000000',
            foregroundAlpha: 1,
            level: 'H',
            padding: 0,
            size: 300,
            value: String(responseText)
        });
    });
}

function getDeviceResponse() {
    fetch('/device-response').then(response => response.text()).then((responseText) => {
        document.getElementById('response-confirmation').innerText = responseText;
    });
}