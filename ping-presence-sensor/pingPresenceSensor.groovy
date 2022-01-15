/*
 * Ping Presence Sensor (based on Hubitat Ping by @thebearmay)
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

static String version()	{  return '1.0.0'  }

metadata {
    definition (
		name: "Ping Presence Sensor", 
		namespace: "achaudhari", 
		author: "Ashish Chaudhari",
	) {
        capability "PresenceSensor"

	    attribute "enabled", "number"
        attribute "lastUpdateTime", "string"
    }   
}

preferences {
    input("pollingEnable", "bool", title: "Enable presence polling?", defaultValue:false, required:true)
    input("pollingInterval", "number", title: "Polling interval in seconds (0 for one-shot)", defaultValue:0, required:true)
    input("ipAddress", "string", title: "IP address of device to monitor", required: true)
    input("numPings", "number", title: "Number of pings to issue per poll", defaultValue:3, required:true, range: "1..5")
    input("debugEnable", "bool", title: "Enable debug logging?")
}

def installed() {
	log.trace "installed()"
}

def configure() {
}

def initialize() {
}

def refresh() {
	unschedule(refresh)
}

def pollingLoop() {
    hubitat.helper.NetworkUtils.PingData pingData = hubitat.helper.NetworkUtils.ping(ipAddress, numPings.toInteger())
    if (pingData.packetLoss < 100) 
        sendEvent(name:"presence", value:"present")
    else
        sendEvent(name:"presence", value:"not present")
    sendEvent(name:"lastUpdateTime", value:(new Date().toString()))
    if (pollingEnable && pollingInterval > 0) runIn(pollingInterval, "pollingLoop")
}

def validIP(ipAddress){
    regxPattern =/^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/
    boolean match = ipAddress ==~ regxPattern
    return match
}

def updated(){
	log.trace "updated()"
	if (debugEnable) runIn(1800,logsOff)

    if(!validIP(ipAddress)) {
        log.warn "Invalid IP address $ipAddress. Polling disabled."
        sendEvent(name:"enabled", value:0)
    } else if (pollingEnable) {
        sendEvent(name:"enabled", value:1)
        pollingLoop()
    } else {
        sendEvent(name:"enabled", value:0)
    }
}

void logsOff(){
    device.updateSetting("debugEnable",[value:"false",type:"bool"])
}

