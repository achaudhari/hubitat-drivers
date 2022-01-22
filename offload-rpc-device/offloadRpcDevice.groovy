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
        capability "MotionSensor"
        capability "HealthCheck"
        capability "PresenceSensor"

        attribute "motion", "string"
        attribute "presence", "string"
        attribute "lastUpdated", "string"
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
        input(name: "checkInterval", type: "number", title: "Check interval in seconds (0 = disabled)", defaultValue: 0)
    }
}

#include offrpclib.offrpclib_v1

def initialized() {
    sendEvent(name: "motion", value: "inactive")
    sendEvent(name: "presence", value: "not present")
}

def updated() {
    initialized()
    ping()
    if (checkInterval > 0) heartbeatLoop()
}

def heartbeatLoop() {
    ping()
    if (checkInterval > 0) runIn(checkInterval, "heartbeatLoop")
}

def ping() {
    retVal = rpcCall(remoteUri, "echo", ["heartbeat"], 10)
    if (retVal["respStatus"] == 0)
        sendEvent(name: "presence", value: "present")
    else
        sendEvent(name: "presence", value: "not present")
    sendEvent(name:"lastUpdated", value:(new Date().format("yyyy-MM-dd HH:mm:ss")))
    if (logEnable) log.debug "ping: Offload RPC server is up"
}

def runCmd(method, params, timeout) {
    if (device.currentValue('motion') == 'inactive') {
        sendEvent(name: "motion", value: "active", isStateChange: true)
        if (params == null) {
            params_blob = []
        } else {
            try {
                params_blob = parseJson(params)
            } catch (groovy.json.JsonException ex) {
                log.warn "Params JSON was malformed"
                return
            }
        }
        if (logEnable) log.debug "Dispatching rpcCall(method = ${method}, params = ${params_blob}, timeout = ${timeout})"
        retVal = rpcCall(remoteUri, method, params_blob, timeout)

        sendEvent(name: "reqTimestamp", value: retVal["reqTimestamp"], isStateChange: true)
        sendEvent(name: "reqMethod", value: retVal["reqMethod"], isStateChange: true)
        sendEvent(name: "respStatus", value: retVal["respStatus"], isStateChange: true)
        sendEvent(name: "respResult", value: retVal["respResult"], isStateChange: true)
        sendEvent(name: "respTimestamp", value: retVal["respTimestamp"], isStateChange: true)

        if (retVal["respStatus"] == 0) {
            if (logEnable) log.debug "rpcCall(method = ${method}, params = ${params}, timeout = ${timeout}) was successful"
        } else {
            log.error "rpcCall(method = ${method}, params = ${params}, timeout = ${timeout}) failed with status ${retVal['respStatus']}"
        }
        sendEvent(name: "motion", value: "inactive", isStateChange: true)
        sendEvent(name:"lastUpdated", value:(new Date().format("yyyy-MM-dd HH:mm:ss")))
        return 0
    } else {
        log.error "Device busy. rpcCall(method = ${method}, params = ${params}, timeout = ${timeout}) failed"
        sendEvent(name: "respStatus", value: -1000, isStateChange: true)
        sendEvent(name: "respResult", value: "RPC device busy", isStateChange: true)
        return -1
    }
}