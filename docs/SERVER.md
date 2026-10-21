# Sync server

A Lukia server lets several people share one library. Each person has an
account, and their place in every book, their favourites, how they read each
PDF, and their reading statistics follow them from device to device.

It is meant to run at home or on a small VPS for a household or a group of
friends: one container, one folder of data. Signing in is optional; without an
account the app works as it always has. See [the spec](specs/sync-server.md)
and [the decisions](decisions/) for how it is built.

## Running it

Each release publishes an image to the GitHub container registry:

```bash
docker run -d --name lukia -p 8080:8080 -v lukia-data:/data --restart unless-stopped ghcr.io/temez26/lukia-server:latest
```

Pin a version (`:1.6.0`) rather than `latest` if you want upgrades to happen
only when you choose.

To build the image yourself from a checkout:

```bash
./gradlew :server:installDist
```

```bash
docker build -t lukia-server server
```

### Settings

| Variable | Default | |
| --- | --- | --- |
| `LUKIA_SERVER_NAME` | `Lukia` | What the app calls this server |
| `LUKIA_PORT` | `8080` | |
| `LUKIA_DATA_DIR` | `/data` in the image | Where the database and the books live |
| `LUKIA_MAX_UPLOAD_MB` | `200` | The largest book an upload may be |

The server refuses to start with a setting it cannot read, rather than
quietly using a default.

## Setting it up

Everything is done from the app; the server has no web page.

1. In the app, open Settings, then Account, type the server's address, and
   choose Create account. On a server with no accounts yet this creates the
   admin.
2. Add books: Add to server on a book's page, or select several in the
   library and add them together. The server reads each book's title, authors
   and cover, and names it by a fingerprint of its bytes, so adding a file
   twice adds it once.
3. Under Administration at the end of the same page, switch on Allow new
   accounts and tell people the server's address. They install the app and
   choose Create account. Switch it off again once everyone is in; while it is
   off, Create account tells people to ask whoever runs the server.

Books can also be uploaded with `curl`, using an access token from signing in:

```bash
curl -X POST "https://books.example.com/api/v1/books?fileName=Moby%20Dick.epub" -H "Authorization: Bearer $TOKEN" --data-binary @"Moby Dick.epub"
```

## Looking after it

The admin sees an Administration section at the end of Account:

- **Allow new accounts:** whether anyone who can reach the server may create
  an account. Off until the admin turns it on.
- **People:** each account with how many books it added, started and
  finished, its favourites, its reading time and when it last read - totals
  only, never which books. Reset password gives someone a new password and
  signs them out everywhere; Remove account removes them as if they had
  deleted their own account, and the books they added stay. Neither is offered
  on your own row.
- **Remove from Server**, in a book's menu: takes the book out of the shared
  library. Devices that never downloaded it drop it on their next sync; a copy
  already downloaded stays on its device as an ordinary book.

## HTTPS

On your own network the app connects over plain `http://`. Anywhere else it
insists on `https://`, because a password and a reading history would otherwise
cross the internet in the clear. Put the server behind a reverse proxy that
holds a certificate. With [Caddy](https://caddyserver.com), the whole
configuration is:

```
books.example.com {
    reverse_proxy localhost:8080
}
```

Behind a proxy every request comes from the proxy's address, so the limit on
failed sign-ins per address applies to all of them together; the limit per
username still works as intended.

With [Nginx Proxy Manager](https://nginxproxymanager.com), point a proxy host at
the container (scheme `http`, port `8080`), request a certificate, and add this
under Advanced, or uploads over a megabyte are refused:

```
client_max_body_size 200m;
proxy_request_buffering off;
proxy_read_timeout 300s;
proxy_send_timeout 300s;
```

The app keeps one connection open while it is on screen, and the server says
down it when something changes, so a book added or a page turned on another
device shows up at once. It is a plain HTTP response that never ends; the server
asks nginx not to buffer it, so no proxy setting is needed for it. Where a proxy
cuts it anyway, the app still catches up every two minutes.

## What the server stores

- **Accounts:** a username, an Argon2id hash of the password, whether it is
  the admin. Tokens are stored only as hashes.
- **The library:** the book files, and what each says about itself.
- **Per account:** the page each book was left at, how each PDF is read,
  favourites, and reading sessions (when, for how long, in which book, and
  the book's title).

It stores no translations, settings or diagnostics, and no API keys for
translation services: those never leave the device.

Deleting an account - Settings, then Account, then Delete account in the app -
removes its sign-ins, its state and its sessions at once. The books it uploaded
stay, because they belong to the shared library, and so does everything on the
device. The admin cannot delete their own account while they are the only
admin.

## Backing up

Everything is in the data folder: `lukia-server.db` and the `books/` folder
beside it. Stop the container and copy the folder:

```bash
docker stop lukia
```

```bash
docker run --rm -v lukia-data:/data -v "$PWD":/backup alpine tar czf /backup/lukia-backup.tgz -C /data .
```

```bash
docker start lukia
```

Copying the database while the server runs can catch it mid-write, which is
why it is stopped first.

## Upgrading

Pull the new image and start a new container on the same volume:

```bash
docker pull ghcr.io/temez26/lukia-server:latest
```

```bash
docker rm -f lukia
```

```bash
docker run -d --name lukia -p 8080:8080 -v lukia-data:/data --restart unless-stopped ghcr.io/temez26/lukia-server:latest
```

Back up first. The app keeps working with an older or newer server: the API is
versioned by path, and fields are only ever added.

## Forgotten passwords

There is no email reset. The admin sets a new password from People - see
[Looking after it](#looking-after-it) - which also signs that account out
everywhere. The same from a shell, with an admin's access token:

```bash
curl -X PUT "https://books.example.com/api/v1/accounts/alice/password" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d '{"password":"a new long password"}'
```
