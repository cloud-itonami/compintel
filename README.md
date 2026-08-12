# cloud-itonami/compintel

**`compintel.etzhayyim.com` 競合インテリジェンス actor の、edge 側だけを切り出した
凍結コピー。** 実体は SvelteKit の thin edge BFF 1 本 —— 判断は何もせず、
`POST /xrpc/<nsid>` を MCP router へ JSON-RPC `tools/call` として中継するだけ。

**そして今日、その MCP router も、自分のホストも DNS に無い**（下記「ホストの現在地」）。
つまりこの repo は「動いているサービス」ではなく、**何が宣言されていたかの記録**である。

名前が `compintel` としか言っていないので、まずここで名乗る —— この repo は
**LangGraph 研究ループそのものではなく、その前に立つ 15 ファイルの受付**である。
実際に競合を調べて脅威スコアを付けていた Python ワーカーは、ここには**無い**
（どこにあるかも下記のとおり、今日は答えが出ない）。

## 何が入っていて、何が入っていないか

`CLAUDE.md`（抽出前の monorepo 時代の runbook）は 4 つの場所を名指しするが、
**この repo に在るのは 1 つだけ**。実測 2026-08-12:

| runbook が指す場所 | 今日どこに在るか |
|---|---|
| `60-apps/etzhayyim-project-compintel/appview/compintel-cpti0001/` | **✅ ここ**（`appview/compintel-cpti0001/`。パスの前半が落ちた形） |
| `00-contracts/bpmn/com/etzhayyim/compintel/` | ⚠ **`etzhayyim/root` に在る**（この repo には無い）。`trackCompetitor.bpmn` 2,040B / `weeklyRefresh.bpmn` 3,641B |
| `40-engine/kotoba/crates/kotoba-kotodama/py/…/compintel_worker_main.py` | ❌ **見つからない**。`etzhayyim/root` の当該パスは 404、`kotoba-lang/kotodama-py`（py 資産の移転先）に compintel の文字列は 1 件も無い。GitHub code search で `compintel_worker_main` に当たるのは**上の BPMN が参照している文字列だけ**で、モジュール本体はどの repo にも見えない |
| `90-docs/adr/2605072000-langgraph-agent-loop-pattern.md` | ❌ この repo には無い |

**したがって「LangGraph 6 ノードのループ」は、この repo からは動かせない。**
`CLAUDE.md` 末尾の `python -m kotodama.compintel_worker_main` は、
**踏めない手順**として読むこと（`cd` 先のディレクトリ自体がここに無い）。

### BPMN だけは生きているので、そこは引ける

`etzhayyim/root` の `00-contracts/bpmn/com/etzhayyim/compintel/weeklyRefresh.bpmn`
が実在し、runbook の記述と一致することは確認できる:

- `WeeklyTimer` の `timeCycle` は **`0 23 * * 0`** = 日曜 23:00 UTC = **月曜 08:00 JST**
  （runbook の「Weekly refresh (Monday 08:00 JST)」は UTC で書かれた cron の裏返し）
- service task は `compintel.run_research_agent` → `compintel.score_threats` →
  `AlertGateway` → `compintel.send_digest`

つまり**契約は残り、実装だけが消えている。**

## 最近接 repo との境界

