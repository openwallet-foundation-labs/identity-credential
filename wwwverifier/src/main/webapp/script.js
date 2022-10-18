// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

function requestMDL() {
    fetch('/request-mdl').then(response => response.text()).then((responseText) => {
        document.getElementById('request-confirmation').innerText = responseText;
        let qrcodeContainer = document.getElementById('request-qrcode');
        qrcodeContainer.innerHTML = "";
        new QRious({
          element: qrcodeContainer,
          value: responseText
        });
    });
}

function getDeviceResponse() {
    fetch('/device-response').then(response => response.text()).then((responseText) => {
        document.getElementById('response-confirmation').innerText = responseText;
    });
}