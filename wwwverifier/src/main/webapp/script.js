const GET_URL = '/request-mdl';
const CREATE_SESSION_URL = '/create-new-session';
const DISPLAY_RESPONSE_URL = '/display-response';

const QRCODE_ID = 'qrcode';
const QRCODE_TEXT_ID = 'qrcode-text'
const RESPONSE_ID = 'response-display';

const INTERVAL_MS = 5000; // 5 seconds

var sessionID = '';

window.onload = onLoad;
window.setInterval(getDeviceResponse, INTERVAL_MS);

function onLoad() {
    fetch(GET_URL + CREATE_SESSION_URL).then(response => response.text()).then((responseText) => {
        var responseArr = responseText.split(',');
        var mdocURL = responseArr[0];
        sessionID = responseArr[1];
        var a = document.createElement('a');
        a.href = mdocURL;
        a.referrerPolicy = 'unsafe-url';
        a.innerHTML = mdocURL;
        document.getElementById(QRCODE_TEXT_ID).appendChild(a);
        new QRious({
            element: document.getElementById(QRCODE_ID),
            background: '#ffffff',
            backgroundAlpha: 1,
            foreground: '#000000',
            foregroundAlpha: 1,
            level: 'H',
            padding: 0,
            size: 300,
            value: String(mdocURL)
        });
    });
}

function getDeviceResponse() {
    if (sessionID.length == 0) return;

    document.getElementById(RESPONSE_ID).innerHTML = '';
    fetch(GET_URL + DISPLAY_RESPONSE_URL + '/' + String(sessionID)).then(response => response.text()).then((responseText) => {
        if (responseText.length != 0) {
            var table = document.createElement('table');
            var textArr = responseText.substring(1, responseText.length - 1).split(',');
            for (var i = 0; i < textArr.length; i++) {
                var row = table.insertRow(i);
                var rowText = textArr[i].substring(1, textArr[i].length - 1).split(':');
                row.insertCell(0).innerHTML = rowText[0];
                if (rowText.length == 2) {
                    row.insertCell(1).innerHTML = rowText[1];
                }
            }
            document.getElementById(RESPONSE_ID).append(table);
        }
    });
}