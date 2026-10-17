# Running a server from the app

Status: Done

## Goal

A server is set up and run from the Lukia app. Someone starts the container,
installs the app, types the server's address, and does everything from there:
creates the admin account, lets people create their own accounts, and looks
after people and books. The web admin page goes. The app still works with no
server at all.

## Acceptance Criteria

Server

1. The server serves the API and nothing else. `/` and the admin page's files
   are gone; any path outside `/api/` answers 404.
2. `GET /health` also says whether sign-up is open (`signUpOpen`), added
   alongside the fields it already has.
3. Sign-up is closed on a new server and on one upgraded from 1.4.0. Only an
   admin can open or close it (`GET`/`PUT /api/v1/settings`).
4. `POST /api/v1/auth/signup` with a username and password creates an
   ordinary account and signs it in, while sign-up is open. While it is
   closed it answers 403 and creates nothing. Usernames and passwords follow
   the rules setup and invites already use, and failed attempts count towards
   the same per-address limit as sign-in.
5. `GET /api/v1/accounts` (admin only) lists every account: username, whether
   it is an admin, when it was created, and its usage - books added to the
   server, books started, books finished, favourites, total reading time,
   reading sessions, and when it last read.
6. `DELETE /api/v1/accounts/{username}` (admin only) removes that account the
   way deleting your own does. It refuses to remove the last admin. The books
   the account added stay in the library.
7. A non-admin calling any admin endpoint gets 403. Nothing about other
   accounts is visible to non-admins.
8. Existing endpoints keep working for apps already installed, including
   invite redemption. A database from 1.4.0 upgrades in place with nothing
   lost.

App

9. Signed out, Account offers Sign in and Create account. The server is asked
   about itself when the form is sent, as it already is for setting up:
   - **set-up required:** the form becomes the one that creates the admin;
   - **sign-up open:** the account is created and signed in;
   - **sign-up closed:** nothing is sent, and the form says to ask whoever runs
     the server to open new accounts;
   - **a server older than this** (no `signUpOpen` in `/health`): the form
     becomes the invite-code form, with a line saying why.
10. Signed in as an admin, Account shows an **Administration** section:
    - an "Allow new accounts" switch, saved on the server;
    - **People**: each account with its usage (books added, started,
      finished, favourites, reading time, last read); for each, **Reset
      password** and **Remove account**, both confirmed first; neither offered
      on your own row where it would leave the server without an admin.
11. An admin sees **Remove from server** in a server book's menu (Book Details
    and the library's long-press menu). After confirming, the book leaves the
    shared library: this device's shelf follows at once, other devices on their
    next sync, and a copy already downloaded stays on its device as an ordinary
    book. Non-admins don't see it.
12. Signed out, or never signed in, the app behaves exactly as it does now.
13. Every new string is in all 11 locales, German and Dutch formal.

Errors

14. Lost connection, a 403 from a demoted admin, and a refused last-admin
    removal each show a plain message in place, and change nothing on the
    device.

## Out of Scope

- Email, password reset by the user themselves, or account approval queues.
- Invite codes in the app. The endpoints stay, for apps already installed.
- Making another account an admin.
- Per-book reading details of other people: the People list shows totals only.
- Any change to how syncing works.

## Flow

First run, server owner:
1. Starts the container and puts it behind HTTPS.
2. Installs Lukia, opens Settings, Account, types the address.
3. The app sees set-up required and asks for the admin's username and password.
4. Signed in as admin, they switch on "Allow new accounts" and tell people the
   address.

Someone joining:
1. Installs Lukia, types the address in Settings, Account.
2. Sees "Create account", picks a username and password, and is signed in.

The owner afterwards switches sign-up off again, resets a forgotten password,
looks at who reads what, or removes an account or a book.

## Impact

| Area | Change |
| --- | --- |
| Contracts | Additive, in `api/`: `signUpOpen` on the health response, sign-up, settings, account list with usage, account removal. Nothing renamed or removed; older apps keep working. |
| Data | A `server_setting` table, added by the server's first SQLDelight migration (`1.sqm`). Usage is counted from tables already there (`book.addedBy`, `book_state`, `reading_session`); no new columns. |
| Security | Sign-up closed by default. Every admin endpoint checks `isAdmin` on the server. Sign-up counts towards the per-address sign-in limit. Removing the web page removes its HTML, script and CSP surface. Last-admin rule enforced on the server. |
| Performance | The account list is one query, each measure a subquery over that account's rows; fine for a household-sized server. |
| UI | New Account sections built from the existing settings components (`SettingsToggle`, groups, `ConfirmDialog`). New strings in 11 locales. |
| Docs | `SERVER.md` set-up section rewritten around the app; admin page and invite instructions removed; the curl password reset kept as a fallback. |
| Rollout | Server and app ship together as 1.5.0. A 1.5.0 app against a 1.4.0 server falls back to the invite flow (criterion 9). |

## Slices

- [x] 1. Server: settings table and migration, `signUpOpen` on health, sign-up endpoint, with tests.
- [x] 2. App: Account offers Create account when sign-up is open.
- [x] 3. Server and app: Allow new accounts switch for admins.
- [x] 4. Server: account list with usage, account removal; app: People list.
- [x] 5. App: reset password and remove account from People.
- [x] 6. App: Remove from server for admins.
- [x] 7. Server: remove the web admin page; docs.

## Decisions

- Sign-up closed says to ask whoever runs the server to open new accounts.
- Removing an account keeps the books it added, as deleting your own does.
