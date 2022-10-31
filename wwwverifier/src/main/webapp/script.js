const GET_URL = '/request-mdl?request-type=';
const GET_URL_CREATE = 'create-uri';
const GET_URL_DISPLAY = 'display-response';

const QRCODE_ID = 'request-qrcode';
const RESPONSE_ID = 'response-confirmation';

function requestMDL() {
    fetch(GET_URL + GET_URL_CREATE).then(response => response.text()).then((responseText) => {
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
  
 function getDeviceResponse() {
    fetch(GET_URL + GET_URL_DISPLAY).then(response => response.text()).then((responseText) => {
        document.getElementById(RESPONSE_ID).innerText = responseText;
    });
 } 