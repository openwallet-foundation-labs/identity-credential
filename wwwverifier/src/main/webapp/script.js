const BASE_URL = '/request-mdl';
const CREATE_SESSION_URL = '/create-new-session';
const DISPLAY_RESPONSE_URL = '/display-response';
const DISPLAY_LOGS_URL = '/display-logs';

const MDOC_URI_ID = 'mdoc-uri-text'
const RESPONSE_ID = 'response-display';
const LOGS_ID = 'logs'
const PORTRAIT_ID = 'portrait-render'

const INTERVAL_MS = 5000; // 5 seconds

const CHECKMARK_PLACEHOLDER = '*';
const CHECKMARK_UNICODE = '\u2705';
const CROSS_PLACEHOLDER = '+';
const CROSS_UNICODE = '\u274c';
const BOLD_PLACEHOLDER = '#';

const AGE_ONLY_REQUEST = 'age';
const ALL_LICENSE_HOLDER_REQUEST = 'all';

var sessionID = '';

var devResponseInterval = window.setInterval(getDeviceResponse, INTERVAL_MS);
var readLogsInterval = window.setInterval(readLogs, INTERVAL_MS);
var logTextArr = '';

function readLogs() {
    if (sessionID.length == 0) return;


    fetch(BASE_URL + DISPLAY_LOGS_URL + '/' + String(sessionID)).then(response => response.text()).then((responseText) => {
        if (responseText.length > 1) {            
            var textArr = responseText.substring(0, responseText.length - 2).split('\n');
            if (responseText === logTextArr) return;
            logTextArr = responseText;
            
            document.getElementById(LOGS_ID).innerHTML = '';

            // create text instruction
            var log_caption = document.createElement('div');
            log_caption.innerHTML = 'Logs for Interop Event';
            document.getElementById(LOGS_ID).append(document.createElement('br'));
            document.getElementById(LOGS_ID).appendChild(log_caption);

            var table = document.createElement('table');
            
            for (var i = 0; i < textArr.length; i++) {
                var row = table.insertRow(i);
                var rowText = textArr[i]
                row.insertCell(0).innerHTML = rowText;
            }
            document.getElementById(LOGS_ID).append(table);
        }
    });
}

function createSession(requestedAttributes) {
    fetch(BASE_URL + CREATE_SESSION_URL + '?' + new URLSearchParams({ requested_attributes: requestedAttributes }).toString(),
        {
            method: "get", 
        })
    .then(response => response.text()).then((responseText) => {
        var responseArr = responseText.split(',');
        var mdocURL = responseArr[0];
        sessionID = responseArr[1];

        // create text instruction
        var uri_instruction = document.createElement('div');
        uri_instruction.innerHTML = 'Click on the following link to proceed to the MDoc application:';
        document.getElementById(MDOC_URI_ID).appendChild(uri_instruction);
        
        // create link
        var a = document.createElement('a');
        a.href = mdocURL;
        a.referrerPolicy = 'unsafe-url';
        a.innerHTML = mdocURL;
        document.getElementById(MDOC_URI_ID).appendChild(a);
    });
    document.getElementById('buttons').style.display = 'none';
}

function getDeviceResponse() {
    if (sessionID.length == 0) return;

    document.getElementById(RESPONSE_ID).innerHTML = '';
    fetch(BASE_URL + DISPLAY_RESPONSE_URL + '/' + String(sessionID)).then(response => response.text()).then((responseText) => {
        if (responseText.length > 1) {
            var table = document.createElement('table');
            var textArr = responseText.substring(1, responseText.length - 2).split(',');
            for (var i = 0; i < textArr.length; i++) {
                var row = table.insertRow(i);
                var rowText = textArr[i].substring(1, textArr[i].length - 1).split(':');
                var rowKey = rowText[0];
                if (rowKey === 'portraitBytes') {
                    var rowVal = rowText[1].trim();
                    document.getElementById(PORTRAIT_ID).src = "data:image/jpeg;base64," + rowVal;
                    continue;
                } 

                if (rowKey.charAt(0) == CHECKMARK_PLACEHOLDER) {
                    rowKey = CHECKMARK_UNICODE + rowKey.substring(1);
                } else if (rowKey.charAt(0) == CROSS_PLACEHOLDER) {
                    rowKey = CROSS_UNICODE + rowKey.substring(1);                    
                }
                if (rowKey.charAt(0) == BOLD_PLACEHOLDER) {
                    rowKey = rowKey.substring(1).bold();
                }
                row.insertCell(0).innerHTML = rowKey;

                if (rowText.length == 2) {
                    var rowVal = rowText[1].trim();
                    if (rowVal.charAt(0) == BOLD_PLACEHOLDER) {
                        rowVal = rowVal.substring(1).bold();
                    }
                    row.insertCell(1).innerHTML = rowVal;
                }
                
            }
            document.getElementById(RESPONSE_ID).append(table);
            window.clearInterval(devResponseInterval);
        }
    });
}