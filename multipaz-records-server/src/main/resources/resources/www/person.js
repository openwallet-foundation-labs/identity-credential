load()

async function load() {
    let token = new URLSearchParams(location.search).get("token");
    let base = location.pathname.substring(0, location.pathname.lastIndexOf("/") + 1);
    let rawSchema = await (await fetch(base + "identity/schema")).json();
    let byRecordTypeId = {};
    let recordTypeSelect = document.getElementById("recordType")
    for (let recordType of rawSchema) {
        if (recordType.identifier != "core") {
            let option = document.createElement("option");
            option.value = recordType.identifier;
            option.textContent = recordType.display_name;
            recordTypeSelect.appendChild(option);
        }
        byRecordTypeId[recordType.identifier] = recordType;
    }
    let coreRecordType = byRecordTypeId.core;
    let coreDiv = document.getElementById("core");
    let recordsDiv = document.getElementById("records");
    let inputs = {
        core: addRecordSection(coreDiv, coreRecordType),
        records: {}
    }
    if (token) {
        let fields = [];
        let records = {}
        for (let attribute of coreRecordType.type.attributes) {
            fields.push(attribute.identifier);
        }
        for (let recordTypeId in byRecordTypeId) {
            if (recordTypeId != "code") {
                records[recordTypeId] = [];
            }
        }
        let request = {
            token: token,
            core: fields,
            records: records
        };
        let data = await(await fetch(base + "identity/get", {
           method: 'POST',
           headers: {
               'Content-Type': 'application/json',
           },
           body: JSON.stringify(request)
        })).json();
        inputs.token = token;
        setSectionData(inputs.core, data.core, coreRecordType);
        for (let recordTypeId in data.records) {
            let recordsData = data.records[recordTypeId];
            let records = {};
            inputs.records[recordTypeId] = records;
            let recordType = byRecordTypeId[recordTypeId];
            for (let recordId in recordsData) {
                let section = addRecordSection(recordsDiv, recordType, recordId);
                records[recordId] = section;
                setSectionData(section, recordsData[recordId], recordType);
            }
        }
    }
    document.getElementById("addRecord").addEventListener("click", function() {
        let typeId = recordTypeSelect.value;
        let recordType = byRecordTypeId[typeId];
        if (recordType != null) {
            let records = inputs.records[typeId];
            if (!records) {
                records = {};
                inputs.records[typeId] = records;
            }
            let recordId = Math.random().toString(36).substring(2);
            records[recordId] = addRecordSection(recordsDiv, recordType, recordId);
        }
    });
    document.getElementById("save").addEventListener("click", function() {
        save(base, inputs, byRecordTypeId);
    });
    document.getElementById("delete").addEventListener("click", function() {
        remove(base, inputs.token);
    });
}

let idIndex = 0

