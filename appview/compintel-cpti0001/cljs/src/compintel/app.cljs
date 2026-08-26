(ns compintel.app
  "compintel-cpti0001 — reagent + re-frame port of the former Svelte scaffold
  at appview/compintel-cpti0001/svelte/src/routes/+page.svelte (84 lines,
  removed by this migration; salvaged non-frontend code now lives under
  appview/compintel-cpti0001/salvage/).

  Faithful port: the same hardcoded facts (title/project/name/kind/routes/
  vars/xrpc), the same four sections (hero facts / Public Routes /
  Runtime Bindings / Source), and the same muted empty-state copy when a
  list is empty. README.md documents that these values are known-stale
  scaffold literals — `routeCount: 0` / `routes: []` / `vars: []` sit next to
  a wrangler.jsonc that actually declares 2 routes and 9 vars, and the
  original Svelte page said so itself (\"画面を信用せず、wrangler.jsonc を
  読むこと\"). That drift is a pre-existing property of the page being
  ported, not something this migration invents or corrects — this task
  migrates the frontend only, faithfully, not the underlying data wiring.

  The one deliberate change from the original literal data is `relative-path`:
  the original scaffold's `relativePath` pointed at the now-deleted
  `.../svelte/src/routes/+page.svelte`. Pointing that at a deleted file in a
  deleted language would be actively wrong, not faithful, so it now names
  this file instead. Every other field keeps its original value verbatim.

  Styling classes used below (`ci-eyebrow` / `ci-name` / `ci-muted` /
  `ci-mono` / `ci-list` / `ci-chips`) are defined in
  scripts/gen-page.cljs's `app-css`, not here — the CSS has to live in the
  generated `public/index.html`'s inlined `<style>` (jp-go-dds.page/->page's
  `:app-css`), which this module has no path to since it is the reagent
  view, not the page assembler.

  Only one screen existed (ADR-2608080100: one document, one bundle, one
  mount)."
  (:require [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [jp-go-dds.core :as dds]))

;; --- db ----------------------------------------------------------------

(def initial-db
  {:title "Compintel Cpti0001"
   :project "etzhayyim-project-compintel"
   :name "compintel-cpti0001"
   :kind "appview"
   :route-count 0
   :routes []
   :vars []
   :xrpc? true
   :relative-path "appview/compintel-cpti0001/cljs/src/compintel/app.cljs"})

(rf/reg-event-db
 ::initialize
 (fn [_ _] initial-db))

(rf/reg-sub ::app (fn [db _] db))

;; --- view --------------------------------------------------------------------

(defn facts-grid [{:keys [project route-count xrpc?]}]
  (dds/grid {}
    (dds/card [:span "Project"] [:strong project])
    (dds/card [:span "Routes"] [:strong (str route-count)])
    (dds/card [:span "XRPC"] [:strong (if xrpc? "enabled" "not configured")])))

(defn routes-panel [routes]
  (dds/section {:title "Public Routes"}
   (if (seq routes)
     (into [:ul {:class "ci-list"}]
           (map (fn [route] [:li {:class "ci-mono"} route]) routes))
     [:p {:class "ci-muted"} "No public route is declared next to this app surface."])))

(defn vars-panel [vars]
  (dds/section {:title "Runtime Bindings"}
   (if (seq vars)
     (into [:div {:class "ci-chips"}] (map dds/chip-label vars))
     [:p {:class "ci-muted"} "No public vars are declared in the nearest wrangler config."])))

(defn source-panel [relative-path]
  (dds/section {:title "Source"}
   [:p {:class "ci-mono"} relative-path]))

(defn app-view []
  (let [{:keys [title name kind routes vars relative-path] :as app} @(rf/subscribe [::app])]
    [:main
     (dds/container
      (dds/section {}
       [:p {:class "ci-eyebrow"} (str "Cloudflare " kind)]
       (dds/heading 1 title)
       [:span {:class "ci-name"} name])
      (facts-grid app)
      (routes-panel routes)
      (vars-panel vars)
      (source-panel relative-path))]))

(defn ^:dev/after-load render! []
  (rdom/render [app-view] (.getElementById js/document "app")))

(defn ^:export main []
  (rf/dispatch-sync [::initialize])
  (render!))
