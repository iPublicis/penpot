;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2021 UXBOX Labs SL

(ns app.main.ui.dashboard.file-menu
  (:require
   [app.main.data.dashboard :as dd]
   [app.main.data.messages :as dm]
   [app.main.data.modal :as modal]
   [app.main.repo :as rp]
   [app.main.store :as st]
   [app.main.ui.context :as ctx]
   [app.main.ui.components.context-menu :refer [context-menu]]
   [app.util.dom :as dom]
   [app.util.i18n :as i18n :refer [tr]]
   [app.util.router :as rt]
   [beicon.core :as rx]
   [rumext.alpha :as mf]))

(mf/defc file-menu
  [{:keys [file show? on-edit on-menu-close top left navigate?] :as props}]
  (assert (some? file) "missing `file` prop")
  (assert (boolean? show?) "missing `show?` prop")
  (assert (fn? on-edit) "missing `on-edit` prop")
  (assert (fn? on-menu-close) "missing `on-menu-close` prop")
  (assert (boolean? navigate?) "missing `navigate?` prop")
  (let [top   (or top 0)
        left  (or left 0)

        current-team-id  (mf/use-ctx ctx/current-team-id)
        teams            (mf/use-state nil)
        current-team     (get @teams current-team-id)
        other-teams      (remove #(= (:id %) current-team-id)
                                (vals @teams))
        current-projects (remove #(= (:id %) (:project-id file))
                                 (:projects current-team))

        project-name (fn [project]
                       (if (:is-default project)
                         (tr "labels.drafts")
                         (:name project)))

        on-new-tab
        (mf/use-callback
         (mf/deps file)
         (fn [event]
           (let [pparams {:project-id (:project-id file)
                          :file-id (:id file)}
                 qparams {:page-id (first (get-in file [:data :pages]))}]
             (st/emit! (rt/nav-new-window :workspace pparams qparams)))))

        on-duplicate
        (mf/use-callback
         (mf/deps file)
         (st/emitf (dm/success (tr "dashboard.success-duplicate-file"))
                   (dd/duplicate-file file)))

        delete-fn
        (mf/use-callback
         (mf/deps file)
         (st/emitf (dm/success (tr "dashboard.success-delete-file"))
                   (dd/delete-file file)))

        on-delete
        (mf/use-callback
         (mf/deps file)
         (fn [event]
           (dom/stop-propagation event)
           (st/emit! (modal/show
                      {:type :confirm
                       :title (tr "modals.delete-file-confirm.title")
                       :message (tr "modals.delete-file-confirm.message")
                       :accept-label (tr "modals.delete-file-confirm.accept")
                       :on-accept delete-fn}))))

        on-move
        (mf/use-callback
         (mf/deps file)
         (fn [team-id project-id]
           (let [data  {:ids #{(:id file)}
                        :project-id project-id}

                 mdata {:on-success
                        (st/emitf (dm/success (tr "dashboard.success-move-file"))
                                  (if navigate?
                                    (rt/nav :dashboard-files
                                            {:team-id team-id
                                             :project-id project-id})
                                    (dd/fetch-recent-files {:team-id team-id})))}]

            (st/emitf (dd/move-files (with-meta data mdata))))))

        add-shared
        (mf/use-callback
         (mf/deps file)
         (st/emitf (dd/set-file-shared (assoc file :is-shared true))))

        del-shared
        (mf/use-callback
         (mf/deps file)
         (st/emitf (dd/set-file-shared (assoc file :is-shared false))))

        on-add-shared
        (mf/use-callback
         (mf/deps file)
         (fn [event]
           (dom/stop-propagation event)
           (st/emit! (modal/show
                      {:type :confirm
                       :message ""
                       :title (tr "modals.add-shared-confirm.message" (:name file))
                       :hint (tr "modals.add-shared-confirm.hint")
                       :cancel-label :omit
                       :accept-label (tr "modals.add-shared-confirm.accept")
                       :accept-style :primary
                       :on-accept add-shared}))))

        on-del-shared
        (mf/use-callback
          (mf/deps file)
          (fn [event]
            (dom/prevent-default event)
            (dom/stop-propagation event)
            (st/emit! (modal/show
                        {:type :confirm
                         :message ""
                         :title (tr "modals.remove-shared-confirm.message" (:name file))
                         :hint (tr "modals.remove-shared-confirm.hint")
                         :cancel-label :omit
                         :accept-label (tr "modals.remove-shared-confirm.accept")
                         :on-accept del-shared}))))]

    (mf/use-layout-effect
      (mf/deps show?)
      (fn []
        (let [group-by-team (fn [projects]
                              (reduce 
                                (fn [teams project]
                                  (update teams (:team-id project)
                                          #(if (nil? %)
                                             {:id (:team-id project)
                                              :name (:team-name project)
                                              :projects [project]}
                                             (update % :projects conj project))))
                                {}
                                projects))]
          (if show?
            (->> (rp/query! :all-projects)
                 (rx/map group-by-team)
                 (rx/subs #(reset! teams %)))
            (reset! teams [])))))

    (when current-team
      [:& context-menu {:on-close on-menu-close
                        :show show?
                        :fixed? (or (not= top 0) (not= left 0))
                        :min-width? true
                        :top top
                        :left left
                        :options [[(tr "dashboard.open-in-new-tab") on-new-tab]
                                  [(tr "labels.rename") on-edit]
                                  [(tr "dashboard.duplicate") on-duplicate]
                                  (when (or (seq current-projects) (seq other-teams))
                                    [(tr "dashboard.move-to") nil
                                     (conj (vec (for [project current-projects]
                                                  [(project-name project)
                                                   (on-move (:id current-team)
                                                                            (:id project))]))
                                           (when (seq other-teams)
                                             [(tr "dashboard.move-to-other-team") nil
                                              (for [team other-teams]
                                                [(:name team) nil
                                                 (for [sub-project (:projects team)]
                                                   [(project-name sub-project)
                                                    (on-move (:id team)
                                                             (:id sub-project))])])]))])
                                  (if (:is-shared file)
                                    [(tr "dashboard.remove-shared") on-del-shared]
                                    [(tr "dashboard.add-shared") on-add-shared])
                                  [:separator]
                                  [(tr "labels.delete") on-delete]]}])))

