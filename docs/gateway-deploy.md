# Station gateway deployment

The app reaches Opinet (station prices) and Kakao Mobility (routing) only through `server/gateway.py`, because
both keys must stay off the vehicle. Production runs on the team's own Linux host (`ssh mini`) behind a Cloudflare
named tunnel, next to the other contest services, each with its own process, port and tunnel.

| Item | Value |
| --- | --- |
| Public origin (app `stationGatewayOrigin`) | `https://wayfill.quickkit.net` |
| Endpoints | `POST /v1/stations/discover`, `POST /v1/stations/route`, `GET /v1/health` |
| Checkout | `~/Documents/fuel-pilot` (branch deployed by `git pull`) |
| Secrets and data (never in git) | `~/Documents/fuel-pilot-run/gateway.env` (mode 600), `~/Documents/fuel-pilot-run/data/` (quota SQLite) |
| Process | pm2 `wayfill-gateway` → `~/Documents/fuel-pilot-run/server.sh` → `server/run.sh gateway.env`, listening on `127.0.0.1:18787` |
| Tunnel | pm2 `wayfill-tunnel` → `cloudflared tunnel --config ~/.cloudflared/wayfill.yml run` (tunnel `wayfill`) |

`gateway.env` follows [server/gateway.env.example](../server/gateway.env.example): `OPINET_API_KEY_1`,
`OPINET_API_KEY_2` (two keys, 300 calls a day each, used in order), `KAKAO_REST_API_KEY`, `WAYFILL_PORT=18787`,
`WAYFILL_DATA_DIR`, `WAYFILL_TRUST_PROXY=true` (per-client limits use `CF-Connecting-IP`; safe only because the
server listens on loopback behind the tunnel) and `WAYFILL_PYTHON=/usr/bin/python3`.

## Update

```sh
ssh mini
cd ~/Documents/fuel-pilot && git pull --ff-only && python3 -m unittest discover -s server/tests
pm2 restart wayfill-gateway && pm2 save
curl -s https://wayfill.quickkit.net/v1/health   # {"schemaVersion": 1, "status": "ok"}
```

Restart only the `wayfill-*` processes; the host also runs other services (for example `chayeon-*`).

## Operations

- Logs: `pm2 logs wayfill-gateway` (the server writes no access log, keys or locations).
- Quota use today: the `quota` table in `~/Documents/fuel-pilot-run/data/opinet-quota-<n>.sqlite` and
  `routing-quota.sqlite` (days roll over at midnight KST).
- A key that stops answering (for example because the shared key was also used elsewhere) is retired for the rest
  of the day once the next key returns stations for the same request; see docs/data-provider.md.
- Rotate a key: edit `gateway.env`, then `pm2 restart wayfill-gateway`. No app change is needed.

## First-time setup (done 2026-11-13)

```sh
git clone --branch <branch> git@github.com:kako-labs/wayfill.git ~/Documents/fuel-pilot
mkdir -p ~/Documents/fuel-pilot-run/data && chmod 700 ~/Documents/fuel-pilot-run ~/Documents/fuel-pilot-run/data
# copy gateway.env (mode 600) and write fuel-pilot-run/server.sh: exec ~/Documents/fuel-pilot/server/run.sh <env>
pm2 start ~/Documents/fuel-pilot-run/server.sh --name wayfill-gateway --interpreter bash
cloudflared tunnel create wayfill
# pass the config explicitly: without it the default ~/.cloudflared/config.yml (another service's tunnel) wins
cloudflared tunnel --config ~/.cloudflared/wayfill.yml route dns --overwrite-dns <tunnel-id> wayfill.quickkit.net
pm2 start bash --name wayfill-tunnel -- -lc "cloudflared tunnel --config ~/.cloudflared/wayfill.yml run"
pm2 save
```