function addRecordSection(containerDiv, recordType, recordId) {
    let section = {}
    if (recordId) {
        section._id = recordId;
    }
    let div = document.createElement("div");
    div.className = recordId ? "record" : "core";
    containerDiv.appendChild(div);
    let header = document.createElement("h3")
    header.textContent = recordType.display_name;
    div.appendChild(header)
    let controlsHolder = document.createElement("div");
    controlsHolder.style.display = "inline-block";
    controlsHolder.style.verticalAlign = "top";
    div.appendChild(controlsHolder);
    let controls = document.createElement("div");
    controls.className = "controls";
    controlsHolder.appendChild(controls);
    for (let attribute of recordType.type.attributes) {
        let id = "id" + (idIndex++);
        let row = document.createElement("div");
        controls.appendChild(row);
        row.className = "row"
        let label = document.createElement("label");
        label.setAttribute("for", id)
        label.textContent = attribute.display_name + ": ";
        row.appendChild(label);
        let input;
        if (typeof attribute.type == "object") {
            if (attribute.type.type == "options") {
                input = document.createElement("select");
                for (let id in attribute.type.options) {
                    let option = document.createElement("option");
                    option.value = id;
                    option.textContent = attribute.type.options[id];
                    input.appendChild(option);
                }
            } else {
                throw new Error("unsupported type")
            }
        } else if (attribute.type == "picture") {
            input = document.createElement("input");
            input.type = "file";
            input.accept = "image/jpeg";
            let container = document.createElement("div");
            container.style.display = "inline-block";
            container.style.verticalAlign = "top";
            container.style.margin = "0px 1em";
            container.style.padding = "0.5em";
            container.style.background = "#EEE";
            let image = document.createElement("img");
            image.style.height = "8em"
            image.style.width = "auto"
            image.style.display = "block";
            let caption = document.createElement("div")
            caption.textContent = attribute.display_name;
            caption.style.textAlign = "center";
            let imageId = "id" + (idIndex++);
            input.dataset.image = imageId;
            image.setAttribute("id", imageId);
            container.appendChild(image);
            container.appendChild(caption);
            div.appendChild(container);
            input.addEventListener('change', imageSelected);
        } else {
            input = document.createElement("input");
            if (attribute.type == "date") {
                input.type = "date";
            } else {
                input.type = "text";
            }
        }
        if (!recordId && attribute.identifier == "utopia_id_number") {
            input.readOnly = true
        }
        row.appendChild(input);
        input.setAttribute("id", id);
        section[attribute.identifier] = input;
    }
    if (recordId != null) {
        let actions = document.createElement("div");
        div.appendChild(actions);
        let remove = document.createElement("button");
        remove.textContent = "Delete record";
        div.appendChild(remove);
        remove.addEventListener("click", function() {
            containerDiv.removeChild(div);
            section._removed = true;
        });
    }
    return section;
}

let dataUrlPrefix = "data:image/jpeg;base64,";

function imageSelected(evt) {
    let input = evt.target;
    let image = document.getElementById(input.dataset.image);
    let file = input.files[0];
    if (!file) {
        return;
    }
    const reader = new FileReader();
    reader.onload = function(e) {
        const base64 = btoa(e.target.result);
        image.src = dataUrlPrefix + base64;
    };
    reader.readAsBinaryString(file);
}

async function save(base, inputs, recordTypes) {
    let data = {records:{}};
    let command;
    if (inputs.token) {
        command = "update";
        data.token = inputs.token;
    } else {
        command = "create";
    }
    data.core = readSection(inputs.core, recordTypes.core);
    for (let recordTypeId in inputs.records) {
        let records = {};
        let sections = inputs.records[recordTypeId];
        data.records[recordTypeId] = records;
        let recordType = recordTypes[recordTypeId];
        for (let recordId in sections) {
            records[recordId] = readSection(sections[recordId], recordType)
        }
    }
    let response = await (await fetch(base + "identity/" + command, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(data)
    })).json();
    if (!inputs.token && response.token) {
        inputs.token = response.token;
        let src = location.href;
        let index = src.indexOf("?")
        if (index > 0) {
            src = src.substring(0, index)
        }
        location.replace(src + "?token=" + response.token);
    }
}

function readSection(section, recordType) {
    if (section._removed) {
        return null;
    }
    let record = {};
    for (let attribute of recordType.type.attributes) {
        let input = section[attribute.identifier];
        let value;
        if (typeof attribute.type == "object") {
            if (attribute.type.type == "options") {
                value = input.value;
            } else {
                throw new Error("unsupported type")
            }
        } else if (attribute.type == "picture") {
            let image = document.getElementById(input.dataset.image);
            value = image.src.substring(dataUrlPrefix.length);
        } else {
            value = input.value;
        }
        record[attribute.identifier] = value;
    }
    return record;
}

function setSectionData(section, data, recordType) {
    for (let attribute of recordType.type.attributes) {
        let input = section[attribute.identifier];
        let value = data[attribute.identifier];
        if (typeof attribute.type == "object") {
            if (attribute.type.type == "options") {
                input.value = value;
            } else {
                throw new Error("unsupported type")
            }
        } else if (attribute.type == "picture") {
            let image = document.getElementById(input.dataset.image);
            image.src = dataUrlPrefix + value;
        } else {
            input.value = value;
        }
    }
}

async function remove(base, token) {
    let response = await (await fetch(base + "identity/delete", {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({token: token})
    })).json();
    if (response.deleted) {
        location.href = "index.html";
    }
}