| repo | 役割 | こことの違い |
|---|---|---|
| [`etzhayyim/root`](https://github.com/etzhayyim/root) | 抽出元の monorepo。BPMN 契約は今もここ | あちらが**契約の正本**。ここは edge の受付だけ |
| [`kotoba-lang/kotodama-py`](https://github.com/kotoba-lang/kotodama-py) | 旧 monorepo の Python worker / SQLMesh 資産の移転先 | **compintel は移されていない**（実測: 該当文字列 0 件）。「py はここ」と当て推量しない |
| [`kotoba-lang/kotodama`](https://github.com/kotoba-lang/kotodama) | functional-organism runtime 本体 | ランタイム。ここは 1 アプリの facade |
| `cloud-itonami` の他の appview（`danjo` / `eigyo` / `aidesk` …） | 同じ抽出バッチの兄弟。同じ SvelteKit scaffold | **scaffold が同型**。`+page.svelte` は生成物で、中身は nanoid が違うだけ |
| **ここ** | **edge BFF の凍結コピー** | 上のどれでもない。新しい競合分析ロジックをここに足さない |

## 中身（15 ファイル）

```
README.edn                 111B  機械可読 metadata（:kind :app）。人間向けの説明は入っていない
CLAUDE.md                        抽出前の runbook。上表のとおり 3/4 のパスが死んでいる
NOTICE                           Apache-2.0 + etzhayyim Charter Rider v3.1
actor-manifest.jsonld            DID did:web:compintel.etzhayyim.com / nanoid cpti0001 / capability 4 本
migration.edn                    抽出元 etzhayyim/root@c3a74d2 の記録（13 files / 17,354B）
appview/compintel-cpti0001/
  wrangler.jsonc                 Worker 設定。main は svelte 側のビルド出力を指す（下記）
  kotodama.jsonld                アプリ宣言。subscribeRepos / integrations
  src/app.ts                     **デプロイされない**（下記）
  svelte/                        **これがデプロイされる本体**
    src/routes/+page.svelte      ランディング。生成物で、中身は自分の設定と食い違う（下記）
    src/routes/xrpc/[...path]/+server.ts   唯一の実処理。MCP router への中継
```

### `src/app.ts` はデプロイされない

`wrangler.jsonc` の `main` は **`svelte/.svelte-kit/cloudflare/_worker.js`** ——
つまり SvelteKit のビルド出力であって、`src/app.ts` ではない。

実測（2026-08-12、`npm run build` 後にビルド成果物を検索）:

- `_worker.js` は `output/server/index.js` を import し、そこに
  `entries/endpoints/xrpc/_...path_/_server.ts.js` が入っている
- 成果物の中に **`dispatcher.etzhayyim.com` は 1 件も現れない**
- ローカルで `GET /health` を叩くと **404**（`src/app.ts` にしか無い経路）

**したがってこの repo には XRPC の中継が 2 つ在り、動くのは片方だけ:**

| | `src/app.ts`（死） | `routes/xrpc/[...path]/+server.ts`（生） |
|---|---|---|
| 上流 | `dispatcher.etzhayyim.com` | `mcp.etzhayyim.com`（`AGENTGATEWAY_MCP_ROUTER_URL`） |
| プロトコル | XRPC をそのまま POST | JSON-RPC `tools/call` に包む |
| 認証 | `x-internal-trust` ヘッダ | 受信ヘッダをそのまま透過 |
| `/health` | 有り | **無し** |

`src/app.ts` を消していないのは、**どちらが意図された設計だったかをこの repo が
答えられない**ため。消す/残すを決めるのは、上流の dispatcher と MCP router の
どちらが正になるかを知っている側。

### ランディングページは自分の設定と食い違う

`+page.svelte` は scaffold 生成物で、`routeCount: 0` / `routes: []` / `vars: []` が
**ソースにハードコードされている**。実際に `GET /` を叩くと
`No public route is declared next to this app surface.` と表示されるが、
**隣の `wrangler.jsonc` は route を 2 本・vars を 9 個宣言している**。
画面を信用せず、`wrangler.jsonc` を読むこと。

## ホストの現在地（2026-08-12 実測）

```
compintel.etzhayyim.com    → DNS 応答なし
cpti0001.etzhayyim.com     → DNS 応答なし
mcp.etzhayyim.com          → DNS 応答なし   ← BFF の唯一の上流
dispatcher.etzhayyim.com   → DNS 応答なし   ← 死んでいる側の上流
etzhayyim.com              → 104.21.51.111 / 172.67.179.128（Cloudflare）
```

zone（`etzhayyim.com`）は生きているが、`wrangler.jsonc` が宣言する 2 本の route は
どちらもホスト名が存在しない。**この Worker は今日デプロイされていない。**

結果として、ローカルで起動しても `POST /xrpc/…` は **500 `{"message":"Internal Error"}`**
になる（`+server.ts` は `fetch` を try で囲っていないので、上流の DNS 失敗が
`TypeError: fetch failed` として素通りし、SvelteKit の 500 になる。502 にはならない）。
**これは設定ミスではなく、上流が無いことの正しい観測。**

## 触る前に

- **動かして確かめる手順は [`docs/operator-quickstart.md`](docs/operator-quickstart.md)。**
  Cloudflare アカウントも secret も要らず、5 分で「何が生きて何が死んでいるか」を
  自分の目で見られる（全手順 2026-08-12 に実測済み）。
- **新しい競合分析ロジックをここに足さない。** ここは受付であって、判断は
  BPMN 契約と（今は行方不明の）LangGraph ワーカーの側に在った。
- `README.edn` は消さない（`etzhayyim.repository/v1` の機械可読 metadata で、
  `migration.edn` の `:allowed-additions` に載っている 2 ファイルのうちの 1 つ）。

### この README 自体が `:allowed-additions` の外に在る

`migration.edn` は `:identity {:allowed-additions ["README.edn" "migration.edn"]}` と
宣言している —— 抽出時の意図は「抽出元の tree に、この 2 つ以外を足さない」だった。
**この `README.md` / `docs/` / `.gitignore` はその 3 つ目以降にあたる。**

黙って増やさないために書いておく: 2026-08-12 時点で **`:allowed-additions` を検査する
gate はこの workspace に無い**（root の `scripts/` `manifest/` `scripts/fleet-ci/` を
検索して 0 件）。凍結を優先して名乗りを持たないままにするより、**何が生きていて何が
死んでいるかを読めるようにする方を採った**。抽出時の tree が知りたければ
`migration.edn` の `:source` に revision と tree hash が固定されている。
