# operator quickstart

この repo は「動いているサービス」ではなく**凍結コピー**なので、最初にやることは
起動ではなく **「何が今日も動き、何が死んでいるか」を自分の手で確かめること**。
所要 5 分（依存の取得を除く）。**Cloudflare アカウントも API token も secret も要らない。**

ここに書いてある手順は **2026-08-12 に実際に踏んで、貼ってある出力はその実測値**。
踏めなかった手順は step 6 に「踏めない」と、理由つきで書いてある。

## 0. 前提

Node だけ。実測した版:

```bash
node --version   # v26.3.0
npm --version    # 11.16.0
```

作業ディレクトリは svelte アプリの中（**step 4-b だけ 1 つ上に出る** —— そこだけ
`wrangler.jsonc` の隣で動かす必要がある）:

```bash
cd appview/compintel-cpti0001/svelte
```

**重い build は 1 本に制限されている**（workspace 規約）。step 2 は必ず
resource guard 経由で回すこと。空いているかは先に見られる:

```bash
node /path/to/com-junkawasaki/scripts/resource-guard.mjs status
# {"build":{"active":false},"deploy":{"active":false}}
```

## 1. 依存を入れる

**`npm ci` は使えない。この repo に lockfile が無い。**

```bash
npm install --no-audit --no-fund
```

```
added 92 packages in 2m
npm warn allow-scripts 3 packages have install scripts not yet covered by allowScripts:
npm warn allow-scripts   esbuild@0.25.12 (postinstall: node install.js)
npm warn allow-scripts   esbuild@0.28.1 (postinstall: node install.js)
npm warn allow-scripts   workerd@1.20260804.1 (postinstall: node install.js)
```

**この allow-scripts 警告は無視してよい**（npm 11 の既定で postinstall が保留される）
—— esbuild も workerd も **step 2 と step 4 は警告を出したまま成功した**。

lockfile が無いので、`package.json` の caret 範囲から**毎回新しく解決される**。
2026-08-12 に解決された版（69 package / `node_modules` 268 MB）:

| package | 解決された版 |
|---|---|
| `svelte` | 5.56.8 |
| `@sveltejs/kit` | 2.70.2 |
| `@sveltejs/adapter-cloudflare` | 7.2.9 |
| `vite` | 6.4.3 |
| `typescript` | 5.9.3 |
| `svelte-check` | 4.7.5 |
| `wrangler`（adapter 経由で入る） | 4.121.0 |

**`wrangler` は devDependencies に書かれていないが入る** —— step 4 はこれを使う。
別途インストールしなくてよい。

`.gitignore` は 2026-08-12 に足した（それまで無く、この step が
`node_modules/` 268 MB を untracked で撒いていた）。

## 2. ビルドする（`wrangler.jsonc` が指す成果物を作る）

```bash
node /path/to/com-junkawasaki/scripts/resource-guard.mjs run build -- npm run build
```

```
✓ built in 7.71s        (client)
✓ built in 1m 13s       (server)
> Using @sveltejs/adapter-cloudflare
  ✔ done
```

**これが作るものが、そのまま deploy 対象**。`wrangler.jsonc` の宣言と突き合わせる:

```bash
ls -la .svelte-kit/cloudflare/_worker.js     # main が指す先
ls .svelte-kit/cloudflare/client             # assets.directory が指す先 → _app / _headers
```

```
-rw-r--r--  1 ... 4335 ... .svelte-kit/cloudflare/_worker.js
_app
_headers
```

型も通る（build とは別の検査。resource guard は要らない）:

```bash
npm run check          # = svelte-kit sync && svelte-check
```

```
COMPLETED 142 FILES 0 ERRORS 0 WARNINGS 0 FILES_WITH_PROBLEMS
```

⚠ **この 142 file に `src/app.ts` は入っていない**（`svelte/` の外に在るので
`.svelte-kit/tsconfig.json` の include に含まれない）。**この repo に
`src/app.ts` を型検査するものは無い。** step 3 のとおりデプロイもされないので、
今日そこは誰にも見られていない。

## 3. `src/app.ts` がデプロイされないことを確かめる

README の主張を自分で検証する step。ビルド成果物に、`src/app.ts` だけが持つ
上流ホストが現れないことを見る:

```bash
grep -rl "dispatcher.etzhayyim.com" .svelte-kit/cloudflare/ ; echo "exit=$?"
```

```
exit=1
```

**1 件も無い**（exit 1 = マッチ 0）。一方、生きている側は成果物に入っている:

```bash
grep -rl "mcp.etzhayyim.com" .svelte-kit/output/server | head -1
```

```
.svelte-kit/output/server/entries/endpoints/xrpc/_...path_/_server.ts.js
```

step 4 の `/health` 404 が、これの実行時側の裏取りになる。

## 4. 実際に起動して叩く

2 通りある。**production に近いのは workerd 側（4-b）**なので、片方だけ踏むなら
そちらにすること。両方 2026-08-12 に踏んで、下の表のとおり**結果は一致した**。

### 4-a. vite preview（Node で動く。速い）

```bash
npm run preview -- --port 4173
```

⚠ **`127.0.0.1` では繋がらない。`localhost`（IPv6 `::1`）を使う。**
実測でここに 40 秒溶かした:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:4173/     # 200
```

### 4-b. wrangler dev（workerd で動く。deploy と同じランタイム）

appview のルート（`wrangler.jsonc` のある場所）から:

```bash
cd ..                                  # appview/compintel-cpti0001
./svelte/node_modules/.bin/wrangler dev --local --port 8788 --ip 127.0.0.1
```

**Cloudflare へのログインを要求されない**（`--local`）。起動すると bindings が
表に出るので、`wrangler.jsonc` の `vars` 9 件がそのまま入っていることを目視できる:

```
env.ASSETS                        Assets                  local
env.APP_NANOID ("cpti0001")       Environment Variable    local
env.AGENTGATEWAY_MCP_ROUTER_URL ("https://mcp.etzhayyim.com/xrpc/com.et...")
...
[wrangler:info] Ready on http://127.0.0.1:8788
```

⚠ `Assets directory watcher hit a platform limit and has been disabled.` が出るが、
**macOS の file watcher 上限の話で、deploy には影響しない**（配信は正常）。

### 叩いた結果（`$BASE` = `http://localhost:4173` または `http://127.0.0.1:8788`）

