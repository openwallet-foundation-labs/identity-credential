const GET_URL = '/request-mdl';
const CREATE_SESSION_URL = '/create-new-session';
const DISPLAY_RESPONSE_URL = '/display-response';

const MDOC_URI_ID = 'mdoc-uri-text'
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
        document.getElementById(MDOC_URI_ID).appendChild(a);
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