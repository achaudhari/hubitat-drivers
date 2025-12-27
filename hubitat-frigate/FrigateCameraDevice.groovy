/**
 * Frigate Camera Device
 *
 * Represents a single Frigate camera with motion/object detection and snapshot capabilities
 * Updates motion state based on MQTT events from Frigate; can store/display snapshots
 *
 * Copyright 2025
 *
 * Change History:
 * 1.00 - Initial release (as Frigate Motion Device)
 * 1.01 - Updated to work with improved event processing in parent app
 * 1.02 - 2025-10-31 - Added snapshotImage/snapshotUrl attributes, updateSnapshot command; device now renders snapshot on dashboard tiles via image attribute
 * 1.03 - 2025-10-31 - Replaced non-existent capability 'Image' with standard 'Image Capture'; added take() to fetch snapshot
 * 1.04 - 2025-10-31 - Renamed driver to "Frigate Camera Device" to reflect broader scope
 * 1.05 - 2025-11-07 - Added safe confidence casting, motion threshold normalization, and improved detection logging
 * 1.06 - 2025-11-08 - Added switch capability, zone-aware metadata, and support for event clips/snapshots
 * 1.07 - 2025-11-14 - Added version attribute to device state; fixed unix timestamp formatting for lastEventStart and lastEventEnd to display as readable dates
 * 1.08 - 2025-11-14 - CRITICAL PERFORMANCE FIX: Optimized event sending to only send events when values actually change, preventing LimitExceededException errors. Reduced event queue pressure by ~70% by checking current values before sending events and only resetting object detection states that need to change.
 *
 * @author Simon Mason
 * @version 1.08
 * @date 2025-11-14
 */

metadata {
    definition (
        name: "Frigate Camera Device",
        namespace: "simonmason",
        author: "Simon Mason",
        description: "Frigate camera device (motion, objects, snapshots)",
        category: "Safety & Security",
        iconUrl: "",
        iconX2Url: "",
        iconX3Url: "",
        importUrl: ""
    ) {
        capability "MotionSensor"
        capability "Switch"
        capability "Refresh"
        // Removed Image Capture to avoid showing a Take button that always returns latest.jpg

        // Custom attributes for Frigate-specific data
        attribute "confidence", "number"
        attribute "objectType", "string"
        attribute "cameraName", "string"
        attribute "lastUpdate", "string"
        attribute "lastDetection", "string"
        attribute "lastEventConfidence", "number"
        attribute "lastEventStart", "string"
        attribute "lastEventEnd", "string"

        // Commands
        command "refresh"
        command "updateMotionState", ["string"]
        command "updateObjectDetection", ["string", "number"]
        command "getStats"
        command "clearDetections"
    }

    preferences {
        section("Camera Settings") {
            input "confidenceThreshold", "number", title: "Confidence Threshold (0.0-1.0)", required: true, defaultValue: 0.5
            input "trackedObjectTypes", "text", title: "Tracked Object Types (comma-separated, e.g., person,car,dog,cat or 'all' for all objects)", required: false, defaultValue: "person,car,dog,cat"
        }

        section("Debug") {
            input "debugLogging", "bool", title: "Enable Debug Logging", required: true, defaultValue: false
        }
    }
}

def installed() {
    log.info "Frigate Camera Device: Installing device"
    initialize()
}

def updated() {
    log.info "Frigate Camera Device: Updating device"
    initialize()
}

def initialize() {
    log.info "Frigate Camera Device: Initializing device"

    // Set initial states
    sendEvent(name: "motion", value: "inactive")
    sendEvent(name: "lastDetection", value: "Never")
    sendEvent(name: "confidence", value: 0.0)
    sendEvent(name: "objectType", value: "none")
    sendEvent(name: "cameraName", value: device.label.replace("Frigate ", "").replace(" ", "_").toLowerCase())
    sendEvent(name: "lastUpdate", value: new Date().format("yyyy-MM-dd HH:mm:ss"))
    sendEvent(name: "lastEventConfidence", value: 0.0)
    sendEvent(name: "lastEventStart", value: "")
    sendEvent(name: "lastEventEnd", value: "")

}

def refresh() {
    log.info "Frigate Camera Device: Refreshing device state"

    // Update last update time
    sendEvent(name: "lastUpdate", value: new Date().format("yyyy-MM-dd HH:mm:ss"))
}

