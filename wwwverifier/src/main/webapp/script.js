const GET_URL = '/request-mdl?request-type=';
const GET_URL_CREATE = 'create-uri';
const GET_URL_DISPLAY = 'display-response';
const GET_URL_RESET = 'reset';

const QRCODE_ID = 'request-qrcode';
const QRCODE_TEXT_ID = 'request-confirmation-text'
const RESPONSE_ID = 'response-confirmation';
const RESET_ID = 'reset-confirmation';

function requestMDL() {
    fetch(GET_URL + GET_URL_CREATE).then(response => response.text()).then((responseText) => {
        var a = document.createElement('a');
        a.href = responseText;
        a.innerHTML = responseText;
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
            value: String(responseText)
        });
    });
 }

window.setInterval(getDeviceResponse, 5000);
  
function getDeviceResponse() {
    document.getElementById(RESPONSE_ID).innerHTML = "";
    fetch(GET_URL + GET_URL_DISPLAY).then(response => response.text()).then((responseText) => {
        if (responseText.length != 0) {
            var table = document.createElement("table");
            var textArr = responseText.substring(1, responseText.length - 1).split(",");
            for (var i = 0; i < textArr.length; i++) {
                var row = table.insertRow(i);
                var rowText = textArr[i].substring(1, textArr[i].length - 1).split(":");
                row.insertCell(0).innerHTML = rowText[0];
                if (rowText.length == 2) {
                    row.insertCell(1).innerHTML = rowText[1];
                }
            }
            document.getElementById(RESPONSE_ID).append(table);
        }
    });
} 

function resetServlet() {
    fetch(GET_URL + GET_URL_RESET).then(response => response.text()).then((responseText) => {
        document.getElementById(RESPONSE_ID).innerText = "";
        document.getElementById(QRCODE_TEXT_ID).innerText = "";
        new QRious({
            element: document.getElementById(QRCODE_ID),
            background: '#ffffff',
            backgroundAlpha: 1,
            foreground: '#ffffff',
            foregroundAlpha: 1,
            level: 'H',
            padding: 0,
            size: 300,
            value: ""
        });
        document.getElementById(RESET_ID).innerText = responseText;
    });
}