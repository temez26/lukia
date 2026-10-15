// The server's one page: set up the first account, sign in, add books to the
// shared library, remove them (admin), make invite codes and reset passwords
// (admin).
//
// It speaks the same /api/v1 the app does. Tokens live in this script's memory
// only, so closing or reloading the page signs out - nothing is left in the
// browser for anything else to read.
//
// Every value from the server is put on the page as text, never as HTML: a
// book's title is whatever its file says it is.

"use strict";

const api = "/api/v1";
let tokens = null;
let account = null;

const $ = (id) => document.getElementById(id);

function show(id, visible) {
    $(id).hidden = !visible;
}

function say(text, isError = false) {
    const message = $("message");
    message.textContent = text;
    message.classList.toggle("error", isError);
    message.hidden = !text;
}

async function errorText(response) {
    try {
        return (await response.json()).message;
    } catch {
        return `The server answered ${response.status}.`;
    }
}

/** A request with the access token, refreshed once if it has expired. */
async function call(path, options = {}) {
    const send = () => fetch(api + path, {
        ...options,
        headers: { ...(options.headers || {}), Authorization: `Bearer ${tokens.accessToken}` },
    });

    let response = await send();
    if (response.status === 401 && tokens) {
        const refreshed = await fetch(`${api}/auth/refresh`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ refreshToken: tokens.refreshToken }),
        });
        if (!refreshed.ok) {
            signedOut("Your session has ended. Sign in again.");
            throw new Error("signed out");
        }
        tokens = await refreshed.json();
        response = await send();
    }
    return response;
}

function postJson(path, body) {
    return fetch(api + path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
    });
}

async function start() {
    const response = await fetch(`${api}/health`);
    if (!response.ok) {
        say("The server is not answering.", true);
        return;
    }
    const health = await response.json();
    $("server-name").textContent = health.name;
    document.title = `${health.name} - Lukia`;
    show("setup", health.setupRequired);
    show("sign-in", !health.setupRequired);
}

async function signedIn(newTokens) {
    tokens = newTokens;
    const response = await call("/me");
    account = await response.json();

    $("who-name").textContent = account.isAdmin ? `${account.username} (admin)` : account.username;
    show("who", true);
    show("setup", false);
    show("sign-in", false);
    show("library", true);
    show("invites", account.isAdmin);
    show("passwords", account.isAdmin);
    say("");
    await loadBooks();
}

function signedOut(text = "") {
    tokens = null;
    account = null;
    show("who", false);
    show("library", false);
    show("invites", false);
    show("invite-code", false);
    show("passwords", false);
    show("sign-in", true);
    $("books").replaceChildren();
    say(text, Boolean(text));
}

async function submitCredentials(event, path) {
    event.preventDefault();
    const form = event.target;
    const button = form.querySelector("button");
    button.disabled = true;
    try {
        const response = await postJson(path, {
            username: form.username.value.trim(),
            password: form.password.value,
        });
        if (!response.ok) {
            say(await errorText(response), true);
            return;
        }
        form.reset();
        await signedIn(await response.json());
    } finally {
        button.disabled = false;
    }
}

async function loadBooks() {
    const books = new Map();
    let since = 0;
    for (;;) {
        const page = await (await call(`/books?since=${since}`)).json();
        for (const book of page.items) {
            if (book.deleted) books.delete(book.id); else books.set(book.id, book);
        }
        since = page.cursor;
        if (!page.hasMore) break;
    }

    const list = $("books");
    list.replaceChildren(...[...books.values()].reverse().map(bookRow));
    show("empty", books.size === 0);
}

function bookRow(book) {
    const row = document.createElement("li");

    const cover = document.createElement(book.hasCover ? "img" : "div");
    cover.className = book.hasCover ? "" : "no-cover";
    if (book.hasCover) {
        cover.alt = "";
        // Fetched rather than linked, because the request needs the token.
        call(`/books/${book.id}/cover`)
            .then((response) => (response.ok ? response.blob() : null))
            .then((blob) => { if (blob) cover.src = URL.createObjectURL(blob); })
            .catch(() => {});
    }

    const text = document.createElement("div");
    const title = document.createElement("div");
    title.className = "title";
    title.textContent = book.title || book.fileName;
    const detail = document.createElement("div");
    detail.className = "detail";
    detail.textContent = [book.authors.join(", "), book.type, size(book.sizeBytes)].filter(Boolean).join(" · ");
    text.append(title, detail);

    row.append(cover, text);

    if (account.isAdmin) {
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "danger";
        remove.textContent = "Remove";
        remove.setAttribute("aria-label", `Remove ${title.textContent}`);
        remove.addEventListener("click", () => removeBook(book, title.textContent));
        row.append(remove);
    } else {
        row.append(document.createElement("span"));
    }
    return row;
}

function size(bytes) {
    const kilobytes = bytes / 1024;
    return kilobytes < 1024 ? `${Math.max(1, Math.round(kilobytes))} kB` : `${(kilobytes / 1024).toFixed(1)} MB`;
}

async function upload(event) {
    event.preventDefault();
    const form = event.target;
    const button = form.querySelector("button");
    const files = [...form.files.files];
    button.disabled = true;

    const failures = [];
    try {
        for (const [index, file] of files.entries()) {
            say(`Uploading ${index + 1} of ${files.length}: ${file.name}`);
            const response = await call(`/books?fileName=${encodeURIComponent(file.name)}`, {
                method: "POST",
                headers: { "Content-Type": "application/octet-stream" },
                body: file,
            });
            if (!response.ok) failures.push(`${file.name}: ${await errorText(response)}`);
        }
    } finally {
        button.disabled = false;
    }

    form.reset();
    await loadBooks();
    if (failures.length) say(failures.join("\n"), true);
    else say(files.length === 1 ? "Added." : `Added ${files.length} books.`);
}

async function removeBook(book, name) {
    if (!confirm(`Remove "${name}" from the library for everyone?`)) return;

    const response = await call(`/books/${book.id}`, { method: "DELETE" });
    if (!response.ok) {
        say(await errorText(response), true);
        return;
    }
    say(`Removed "${name}".`);
    await loadBooks();
}

async function makeInvite() {
    const response = await call("/invites", { method: "POST" });
    if (!response.ok) {
        say(await errorText(response), true);
        return;
    }
    const invite = await response.json();
    say("");
    $("invite-code").querySelector("code").textContent = invite.code;
    show("invite-code", true);
}

async function resetPassword(event) {
    event.preventDefault();
    const form = event.target;
    const username = form.username.value.trim();
    const button = form.querySelector("button");
    button.disabled = true;
    try {
        const response = await call(`/accounts/${encodeURIComponent(username)}/password`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ password: form.password.value }),
        });
        if (!response.ok) {
            say(await errorText(response), true);
            return;
        }
        form.reset();
        say(`New password set for ${username}. They are signed out everywhere.`);
    } finally {
        button.disabled = false;
    }
}

async function signOut() {
    try {
        await call("/auth/signout", { method: "POST" });
    } finally {
        signedOut();
    }
}

$("setup-form").addEventListener("submit", (event) => submitCredentials(event, "/setup"));
$("sign-in-form").addEventListener("submit", (event) => submitCredentials(event, "/auth/signin"));
$("upload-form").addEventListener("submit", upload);
$("invite").addEventListener("click", makeInvite);
$("password-form").addEventListener("submit", resetPassword);
$("sign-out").addEventListener("click", signOut);

start().catch(() => say("The server is not answering.", true));