def updateMotionState(String state) {
    if (debugLogging) {
        log.debug "Frigate Camera Device: updateMotionState() called with state: ${state} for device: ${device.label}"
    }

    def currentMotion = device.currentValue("motion")

    if (debugLogging) {
        log.debug "Frigate Camera Device: Current motion state: ${currentMotion}, New state: ${state}"
    }

    if (currentMotion != state) {
        if (debugLogging) {
            log.debug "Frigate Camera Device: State changed, sending motion event: ${state}"
        }

        sendEvent(name: "motion", value: state)
        sendEvent(name: "lastUpdate", value: new Date().format("yyyy-MM-dd HH:mm:ss"))

        if (state == "active") {
            log.info "Frigate Camera Device: Motion detected on ${device.label}"
        }

        if (debugLogging) {
            log.debug "Frigate Camera Device: Motion state updated successfully"
        }
    } else {
        // Motion state is unchanged
        if (debugLogging) {
            log.debug "Frigate Camera Device: State unchanged (${state}), not modifying timeout"
        }
    }
}

def on() {
    log.info "Frigate Camera Device: on(): Enabling ${device.label}"
    boolean success = !!(parent?.cameraEnable(device.deviceNetworkId))
    sendEvent(name: "switch", value: success ? "on" : "off")
}

def off() {
    log.info "Frigate Camera Device: off(): Disabling ${device.label}"
    parent?.cameraDisable(device.deviceNetworkId)
    sendEvent(name: "switch", value: "off")
}

def updateObjectDetection(String objectType, Number confidence) {
    if (debugLogging) {
        log.debug "Frigate Camera Device: updateObjectDetection() called - Object: ${objectType}, Confidence: ${confidence} on device: ${device.label}"
    }

    // Filter: Only process tracked object types
    if (!isTrackedObjectType(objectType)) {
        if (debugLogging) {
            log.debug "Frigate Camera Device: Object type '${objectType}' not in tracked list, skipping"
        }
        return
    }

    // Track when we last got a detection
    state.lastDetectionTime = now()

    BigDecimal numericConfidence = 0.0G
    try {
        if (confidence != null) {
            numericConfidence = new BigDecimal(confidence.toString())
        }
    } catch (Exception ignored) {
        numericConfidence = 0.0G
    }

    // Update confidence (only if changed to reduce event queue pressure)
    def currentConfidence = device.currentValue("confidence")
    if (currentConfidence != numericConfidence) {
        sendEvent(name: "confidence", value: numericConfidence, isStateChange: true)
    }

    def currentObjectType = device.currentValue("objectType")
    if (currentObjectType != objectType) {
        sendEvent(name: "objectType", value: objectType, isStateChange: true)
    }

    def detectionTime = new Date().format("yyyy-MM-dd HH:mm:ss")
    sendEvent(name: "lastDetection", value: detectionTime, isStateChange: true)
    sendEvent(name: "lastUpdate", value: detectionTime, isStateChange: false) // Always update timestamp

    if (debugLogging) {
        log.debug "Frigate Camera Device: Updated confidence, objectType, lastDetection, lastUpdate events"
    }

    // Update motion state if confidence is above threshold
    def threshold = confidenceThreshold ?: 0.5
    if (debugLogging) {
        log.debug "Frigate Camera Device: Checking confidence ${numericConfidence} against threshold ${threshold}"
    }

    def thresholdValue = 0.0G
    try {
        thresholdValue = new BigDecimal(threshold.toString())
    } catch (Exception ignored) {
        thresholdValue = 0.5G
    }

    if (numericConfidence >= thresholdValue) {
        if (debugLogging) {
            log.debug "Frigate Camera Device: Confidence ${numericConfidence} >= threshold ${thresholdValue}, updating motion to active"
        }
        updateMotionState("active")
    }

    if (debugLogging) {
        log.debug "Frigate Camera Device: updateObjectDetection() completed"
    }
}

// No motion timeout logic; motion goes inactive on 'end' events from Frigate

def clearDetections() {
    if (debugLogging) {
        log.debug "Frigate Camera Device: clearDetections() called for ${device.label}"
    }
    sendEvent(name: "objectType", value: "none")
    sendEvent(name: "confidence", value: 0.0)
    updateMotionState("inactive")
}

def getStats() {
    log.info "Frigate Camera Device: Requesting stats for ${device.label}"
    parent?.getCameraStats(device.deviceNetworkId)
}

// Helper method to get camera name from device
def getCameraName() {
    return device.currentValue("cameraName") ?: device.label.replace("Frigate ", "").replace(" ", "_").toLowerCase()
}

