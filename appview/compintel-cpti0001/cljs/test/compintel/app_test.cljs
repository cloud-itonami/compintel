(ns compintel.app-test
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :as rf-db]
            [compintel.app :as app]))

;; re-frame keeps its db in a global atom (re-frame.db/app-db). Reset it
;; around each test so events fired in one test can't leak into the next.
(use-fixtures :each
  {:before (fn [] (reset! rf-db/app-db {}))})

(deftest initialize-sets-the-ported-svelte-scaffolds-literals
  (testing "::initialize seeds exactly the data the old +page.svelte hardcoded"
    (rf/dispatch-sync [::app/initialize])
    (let [db @(rf/subscribe [::app/app])]
      (is (= "Compintel Cpti0001" (:title db)))
      (is (= "etzhayyim-project-compintel" (:project db)))
      (is (= "compintel-cpti0001" (:name db)))
      (is (= "appview" (:kind db)))
      (is (= 0 (:route-count db)))
      (is (= [] (:routes db)))
      (is (= [] (:vars db)))
      (is (true? (:xrpc? db))))))

(deftest relative-path-points-at-this-migration-not-the-deleted-svelte-file
  (testing "the one deliberate data change: relative-path no longer names a deleted file"
    (is (= "appview/compintel-cpti0001/cljs/src/compintel/app.cljs"
           (:relative-path app/initial-db)))))

(deftest app-view-renders-hiccup-rooted-at-main
  (testing "app-view returns a hiccup vector rooted at :main, wrapping the DADS container"
    (rf/dispatch-sync [::app/initialize])
    (let [hiccup (app/app-view)]
      (is (vector? hiccup))
      (is (= :main (first hiccup)))
      (let [container (second hiccup)]
        (is (vector? container))
        (is (= :div (first container)))
        (is (= "dds-ext-container" (:class (second container))))))))

(deftest routes-panel-shows-muted-copy-when-empty
  (testing "empty routes -> the same muted message the Svelte {#if}/{:else} showed"
    (let [panel (app/routes-panel [])
          ;; dds/section returns [:section {...} (maybe heading) & children];
          ;; the last child is what routes-panel actually decided to render.
          rendered (last panel)]
      (is (= :p (first rendered)))
      (is (= "ci-muted" (:class (second rendered))))
      (is (= "No public route is declared next to this app surface." (last rendered))))))

(deftest routes-panel-lists-routes-when-present
  (testing "non-empty routes render as a <ul> of <li> instead of the muted copy"
    (let [panel (app/routes-panel ["/xrpc/foo" "/xrpc/bar"])
          rendered (last panel)]
      (is (= :ul (first rendered)))
      (is (= "ci-list" (:class (second rendered))))
      (is (= 2 (count (drop 2 rendered)))))))

(deftest vars-panel-shows-muted-copy-when-empty
  (testing "empty vars -> the same muted message the Svelte {#if}/{:else} showed"
    (let [panel (app/vars-panel [])
          rendered (last panel)]
      (is (= :p (first rendered)))
      (is (= "No public vars are declared in the nearest wrangler config." (last rendered))))))

(deftest vars-panel-shows-chips-when-present
  (testing "non-empty vars render as chip-labels instead of the muted copy"
    (let [panel (app/vars-panel ["APP_NANOID" "AGENTGATEWAY_MCP_ROUTER_URL"])
          rendered (last panel)]
      (is (= :div (first rendered)))
      (is (= "ci-chips" (:class (second rendered))))
      (is (= 2 (count (drop 2 rendered)))))))
