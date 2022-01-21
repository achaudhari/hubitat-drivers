/*
 * Roomba Controller
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
    definition (name: "Roomba Controller", namespace: "achaudhari", author: "Ashish Chaudhari") {
        capability "Refresh"
		capability "Battery"
        capability "Switch"
		capability "Actuator"
        capability "PushableButton"

        attribute "phase", "string"
        attribute "readyMsg", "string"
        attribute "errorMsg", "string"
        attribute "binStatus", "string"
        attribute "wifiRssi", "number"
        attribute "updateTimestamp", "string"
        
        command "start"
        command "stop"
        command "pause"
        command "resume"
        command "dock"
        command "locate"
        command "push", [[name:"action", type:"STRING"]]
    }
    preferences {
        input(name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true)
        input(name: "remoteUri", type: "str", title: "Offload Device URI")
        input(name: "pollingEnable", type: "bool", title: "Enable state polling?", defaultValue:false)
        input(name: "pollingInterval", type: "number", title: "Polling interval in seconds", defaultValue:0)
    }
}

#include offrpclib.offrpclib_v1

def start() {
    if(logEnable) log.debug "Starting Roomba via RPC"
    retVal = rpcCall(remoteUri, "roomba_send_cmd", ["start"], 30)
    if (retVal["respStatus"] == 0) {
        sendEvent(name: "switch", value: "on", isStateChange: true)
        if(logEnable) log.debug "Roomba started"
    } else {
        log.error "Could not start Roomba"
    }
}

def stop() {
    if(logEnable) log.debug "Stopping Roomba via RPC"
    retVal = rpcCall(remoteUri, "roomba_send_cmd", ["stop"], 30)
    if (retVal["respStatus"] == 0) {
        sendEvent(name: "switch", value: "off", isStateChange: true)
        if(logEnable) log.debug "Roomba stopped"
    } else {
        log.error "Could not stop Roomba"
    }
}

def pause() {
    if(logEnable) log.debug "Pausing Roomba via RPC"
    retVal = rpcCall(remoteUri, "roomba_send_cmd", ["pause"], 30)
    if (retVal["respStatus"] == 0) {
        sendEvent(name: "switch", value: "off", isStateChange: true)
        if(logEnable) log.debug "Roomba paused"
    } else {
        log.error "Could not pause Roomba"
    }
}

def resume() {
    if(logEnable) log.debug "Resuming Roomba via RPC"
    retVal = rpcCall(remoteUri, "roomba_send_cmd", ["resume"], 30)
    if (retVal["respStatus"] == 0) {
        sendEvent(name: "switch", value: "on", isStateChange: true)
        if(logEnable) log.debug "Roomba resumed"
    } else {
        log.error "Could not resume Roomba"
    }
}

def dock() {
    if(logEnable) log.debug "Docking Roomba via RPC"
    retVal = rpcCall(remoteUri, "roomba_send_cmd", ["dock"], 30)
    if (retVal["respStatus"] == 0) {
        sendEvent(name: "switch", value: "off", isStateChange: true)
        if(logEnable) log.debug "Roomba is docking now"
    } else {
        log.error "Could not dock Roomba"
    }
}

def locate() {
    if(logEnable) log.debug "Locating Roomba via RPC"
    retVal = rpcCall(remoteUri, "roomba_send_cmd", ["locate"], 30)
}

def refresh() {
    if(logEnable) log.debug "One-time update of Roomba state"
    retrieveState()
}    

def pollingLoop() {
    retrieveState()
    if (pollingEnable && pollingInterval > 0) runIn(pollingInterval, "pollingLoop")
}

def updated(){
    if (pollingEnable) {
        if(logEnable) log.debug "Starting polling loop"
        pollingLoop()
    } else {
        if(logEnable) log.debug "Polling loop disabled"
    }
}

def push(action) {
    if (action == "start")
        start()
    else if (action == "stop")
        stop()
    else if (action == "pause")
        pause()
    else if (action == "resume")
        resume()
    else if (action == "dock")
        dock()
    else if (action == "locate")
        locate()
    else if (action == "refresh")
        refresh()
    else
        log.error "Invalid button action: ${action}"
    if (action != "refresh")
        runIn(3, "retrieveState")
}

def retrieveState() {
    if(logEnable) log.debug "Updating Roomba state"
    retVal = rpcCall(remoteUri, "roomba_get_state", ["reduced"], 30)
    if (retVal["respStatus"] == 0) {
        state = retVal["respResult"]
        sendEvent(name: "battery", value: state["battery"], isStateChange: true)
        sendEvent(name: "phase", value: state["phase"], isStateChange: true)
        sendEvent(name: "readyMsg", value: state["ready_msg"], isStateChange: true)
        sendEvent(name: "errorMsg", value: state["error_msg"], isStateChange: true)
        sendEvent(name: "binStatus", value: state["bin_status"], isStateChange: true)
        sendEvent(name: "wifiRssi", value: "RSSI " + state["wifi_rssi"] + "dB", isStateChange: true)
        sendEvent(name: "updateTimestamp", value: new Date().format("yyyy-MM-dd HH:mm:ss"), isStateChange: true)
    }    
}