```bash
curl -s -o /dev/null -w 'GET /            → %{http_code}\n' "$BASE/"
curl -s -o /dev/null -w 'GET /health      → %{http_code}\n' "$BASE/health"
curl -s -o /dev/null -w 'OPTIONS /xrpc/…  → %{http_code} %header{access-control-allow-methods}\n' \
  -X OPTIONS "$BASE/xrpc/com.etzhayyim.apps.compintel.listCompetitors"
curl -s -w '\nPOST /xrpc/…     → %{http_code}\n' -X POST -H 'content-type: application/json' -d '{}' \
  "$BASE/xrpc/com.etzhayyim.apps.compintel.listCompetitors"
```

| 経路 | vite preview | wrangler dev | 読み方 |
|---|---|---|---|
| `GET /` | **200** html 2,282B | **200** html 2,282B | ランディングは配信されている。`<title>compintel-cpti0001</title>` |
| `GET /health` | **404** | **404** | **`src/app.ts` は動いていない**（step 3 の実行時側の証拠） |
| `OPTIONS /xrpc/…` | **204** `POST,OPTIONS` | **204** `POST,OPTIONS` | CORS preflight だけは上流に触らないので通る |
| `POST /xrpc/…` | **500** `{"message":"Internal Error"}` | **500** `{"message":"Internal Error"}` | **上流 `mcp.etzhayyim.com` が DNS に無い** |
| `POST /xrpc/`（nsid 無し） | **308** | **308** | 末尾スラッシュのリダイレクト。400 にはならない |

`POST` が 500 になる理由はサーバログに出る:

```
[500] POST /xrpc/com.etzhayyim.apps.compintel.listCompetitors
TypeError: fetch failed
```

**これは設定ミスではない。** `+server.ts` は上流 `fetch` を try で囲っていないので、
DNS 失敗が例外として素通りし、ハンドラ内の 502 分岐（`MCP router request failed`）に
到達しない。上流が復活すればこの経路はそのまま動く。

## 5. 上流が本当に無いことを確かめる

step 4 の 500 を「自分の環境のせい」と誤読しないための裏取り:

```bash
for h in compintel.etzhayyim.com cpti0001.etzhayyim.com mcp.etzhayyim.com \
         dispatcher.etzhayyim.com etzhayyim.com; do
  printf '%-28s ' "$h"; dig +short "$h" | head -1; echo
done
```

```
compintel.etzhayyim.com
cpti0001.etzhayyim.com
mcp.etzhayyim.com
dispatcher.etzhayyim.com
etzhayyim.com                172.67.179.128
```

（`etzhayyim.com` は Cloudflare の 2 IP `104.21.51.111` / `172.67.179.128` を
ラウンドロビンで返すので、最後の行はどちらかになる。**上の 4 つが空であること**が
見たいところ。）

**zone は生きていて、この actor のホストだけが無い。**
`wrangler.jsonc` の route 2 本は、どちらも存在しないホスト名を指している。

契約側が残っていることも確かめられる（GitHub token があれば）:

```bash
gh api repos/etzhayyim/root/contents/00-contracts/bpmn/com/etzhayyim/compintel \
  --jq '.[] | "\(.name)  \(.size)B"'
```

```
trackCompetitor.bpmn  2040B
weeklyRefresh.bpmn  3641B
```

## 6. ここから先は踏めない（踏まずに書いていない）

以下は **2026-08-12 時点で実行していない**。理由つきで残す:

- **`wrangler deploy`** —— `wrangler.jsonc` の route 2 本のホスト名が DNS に無いので、
  deploy しても叩ける URL が生えない。**「動いた」を確認できない deploy はしない**
  （workspace 規約: 検証を省いた blind deploy をしない）。復活させるなら、先に
  `compintel.etzhayyim.com` / `cpti0001.etzhayyim.com` の DNS と、上流
  `mcp.etzhayyim.com` の両方を用意する側の判断が要る。
- **LangGraph 研究ループ（`fetch_signals` → … → `store_snapshot`）** ——
  `CLAUDE.md` が指す `compintel_worker_main.py` が**どの repo にも見つからない**
  （README「何が入っていて、何が入っていないか」）。`cd` する先が無いので
  runbook の `python -m kotodama.compintel_worker_main` は踏めない。
- **`vertex_compintel_*` テーブル** —— RisingWave 側のスキーマはこの repo に無く、
  `RW_URL` の実体も分からない。

## 後片付け

```bash
git status --porcelain
```

```
?? appview/compintel-cpti0001/svelte/package-lock.json
```

step 1〜4 が作るもののうち `node_modules/` `.svelte-kit/` `.wrangler/` は
`.gitignore` 済みなので出ない。**残る 1 行は `package-lock.json` で、これは
意図的に無視していない** —— コミットすれば step 1 が `npm ci` になって再現可能に
なる（今は毎回解決し直している）。**そうするかどうかはこの repo に再現可能ビルドを
求めるかどうかの判断**なので、手順としては決めずに、見えるところに残してある。

消したい場合はこれだけ:

```bash
rm appview/compintel-cpti0001/svelte/package-lock.json
```
