/*
 * Email Notification Device
 *
 * Copyright 2022 Ashish Chaudhari
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

metadata {
    definition (name: "Email Notification Device", namespace: "achaudhari", author: "Ashish Chaudhari") {
        capability "Notification"

        attribute "lastSubject", "string"
        attribute "lastBody", "string"
        attribute "respStatus", "number"
        attribute "respErrMsg", "string"
        attribute "respTimestamp", "string"

    }   
    preferences {
        input(name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true)
        input(name: "emailAddr", type: "str", title: "Email address to send to")
        input(name: "remoteUri", type: "str", title: "Offload Device URI")
    }
}

#include offrpclib.offrpclib_v1

void installed() {
}

void updated() {
}

void deviceNotification(notificationText) {
    if (logEnable) log.debug "deviceNotification(notificationText = ${notificationText})"
    sendEvent(name: "deviceNotification", value: notificationText, isStateChange: true)

    subject = "Generic Notification"
    body = notificationText
    try {
        obs = parseJson(notificationText)
        if (obs.subject != null && obs.body != null) {
            subject = obs.subject
            body = obs.body
        } else {
            log.warn "Notification JSON did not have the correct keys"
        }
    } catch (groovy.json.JsonException ex) {
        log.warn "Notification JSON was malformed"
    }
    sendEvent(name:"lastSubject", value:subject)
    sendEvent(name:"lastBody", value:body)

    retVal = rpcCall(remoteUri, "email_text", [emailAddr, subject, body], 10)
    if (retVal["respStatus"] == 0) {
        sendEvent(name: "respErrMsg", value: "")
        if (logEnable) log.debug "Notification sent: (subject = ${subject}, body = ${body})"
    } else {
        sendEvent(name: "respErrMsg", value: retVal["respResult"])
        log.error "Could not send notification: (subject = ${subject}, body = ${body})"
    }

    sendEvent(name: "respStatus", value: retVal["respStatus"])
    sendEvent(name: "respTimestamp", value: retVal["respTimestamp"], isStateChange: true)
}