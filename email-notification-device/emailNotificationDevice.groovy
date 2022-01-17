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

void installed() {
    log.debug "installed()"
}

void updated() {
    log.debug "updated()"
}

void httpResponseHandler(resp, data) {
    respStatus = -1
    respResult = ""
    if (resp.status >= 300) {
        respResult = "HTTP POST Status = ${resp.status}"
        log.error respResult
    } else {
        try {
            respStatus = -2
            resp_blob = parseJson(resp.getData())
            if (resp_blob.containsKey("error")) {
                respStatus = resp_blob.error.code
                respResult = "${resp_blob.error.message}, ${resp_blob.error.data}"
                log.error "JSONRPC Error = ${resp_blob.error}}"
            } else if (resp_blob.containsKey("result")) {
                respStatus = 0
                respResult = resp_blob.result
                log.debug "JSONRPC Success. Result = ${resp_blob.result}}"
            }
        } catch (groovy.json.JsonException ex) {
            log.warn "Response JSON was malformed"
        }
    }
    sendEvent(name:"respStatus", value:respStatus)
    sendEvent(name:"respErrMsg", value:respResult)
    def dateTime = new Date()
    sendEvent(name: "respTimestamp", value: dateTime.format("yyyy-MM-dd HH:mm:ss"), isStateChange: true)
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

    rpcParams = "[\"${emailAddr}\", \"${subject}\", \"${body}\"]"
    sendEvent(name:"subject", value:subject)
    sendEvent(name:"body", value:body)
    Map postParams = [
        uri: remoteUri,
        contentType: "application/json",
        requestContentType: 'application/json',
        body: ["jsonrpc": "2.0", "id": 0, "method": "email_text", "params": parseJson(rpcParams)],
        timeout: 10
    ]
    asynchttpPost("httpResponseHandler", postParams)
    if (logEnable) log.debug "POST sent to ${remoteUri}"
}