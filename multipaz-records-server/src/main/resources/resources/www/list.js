identityList()

async function identityList() {
    let base = location.pathname.substring(0, location.pathname.lastIndexOf("/") + 1);
    let list = await (await fetch(base + "identity/list", {
       method: 'POST'
    })).json();
    let table = document.getElementById("list");
    for (let token of list) {
        let row = document.createElement("tr")
        table.appendChild(row)
        fetchIdentity(base, row, token)
    }
}

async function fetchIdentity(base, row, token) {
    let identity = await (await fetch(base + "identity/get", {
       method: 'POST',
       headers: {
           'Content-Type': 'application/json',
       },
       body: JSON.stringify({
         token: token,
         core: ['family_name', 'given_name', 'utopia_id_number'],
         records: {}
       })
    })).json()
    let core = identity.core
    let last = document.createElement("td")
    last.textContent = core.family_name
    row.appendChild(last)
    let first = document.createElement("td")
    first.textContent = core.given_name
    row.appendChild(first)
    let idnum = document.createElement("td")
    let link = document.createElement("a");
    idnum.appendChild(link);
    link.textContent = core.utopia_id_number;
    link.href = "person.html?token=" + token;
    row.appendChild(idnum);
}
