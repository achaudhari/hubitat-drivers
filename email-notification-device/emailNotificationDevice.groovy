/*
 * Webcore Email Notification Device
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
    }   
    preferences {
        input(name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true)
        input(name: "baseUri", type: "str", title: "Base URI for email piston")
    }
}

void installed() {
    log.debug "installed()"
}

void updated() {
    log.debug "updated()"
}

void httpResponseHandler(resp, data) {
    if (resp.status >= 300) {
        log.warn "POST Status = ${resp.status}, Data = ${resp.getData()}"
    }
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

    fullUri = baseUri + "&subject=${java.net.URLEncoder.encode(subject, "UTF-8")}&body=${java.net.URLEncoder.encode(body, "UTF-8")}"
    Map params = [
        uri: fullUri,
        contentType: "application/json",
        timeout: 15
    ]
    asynchttpPost("httpResponseHandler", params)
    if (logEnable) log.debug "POST sent to ${fullUri}"
}

