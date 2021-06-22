(ns status-im.ui.screens.chat.pinned-messages
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [status-im.i18n.i18n :as i18n]
            [status-im.ui.components.connectivity.view :as connectivity]
            [status-im.ui.components.react :as react]
            [quo.animated :as animated]
            [status-im.ui.screens.chat.message.message :as message]
            [status-im.ui.screens.chat.styles.main :as style]
            [status-im.ui.screens.chat.message.gap :as gap]
            [status-im.ui.screens.chat.components.accessory :as accessory]
            [status-im.ui.screens.chat.message.datemark :as message-datemark]
            [status-im.utils.platform :as platform]
            [quo.react :as quo.react]
            [status-im.ui.components.topbar :as topbar]
            [status-im.ui.screens.chat.views :as chat]))

(defn pins-topbar []
  (let [{:keys [group-chat chat-id chat-name]}
        @(re-frame/subscribe [:chats/current-chat])
        pinned-messages @(re-frame/subscribe [:chats/pinned chat-id])
        [first-name _] (when-not group-chat @(re-frame.core/subscribe [:contacts/contact-two-names-by-identity chat-id]))]
    [topbar/topbar {:show-border? true
                    :title        (if group-chat chat-name first-name)
                    :subtitle     (if (= (count pinned-messages) 0)
                                    (i18n/label :t/no-pinned-messages)
                                    (i18n/label-pluralize (count pinned-messages) :t/pinned-messages-count))}]))

(defn get-space-keeper-ios [bottom-space panel-space active-panel text-input-ref]
  (fn [state]
    ;; NOTE: Only iOS now because we use soft input resize screen on android
    (when platform/ios?
      (cond
        (and state
             (< @bottom-space @panel-space)
             (not @active-panel))
        (reset! bottom-space @panel-space)

        (and (not state)
             (< @panel-space @bottom-space))
        (do
          (some-> ^js (quo.react/current-ref text-input-ref) .focus)
          (reset! panel-space @bottom-space)
          (reset! bottom-space 0))))))

(defn pinned-messages-empty []
  [react/view {:style {:flex 1
                       :align-items :center
                       :justify-content :center}}
   [react/text {:style style/intro-header-description}
    (i18n/label :t/pinned-messages-empty)]])

(defonce messages-list-ref (atom nil))

(defn render-pin-fn [{:keys [outgoing type] :as message}
                     idx
                     _
                     {:keys [group-chat public? current-public-key space-keeper chat-id show-input? message-pin-enabled]}]
  [react/view
   (if (= type :datemark)
     [message-datemark/chat-datemark (:value message)]
     (if (= type :gap)
       [gap/gap message idx messages-list-ref false chat-id]
       ; message content
       [message/chat-message
        (assoc message
               :incoming-group (and group-chat (not outgoing))
               :group-chat group-chat
               :public? public?
               :current-public-key current-public-key
               :show-input? show-input?
               :message-pin-enabled message-pin-enabled
               :pinned true)
        space-keeper]))])

(defn pinned-messages []
  (let [bottom-space (reagent/atom 0)
        panel-space (reagent/atom 52)
        active-panel (reagent/atom nil)
        position-y (animated/value 0)
        pan-state (animated/value 0)
        text-input-ref (quo.react/create-ref)
        pan-responder (accessory/create-pan-responder position-y pan-state)
        space-keeper (get-space-keeper-ios bottom-space panel-space active-panel text-input-ref)]
    (fn []
      (let [{:keys [chat-id] :as chat} @(re-frame/subscribe [:chats/current-chat-chat-view])
            max-bottom-space (max @bottom-space @panel-space)
            pinned-messages (re-frame/subscribe [:chats/raw-chat-pin-messages-stream chat-id])]
        [:<>
         [pins-topbar]
         [connectivity/loading-indicator]
         (if (= (count @pinned-messages) 0)
           [pinned-messages-empty]
           [chat/messages-view {:chat          chat
                                :bottom-space  max-bottom-space
                                :pan-responder pan-responder
                                :space-keeper  space-keeper
                                :inverted-ui   true
                                :show-header   false
                                :show-footer   false}
            pinned-messages
            render-pin-fn])]))))