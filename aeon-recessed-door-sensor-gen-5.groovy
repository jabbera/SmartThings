/**
 *  Aeon Recessed Door Sensor
 *  Based On: Z-Wave Door/Window Sensor
 *
 *  Author: Mike
 *  Modified by: Mike
 *  Date: 2014-12-28
 */

// for the UI
metadata {
	// Automatically generated. Make future change here.
	definition (name: "Aeon Recessed Door Sensor", namespace: "jabbera", author: "Mike") {
		capability "Contact Sensor"
		capability "Sensor"
		capability "Battery"

		fingerprint deviceId: "0x0701", inClusters: "0x5E,0x86,0x72,0x98,0xEF,0x5A,0x82"
	}

	// simulator metadata
	simulator {
		// status messages
		status "open":  "command: 2001, payload: FF"
		status "closed": "command: 2001, payload: 00"
	}

	// UI tile definitions
	tiles {
		standardTile("contact", "device.contact", width: 2, height: 2) {
			state "open", label: '${name}', icon: "st.contact.contact.open", backgroundColor: "#ffa81e"
			state "closed", label: '${name}', icon: "st.contact.contact.closed", backgroundColor: "#79b821"
		}
        valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") {
			state "battery", label:'${currentValue}% battery', unit:""
		}


		main "contact"
		details(["contact", "battery"])
	}
}

def parse(String description) {
	def result = null
    
    if (description.startsWith("Err")) {
	    result = createEvent(descriptionText:description)
	} else if (description == "updated") {
		if (!state.MSR) {
        	log.debug("NO MSR")
			result = [
				secure(zwave.wakeUpV1.wakeUpIntervalSet(seconds:8*60*60, nodeid:zwaveHubNodeId)),
				secure(zwave.manufacturerSpecificV2.manufacturerSpecificGet()),
			]
		}
	} else {
		def cmd = zwave.parse(description, [0x20: 1, 0x25: 1, 0x30: 1, 0x31: 5, 0x80: 1, 0x84: 1, 0x71: 3, 0x9C: 1])
		if (cmd) {
			result = zwaveEvent(cmd)
		}
    }

	// log.debug("Parse returned ${result}")

	return result
}

// Devices that support the Security command class can send messages in an encrypted form;
// they arrive wrapped in a SecurityMessageEncapsulation command and must be unencapsulated
def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
        def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x25: 1, 0x30: 1, 0x31: 5, 0x80: 1, 0x84: 1, 0x71: 3, 0x9C: 1]) // can specify command class versions here like in zwave.parse
        if (encapsulatedCommand) {
                return zwaveEvent(encapsulatedCommand)
        }
}


def sensorValueEvent(value) {
	if (value) {
		createEvent(name: "contact", value: "open", descriptionText: "$device.displayName is open")
	} else {
		createEvent(name: "contact", value: "closed", descriptionText: "$device.displayName is closed")
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{
	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd)
{
	def result = [createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)]
    
    if (!state.lastbat || (new Date().time) - state.lastbat > 12*60*60*1000) {
		result << secure(zwave.batteryV1.batteryGet())
		result << response("delay 1200")
	}
	result << secure(zwave.wakeUpV1.wakeUpNoMoreInformation())
	result
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def map = [ name: "battery", unit: "%" ]
	if (cmd.batteryLevel == 0xFF) {
		map.value = 1
		map.descriptionText = "${device.displayName} has a low battery"
		map.isStateChange = true
	} else {
		map.value = cmd.batteryLevel
	}
	state.lastbat = new Date().time
	[createEvent(map), secure(zwave.wakeUpV1.wakeUpNoMoreInformation())]
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	def result = []

	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	log.debug "msr: $msr"
	updateDataValue("MSR", msr)

	result << createEvent(descriptionText: "$device.displayName MSR: $msr", isStateChange: false)

	if (msr == "011A-0601-0901") {  // Enerwave motion doesn't always get the associationSet that the hub sends on join
		result << secure(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId))
	} else if (!device.currentState("battery")) {
		result << secure(zwave.batteryV1.batteryGet())
	}

	result
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	createEvent(descriptionText: "$device.displayName: $cmd", displayed: false)
}

private secure(physicalgraph.zwave.Command cmd) {
	response(zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format())
}
