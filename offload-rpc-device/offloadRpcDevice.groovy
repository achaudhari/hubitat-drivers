/*
 * Processing Offload RPC Device
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
    definition (name: "Processing Offload RPC Device", namespace: "achaudhari", author: "Ashish Chaudhari") {
        capability "Actuator"
        
        attribute "reqTimestamp", "string"
        attribute "reqMethod", "string"
        attribute "reqStatus", "number"
        attribute "respStatus", "number"
        attribute "respResult", "string"
        attribute "respTimestamp", "string"

        command "runCmd", [[name:"method", type:"STRING", description:"RPC method to run"],
                           [name:"params", type:"STRING", description:"JSON formatted parameters"],
                           [name:"timeout", type:"NUMBER", description:"Operation timeout in seconds"]]   
    }   
    preferences {
        input(name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true)
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
    sendEvent(name:"respResult", value:respResult)
    def dateTime = new Date()
    sendEvent(name: "respTimestamp", value: dateTime.format("yyyy-MM-dd HH:mm:ss"), isStateChange: true)
}

def runCmd(method, params, timeout) {
    if (params == null) {
        params = '[""]'
    }
    if (logEnable) log.debug "runCmd(method = ${method}, params = ${params})"
    def dateTime = new Date()
    sendEvent(name: "reqTimestamp", value: dateTime.format("yyyy-MM-dd HH:mm:ss"), isStateChange: true)
    sendEvent(name: "reqMethod", value: method)

    try {
        params_blob = parseJson(params)
    } catch (groovy.json.JsonException ex) {
        sendEvent(name:"reqStatus", value:-1)
        log.warn "Params JSON was malformed"
        return
    }

    Map postParams = [
        uri: remoteUri,
        contentType: "application/json",
        requestContentType: 'application/json',
        body: ["jsonrpc": "2.0", "id": 0, "method": method, "params": params_blob],
        timeout: timeout
    ]
    asynchttpPost("httpResponseHandler", postParams)
    if (logEnable) log.debug "POST sent to ${remoteUri}"
    sendEvent(name:"reqStatus", value:0)
}