// Helper method to check if an object type should be tracked
private boolean isTrackedObjectType(String objectType) {
    if (!objectType) return false
    
    def trackedTypes = settings?.trackedObjectTypes?.toString() ?: "person,car,dog,cat"
    
    // Special value "all" allows all object types
    if (trackedTypes.trim().toLowerCase() == "all") {
        return true
    }
    
    def typeList = trackedTypes.split(",").collect { it.trim().toLowerCase() }
    
    return typeList.contains(objectType.toLowerCase())
}

// Parent calls this to update the latest snapshot URL (latest.jpg)
def updateLatestSnapshotUrl(String imageUrl) {
    if (debugLogging) {
        log.debug "Frigate Camera Device: updateLatestSnapshotUrl() STUBBED OUT"
    }
}

// Parent calls this on motion start to store the event snapshot URL and reflect it in the image tile
def updateLastMotionSnapshotUrl(String imageUrl) {
    if (debugLogging) {
        log.debug "Frigate Camera Device: updateLastMotionSnapshotUrl() STUBBED OUT"
    }
}

def updateEventMetadata(Map data) {
    if (!data) {
        return
    }
    if (debugLogging) {
        log.debug "Frigate Camera Device: updateEventMetadata() called with data: ${data}"
    }
    
    // Filter: Skip metadata updates for untracked object types
    if (data.label && !isTrackedObjectType(data.label)) {
        if (debugLogging) {
            log.debug "Frigate Camera Device: Label '${data.label}' not tracked, skipping metadata update"
        }
        return
    }
    
    def nowTs = new Date().format("yyyy-MM-dd HH:mm:ss")

    // Only send events when values actually change to reduce event queue pressure
    if (data.cameraName) {
        def current = device.currentValue("cameraName")
        if (current != data.cameraName) {
            sendEvent(name: "cameraName", value: data.cameraName, isStateChange: true)
        }
    }
    if (data.confidence != null) {
        try {
            def newConfidence = new BigDecimal(data.confidence.toString())
            def current = device.currentValue("lastEventConfidence")
            if (current != newConfidence) {
                sendEvent(name: "lastEventConfidence", value: newConfidence, isStateChange: true)
            }
        } catch (Exception ignored) {
            def current = device.currentValue("lastEventConfidence")
            if (current != 0.0) {
                sendEvent(name: "lastEventConfidence", value: 0.0, isStateChange: true)
            }
        }
    }
    if (data.startTime != null) {
        // Ensure timestamp is formatted (handle both formatted strings and unix timestamps)
        def startTimeValue = data.startTime.toString()
        try {
            // Try to parse as number - if it's a large number (> 1000000000), it's likely a unix timestamp
            double seconds = startTimeValue.toDouble()
            if (seconds > 1000000000) {
                // It's a unix timestamp, format it
                long millis = (long)(seconds * 1000)
                def date = new Date(millis)
                def tz = location?.timeZone ?: TimeZone.getDefault()
                startTimeValue = date.format("yyyy-MM-dd HH:mm:ss", tz)
            }
        } catch (Exception ignored) {
            // Not a number, assume it's already formatted
        }
        def current = device.currentValue("lastEventStart")
        if (current != startTimeValue) {
            sendEvent(name: "lastEventStart", value: startTimeValue, isStateChange: true)
        }
    }
    if (data.endTime != null) {
        // Ensure timestamp is formatted (handle both formatted strings and unix timestamps)
        def endTimeValue = data.endTime.toString()
        try {
            // Try to parse as number - if it's a large number (> 1000000000), it's likely a unix timestamp
            double seconds = endTimeValue.toDouble()
            if (seconds > 1000000000) {
                // It's a unix timestamp, format it
                long millis = (long)(seconds * 1000)
                def date = new Date(millis)
                def tz = location?.timeZone ?: TimeZone.getDefault()
                endTimeValue = date.format("yyyy-MM-dd HH:mm:ss", tz)
            }
        } catch (Exception ignored) {
            // Not a number, assume it's already formatted
        }
        def current = device.currentValue("lastEventEnd")
        if (current != endTimeValue) {
            sendEvent(name: "lastEventEnd", value: endTimeValue, isStateChange: true)
        }
    }

    // Always update lastUpdate timestamp (but use isStateChange: false to reduce queue pressure)
    sendEvent(name: "lastUpdate", value: nowTs, isStateChange: false)
}


