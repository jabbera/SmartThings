// UNTESTED
metadata {
	// Automatically generated. Make future change here.
	definition (name: "Secure Z-Wave Metering Switch", namespace: "jabbera", author: "Mike") {
		capability "Energy Meter"
		capability "Actuator"
		capability "Switch"
		capability "Power Meter"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"

		command "reset"

		fingerprint inClusters: "0x25,0x32"
	}

	// simulator metadata
	simulator {
		status "on":  "command: 2003, payload: FF"
		status "off": "command: 2003, payload: 00"

		for (int i = 0; i <= 10000; i += 1000) {
			status "power  ${i} W": new physicalgraph.zwave.Zwave().meterV1.meterReport(
				scaledMeterValue: i, precision: 3, meterType: 4, scale: 2, size: 4).incomingMessage()
		}
		for (int i = 0; i <= 100; i += 10) {
			status "energy  ${i} kWh": new physicalgraph.zwave.Zwave().meterV1.meterReport(
				scaledMeterValue: i, precision: 3, meterType: 0, scale: 0, size: 4).incomingMessage()
		}

		// reply messages
		reply "2001FF,delay 100,2502": "command: 2503, payload: FF"
		reply "200100,delay 100,2502": "command: 2503, payload: 00"

	}

	// tile definitions
	tiles {
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
			state "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821"
			state "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
		}
		valueTile("power", "device.power", decoration: "flat") {
			state "default", label:'${currentValue} W'
		}
		valueTile("energy", "device.energy", decoration: "flat") {
			state "default", label:'${currentValue} kWh'
		}
		standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat") {
			state "default", label:'reset kWh', action:"reset"
		}
		standardTile("configure", "device.power", inactiveLabel: false, decoration: "flat") {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}
		standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main "switch"
		details(["switch","power","energy","reset","configure","refresh"])
	}
}

def parse(String description) {
	def result = null
	def cmd = zwave.parse(description, [0x20: 1, 0x32: 1])
	if (cmd) {
		result = createEvent(zwaveEvent(cmd))
	}
	return result
}

// Devices that support the Security command class can send messages in an encrypted form;
// they arrive wrapped in a SecurityMessageEncapsulation command and must be unencapsulated
def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
        def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x32: 1]) // can specify command class versions here like in zwave.parse
        if (encapsulatedCommand) {
                return zwaveEvent(encapsulatedCommand)
        }
}
def zwaveEvent(physicalgraph.zwave.commands.meterv1.MeterReport cmd) {
	if (cmd.scale == 0) {
		[name: "energy", value: cmd.scaledMeterValue, unit: "kWh"]
	} else if (cmd.scale == 1) {
		[name: "energy", value: cmd.scaledMeterValue, unit: "kVAh"]
	}
	else {
		[name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W"]
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd)
{
	[
		name: "switch", value: cmd.value ? "on" : "off", type: "physical"
	]
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd)
{
	[
		name: "switch", value: cmd.value ? "on" : "off", type: "digital"
	]
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	// Handles all Z-Wave commands we aren't interested in
	[:]
}

def on() {
	delayBetween([
			secure(zwave.basicV1.basicSet(value: 0xFF)),
			secure(zwave.switchBinaryV1.switchBinaryGet())
	])
}

def off() {
	delayBetween([
			secure(zwave.basicV1.basicSet(value: 0x00)),
			secure(zwave.switchBinaryV1.switchBinaryGet())
	])
}

def poll() {
	delayBetween([
		secure(zwave.switchBinaryV1.switchBinaryGet()),
		secure(zwave.meterV2.meterGet(scale: 0)),
		secure(zwave.meterV2.meterGet(scale: 2))
	])
}

def refresh() {
	delayBetween([
		secure(zwave.switchBinaryV1.switchBinaryGet()),
		secure(zwave.meterV2.meterGet(scale: 0)),
		secure(zwave.meterV2.meterGet(scale: 2))
	])
}

def reset() {
	return [
		secure(zwave.meterV2.meterReset()),
		secure(zwave.meterV2.meterGet(scale: 0))
	]
}

def configure() {
	delayBetween([
		secure(zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: 4)),   // combined power in watts
		secure(zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: 300)), // every 5 min
		secure(zwave.configurationV1.configurationSet(parameterNumber: 102, size: 4, scaledConfigurationValue: 8)),   // combined energy in kWh
		secure(zwave.configurationV1.configurationSet(parameterNumber: 112, size: 4, scaledConfigurationValue: 300)), // every 5 min
		secure(zwave.configurationV1.configurationSet(parameterNumber: 103, size: 4, scaledConfigurationValue: 0)),    // no third report
		secure(zwave.configurationV1.configurationSet(parameterNumber: 113, size: 4, scaledConfigurationValue: 300)) // every 5 min
	])
}

private secure(physicalgraph.zwave.Command cmd) {
	response(zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format())
}