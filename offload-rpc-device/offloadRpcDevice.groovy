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
        capability "Initialize"
        capability "Actuator"
        capability "MotionSensor"
        capability "HealthCheck"
        capability "PresenceSensor"

        attribute "motion", "string"
        attribute "presence", "string"
        attribute "health", "string"
        attribute "lastUpdated", "string"
        attribute "reqTimestamp", "string"
        attribute "reqMethod", "string"
        attribute "respStatus", "number"
        attribute "respResult", "string"
        attribute "respTimestamp", "string"

        command "runCmd", [[name:"method", type:"STRING", description:"RPC method to run"],
                           [name:"params", type:"STRING", description:"JSON formatted parameters"],
                           [name:"timeout", type:"NUMBER", description:"Operation timeout in seconds (0 = infinite)"]]
        command "reboot"
        command "shutdown"
        command "hubSafeShutdown"
    }
    preferences {
        input(name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false)
        input(name: "remoteUri", type: "str", title: "Offload Device URI")
        input(name: "checkInterval", type: "number", title: "Check interval in seconds (0 = disabled)", defaultValue: 0)
        input(name: "authCookie", type: "str", title: "Authentication Cookie")
    }
}

#include offrpclib.offrpclib_v1

def initialize() {
    sendEvent(name: "motion", value: "inactive")
    sendEvent(name: "presence", value: "not present")
    sendEvent(name: "reqTimestamp", value: "")
    sendEvent(name: "reqMethod", value: "")
    sendEvent(name: "respStatus", value: "")
    sendEvent(name: "respResult", value: "")
    sendEvent(name: "respTimestamp", value: "")
    sendEvent(name: "lastUpdated", value:"")
    ping()
    if (checkInterval > 0) heartbeatLoop()
}

def updated() {
    initialize()
}

def heartbeatLoop() {
    check_health()
    if (checkInterval > 0) runIn(checkInterval, "heartbeatLoop")
}

def ping() {
    try {
        retVal = rpcCall(remoteUri, "echo", ["heartbeat"], 10)
        if (retVal["respStatus"] == 0)
            sendEvent(name: "presence", value: "present")
        else
            sendEvent(name: "presence", value: "not present")
        sendEvent(name:"lastUpdated", value:(new Date().format("yyyy-MM-dd HH:mm:ss")))
    } catch (Exception e) {
        log.warn "Heartbeat ping failed but continuing loop..."
    }
}

def check_health() {
    try {
        retVal = rpcCall(remoteUri, "check_health", [], 10)
        if (retVal["respStatus"] == 0 && retVal["respResult"]["overall"] == "OKAY")
            sendEvent(name: "presence", value: "present")
        else
            sendEvent(name: "presence", value: "not present")
        sendEvent(name:"lastUpdated", value:(new Date().format("yyyy-MM-dd HH:mm:ss")))
        sendEvent(name:"health", value: retVal["respResult"])
    } catch (Exception e) {
        log.warn "Heartbeat ping failed but continuing loop..."
    }
}

def runCmd(method, params, timeout) {
    if (params == null) {
        params_blob = []
    } else {
        try {
            params_blob = parseJson(params)
        } catch (groovy.json.JsonException ex) {
            log.warn "params JSON was malformed"
            return
        }
    }
    sendEvent(name: "motion", value: "active", isStateChange: true)
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
    sendEvent(name: "lastUpdated", value:(new Date().format("yyyy-MM-dd HH:mm:ss")))
}

def reboot() {
    log.info "Rebooting offload device"
    retVal = rpcCall(remoteUri, "reboot", [authCookie], 10)
    if (retVal["respStatus"] == 0) {
        log.info "Reboot request sent successfully"
        sendEvent(name: "motion", value: "inactive")
        sendEvent(name: "presence", value: "not present")
        runIn(60, "initialize")
    } else {
        log.error "Reboot request failed"
    }
}

def shutdown() {
    log.info "Shutting down offload device"
    retVal = rpcCall(remoteUri, "shutdown", [authCookie], 10)
    if (retVal["respStatus"] == 0) {
        log.info "Shutdown request sent successfully"
        sendEvent(name: "motion", value: "inactive")
        sendEvent(name: "presence", value: "not present")
    } else {
        log.error "Shutdown request failed"
    }
}

def hubSafeShutdown() {
    log.info "Safely shutting down hub and offload device"
    retVal = rpcCall(remoteUri, "hub_safe_shutdown", [authCookie], 10)
    if (retVal["respStatus"] == 0) {
        log.info "Safe shutdown request sent successfully"
    } else {
        log.error "Safe shutdown request failed"
    }    
}
