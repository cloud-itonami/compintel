# operator quickstart

この repo は「動いているサービス」ではなく**凍結コピー**なので、最初にやることは
起動ではなく **「何が今日も動き、何が死んでいるか」を自分の手で確かめること**。

**2026-08-26 に SvelteKit から ClojureScript（reagent + re-frame + jp-go-dds）へ
移行した。** この文書もその移行に合わせて全面的に書き直している。旧
SvelteKit 版の手順（`npm run build` / `wrangler dev` / `svelte-check` 等）は
もう存在しないファイルを指すので削除した —— 履歴は `git log -p
docs/operator-quickstart.md` が持つ。

貼ってある出力は **2026-08-26 に実際に踏んだ実測値**。踏めなかった手順は
最後の節に理由つきで書いてある。

## 0. 前提

作業ディレクトリは cljs アプリの中:

```bash
cd appview/compintel-cpti0001/cljs
```

実測した版:

```bash
clojure --version   # Clojure CLI version 1.12.5.1654
nbb --version        # v1.5.212
node --version        # v26.7.0
```

`jp-go-digital-design-system`（`deps.edn` の git 依存）の CSS を nbb で読むため、
`scripts/gen-page.cljs` はローカル checkout を探す（west 管理下ならデフォルトで
見つかる。孤立した migration worktree の場合は `DDS_ROOT` を渡す）:

```bash
export DDS_ROOT=/path/to/orgs/kotoba-lang/jp-go-digital-design-system
```

**重い build は 1 本に制限されている**（workspace 規約）。build は必ず
resource guard 経由で回すこと:

```bash
node /path/to/com-junkawasaki/scripts/resource-guard.mjs status
```

## 1. 依存を入れる

```bash
npm install --no-audit --no-fund
```

```
added 129 packages in 10s
```

## 2. `public/index.html` を生成する（jp-go-dds.page/->page から）

このファイルはコミット済みの生成物。手で編集しない —— ソースは
`scripts/gen-page.cljs` と `src/compintel/app.cljs`（app-css の側）。

```bash
nbb --classpath "$(clojure -Spath)" scripts/gen-page.cljs
```

```
wrote public/index.html 79006 bytes
```

差分が無いことだけ確認したいときは `npm run page:check`
（`scripts/gen-page.cljs --check`、差分があれば exit 1）。

## 3. ビルドする

```bash
node /path/to/com-junkawasaki/scripts/resource-guard.mjs run build -- npx shadow-cljs compile app
```

```
[:app] Compiling ...
[:app] Build completed. (111 files, 34 compiled, 0 warnings, 13.71s)
```

`assets.directory`（`../wrangler.jsonc` の `./cljs/public`）が指す先はこれで揃う:

```bash
ls public/js/app.js      # shadow-cljs の出力
ls public/index.html     # step 2 の生成物
```

## 4. テストする

```bash
node /path/to/com-junkawasaki/scripts/resource-guard.mjs run build -- npx shadow-cljs compile test
node out/tests.js
```

```
[:test] Build completed. (112 files, 111 compiled, 0 warnings, 11.73s)
Testing compintel.app-test

Ran 7 tests containing 25 assertions.
0 failures, 0 errors.
```

⚠ `re-frame: Subscribe was called outside of a reactive context.` という警告が
2 行出るが、これは `app-view` を reagent の render サイクル外（テストの中）で
直接呼んで `@(rf/subscribe …)` を評価しているために出る re-frame 自身の既知の
警告で、**アサーション自体は 0 failures / 0 errors**。無視してよい。

## 5. dev server で見る（任意）

```bash
npx shadow-cljs watch app
```

`public/index.html` を直接開くか、`shadow-cljs` が案内する URL を開く。
**これは今回のセッションでは踏んでいない** —— build と test の緑で足りると
判断したため（下記 6 節）。

## 6. ここから先は踏めない（踏まずに書いていない）

以下は 2026-08-26 時点で実行していない。理由つきで残す:

- **`wrangler deploy` / `wrangler dev`** —— `wrangler.jsonc` の route 2 本の
  ホスト名（`compintel.etzhayyim.com` / `cpti0001.etzhayyim.com`）は
  README.md「ホストの現在地」のとおり今日も DNS に無い。叩ける URL が
  生えない deploy はしない（workspace 規約: 検証を省いた blind deploy を
  しない）。復活させるなら、先に DNS と上流 `mcp.etzhayyim.com` を用意する
  側の判断が要る。
- **旧 `+server.ts`（MCP router への中継）の動作確認** —— この handler は
  `appview/compintel-cpti0001/salvage/svelte/src/routes/xrpc/[...path]/
  +server.ts` に退避されているだけで、今日この Worker のどこにも配線されて
  いない。動かすには Cloudflare Worker の fetch handler として書き直す
  必要があり、それはこの移行のスコープ外（README.md「2026-08-26」節）。
- **LangGraph 研究ループ・`vertex_compintel_*` テーブル** —— 2026-08-12 の
  調査のとおりこの repo の外にあり、今回の移行で状況は変わっていない。

## 後片付け

```bash
git status --porcelain
```

`node_modules/` `.shadow-cljs/` `public/js/` `.cpcache/` `out/` は
`cljs/.gitignore` 済みなので出ない。`public/index.html` はコミット対象
（step 2 の生成物）。
