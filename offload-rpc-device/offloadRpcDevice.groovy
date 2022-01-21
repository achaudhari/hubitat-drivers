/*
 * Processing Offload Device
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
    definition (name: "Processing Offload Device", namespace: "achaudhari", author: "Ashish Chaudhari") {
        capability "Actuator"
        
        attribute "reqTimestamp", "string"
        attribute "reqMethod", "string"
        attribute "respStatus", "number"
        attribute "respResult", "string"
        attribute "respTimestamp", "string"

        command "runCmd", [[name:"method", type:"STRING", description:"RPC method to run"],
                           [name:"params", type:"STRING", description:"JSON formatted parameters"],
                           [name:"timeout", type:"NUMBER", description:"Operation timeout in seconds (0 = infinite)"]]   
    }   
    preferences {
        input(name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false)
        input(name: "remoteUri", type: "str", title: "Offload Device URI")
    }
}

#include offrpclib.offrpclib_v1

void installed() {
}

void updated() {
}

def runCmd(method, params_txt, timeout) {
    if (params_txt == null) {
        params = []
    } else {
        try {
            params = parseJson(params_txt)
        } catch (groovy.json.JsonException ex) {
            log.warn "Params JSON was malformed"
            return fCallOutput
        }
    }
    if (logEnable) log.debug "Dispatching rpcCall(method = ${method}, params = ${params})"
    retVal = rpcCall(remoteUri, method, params, timeout)
    if (retVal["respStatus"] == 0) {
        if (logEnable) log.debug "rpcCall(method = ${method}, params = ${params}) was successful"
    } else {
        log.error "rpcCall(method = ${method}, params = ${params}) failed with status ${retVal['respStatus']}"
    }
    
    sendEvent(name: "reqTimestamp", value: retVal["reqTimestamp"], isStateChange: true)
    sendEvent(name: "reqMethod", value: retVal["reqMethod"])
    sendEvent(name: "respStatus", value: retVal["respStatus"])
    sendEvent(name: "respResult", value: retVal["respResult"])
    sendEvent(name: "respTimestamp", value: retVal["respTimestamp"], isStateChange: true)
}