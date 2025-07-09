identityList()

async function identityList() {
    let base = location.pathname.substring(0, location.pathname.lastIndexOf("/") + 1);
    let list = await (await fetch(base + "identity/list", {
       method: 'POST'
    })).json();
    let container = document.getElementById("list");
    for (let token of list) {
        let div = document.createElement("div")
        container.appendChild(div)
        fetchIdentity(base, div, token)
    }
}

async function fetchIdentity(base, div, token) {
    let identity = await (await fetch(base + "identity/get", {
       method: 'POST',
       headers: {
           'Content-Type': 'application/json',
       },
       body: JSON.stringify({
         token: token,
         core: ['family_name', 'given_name', 'birth_date'],
         records: {}
       })
    })).json()
    let core = identity.core
    let link = document.createElement("a");
    link.textContent = core.family_name + ", " + core.given_name + " " + core.birth_date;
    link.href = "person.html?token=" + token;
    div.append(link);
}
