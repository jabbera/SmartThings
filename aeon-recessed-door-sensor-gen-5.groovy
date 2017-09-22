/**
 *  Aeon Recessed Door Sensor
 *  Based On: Z-Wave Door/Window Sensor
 *
 *  Author: Mike
 *  Modified by: Mike
 *  Date: 2015-2-4
 *  
 *  Updated by Alex Ruffell - Battery Fix
 *  Updated On: 18-Jul-2017 
 */

// for the UI
metadata {
	// Automatically generated. Make future change here.
	definition (name: "Aeon Recessed Door Sensor", namespace: "jabbera", author: "Mike") {
		capability	"Contact Sensor"
		capability	"Sensor"
		capability	"Battery"
		capability	"Configuration"
		
		/*
		Z-Wave Command Classes
		Hex id from https://graph.api.smartthings.com/ide/doc/zwave-utils.html
		
		Manufacturer: Aeotec
		Model: Recessed Door Sensor Gen5 (ZW089-A)
		Z-Wave Certification Number: ZC10-14120008
		http://products.z-wavealliance.org/products/1179
		Product Type ID: 0x0102
		Product ID: 0x0059
		
		To Include:
		Turn the primary controller of Z-Wave network into inclusion mode, short press the product’s Z-Wave button that you can find in the back of the product. 

		To Exclude:
		Turn the primary controller of Z-Wave network into exclusion mode, short press the product’s Z-Wave button that you can find in back of the product. 

		Factory Reset:
		Press and hold the Z-Wave button that you can find in back of the product for 20 seconds and then release. This procedure should only be used when the primary controller is inoperable. 

		Wake Up:
		Press and hold the Z-Wave button for 5 seconds will trigger sending the Wake up notification command and then keep waking up for 10 seconds after release the Z-Wave button.
		
		Supported Command Classes 
		 
			cc:
			0x5E
			0x86 Version
			0x72 Manufacturer Specific 
			0x98 Security
			
			ccOut:
			0x5A Device Reset Locally
			0x82 Hail

			sec:
			0x30 Sensor Binary
			0x80 Battery
			0x84 Wake Up
			0x70 Configuration 
			0x85 Association 
			0x59 Association Grp Info
			0x71 Alarm
			0x7A Firmware Update Md
			0x73 Powerlevel
			
		*/
		
		//Raw Description
		//zw:Ss type:0701 mfr:0086 prod:0102 model:0059 ver:1.13 zwv:3.92 lib:03 cc:5E,86,72,98 ccOut:5A,82 sec:30,80,84,70,85,59,71,7A,73 role:06 ff:8C00 ui:8C00

		//Fingerprint - old method
		fingerprint deviceId: "0x0701", inClusters: "0x5E,0x86,0x72,0x98,0xEF,0x5A,0x82"
		fingerprint deviceId: "0x0701", inClusters: "0x5E 0x30 0x80 0x84 0x70 0x85 0x59 0x71 0x86 0x72 0x73 0x7A 0x98", outClusters: "0x5A 0x82"
		
		//Fingerprint - new method
		fingerprint mfr: "0086", prod: "0102", model: "0059"
		fingerprint type: "0701", cc: "5E,86,72,98", ccOut: "5A,82", sec: "sec:30,80,84,70,85,59,71,7A,73"
		
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
        standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat") {
	    	state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main (["contact"])
		details(["contact","battery","refresh"])
	}
}

/*
	Upon intial connection the switch sends an unsolicited ManufacturerSpecificReport
	We will setup the wake up interval there. This is a sleepy device meaning you cannot
	send it commands at any time. You can only send them between wake up intervals. This 
	means the interval will only change once the device has woken up and we can ask it to change
	it connot be less then 8 minutes.
*/


def parse(String description) {
	def result = null
	
	log.debug("Parse got ${description?.inspect()}")
	
	if (description.startsWith("Err")) {
		result = createEvent(descriptionText:description)
	} else if (description == "updated") {
	// nothing to do.
	} else {
		def cmd = zwave.parse(description, [0x20: 1, 0x25: 1, 0x30: 1, 0x31: 5, 0x80: 1, 0x84: 2, 0x71: 3, 0x9C: 1])
		
		if (state.debug) log.debug("Parsed command: ${cmd?.inspect()}")
		
		if (cmd) {
			result = zwaveEvent(cmd)
		}
	}

	log.debug("Parse returned ${result}")

	return result
}

