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

 const interval = setInterval(getDeviceResponse(), 5000);
  
 function getDeviceResponse() {
    fetch(GET_URL + GET_URL_DISPLAY).then(response => response.text()).then((responseText) => {
        document.getElementById(RESPONSE_ID).innerText = responseText;
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