// Devices that support the Security command class can send messages in an encrypted form;
// they arrive wrapped in a SecurityMessageEncapsulation command and must be unencapsulated
def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
		log.debug("Attempting to decrypt ${cmd?.inspect()}")
		def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x25: 1, 0x30: 1, 0x31: 5, 0x80: 1, 0x84: 2, 0x71: 3, 0x9C: 1]) // can specify command class versions here like in zwave.parse
		if (encapsulatedCommand) {
			log.debug("Decrypted returned ${encapsulatedCommand?.inspect()}")
				return zwaveEvent(encapsulatedCommand)
		}
		else
		{
			log.debug("Failed to decrypt ${cmd?.inspect()}")
		}
}


def sensorValueEvent(value) {
	log.debug "sensorValueEvent(value:${value})"

	if (value) {
		createEvent(name: "contact", value: "open", descriptionText: "$device.displayName is open")
	} else {
		createEvent(name: "contact", value: "closed", descriptionText: "$device.displayName is closed")
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{
	log.debug "BasicSet(value:${cmd.value})"

	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd)
{
	log.debug "WakeUpNotification. Enabling battery level reporting (if needed) and asking for battery level."

	def result = [createEvent(descriptionText: "${device.displayName} woke up", isStateChange: true, displayed: true)]

	if (state.batteryReportingEnabled != false) {
		// Set param #101 (0x65) to 1 to enable reporting of battery levels when the sensor wakes up - do this once regardless of its current value.
		// I will have to add a preference to force the state.batteryReportingEnabled to false again in case the parameter gets reset to 0 somehow
		zwave.configurationV1.configurationSet(parameterNumber: 0x65, size: 1, scaledConfigurationValue: 1)
		log.debug "Set Parameter 101 to 1 to enable battery reporting."

		state.batteryReportingEnabled = true
		log.debug "state.batteryReportingEnabled set to ${state.batteryReportingEnabled} so we do not keep setting the parameter."
	}
	
	result << secure(zwave.batteryV1.batteryGet())

	if (null == state.wakeUpSet)
	{
		def reportIntervalSec = 720*60 

		log.debug "Setting wake up interval to: ${reportIntervalSec}"
	
		result << secure(zwave.wakeUpV2.wakeUpIntervalSet(seconds:reportIntervalSec, nodeid:zwaveHubNodeId))
	}

	result << response("delay 6000") // This is to give the sensor enough time to return a result before telling the sensor to turn off its receiver           
	result << secure(zwave.wakeUpV2.wakeUpNoMoreInformation())
	result
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	log.debug "BatteryReport(${cmd?.inspect()})"

	def map = [ name: "battery", unit: "%" ]
	if (cmd.batteryLevel == 0xFF) {
		map.value = 1
		map.descriptionText = "${device.displayName} has a low battery"
		map.isStateChange = true
	} else {
		map.value = cmd.batteryLevel
		map.displayed = true
		map.isStateChange = true
	}	
	[createEvent(map), secure(zwave.wakeUpV2.wakeUpNoMoreInformation())]
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {	    
	log.debug "ManufacturerSpecificReport: ${cmd.inspect()}"

	def reportIntervalSec = 720*60 
	def result = []    

	log.debug "Setting wake up interval to: ${reportIntervalSec}"

	result << secure(zwave.wakeUpV2.wakeUpIntervalSet(seconds:reportIntervalSec, nodeid:zwaveHubNodeId));
	result << secure(zwave.batteryV1.batteryGet())

	state.wakeUpSet = true
    
	return result
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	createEvent(descriptionText: "No handler for command: $device.displayName: ${cmd.inspect()}", displayed: state.display)
}

private secure(physicalgraph.zwave.Command cmd) {
	log.debug "Securing command: ${cmd?.inspect()}"
	response(zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format())
}
