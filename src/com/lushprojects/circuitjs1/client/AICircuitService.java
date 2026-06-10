package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayNumber;
import com.google.gwt.core.client.JsArrayString;

class AICircuitService {
    private static final String ERR_TX_ACTIVE = "TRANSACTION_ACTIVE";
    private static final String ERR_TX_MISSING = "TRANSACTION_MISSING";
    private static final String ERR_NOT_FOUND = "NOT_FOUND";
    private static final String ERR_INVALID_GRID = "INVALID_GRID";
    private static final String ERR_INVALID_TYPE = "INVALID_COMPONENT_TYPE";
    private static final String ERR_OVERLAP = "OVERLAP";
    private static final String ERR_INVALID_PIN = "INVALID_PIN";
    private static final String ERR_BUS_MISMATCH = "BUS_WIDTH_MISMATCH";
    private static final String ERR_INVALID_PROPERTY = "INVALID_PROPERTY";
    private static final String ERR_INVALID_PROPERTY_TYPE = "INVALID_PROPERTY_TYPE";
    private static final String ERR_SIMULATION = "SIMULATION_ERROR";
    private static final String ERR_INVALID_ARGUMENT = "INVALID_ARGUMENT";
    private static final String ERR_UNSUPPORTED = "UNSUPPORTED";

    private final CirSim app;
    private final Map<CircuitElm, String> elmToId = new IdentityHashMap<CircuitElm, String>();
    private final Map<String, CircuitElm> idToElm = new HashMap<String, CircuitElm>();
    private int nextId = 1;
    private String transactionDump;

    AICircuitService(CirSim app) {
	this.app = app;
    }

    JavaScriptObject beginTransaction() {
	if (transactionDump != null)
	    return error(ERR_TX_ACTIVE, "A transaction is already active.");
	transactionDump = app.dumpCircuit();
	return okNoData();
    }

    JavaScriptObject commitTransaction() {
	if (transactionDump == null)
	    return error(ERR_TX_MISSING, "No active transaction.");
	transactionDump = null;
	return okNoData();
    }

    JavaScriptObject rollbackTransaction() {
	if (transactionDump == null)
	    return error(ERR_TX_MISSING, "No active transaction.");
	String dump = transactionDump;
	transactionDump = null;
	app.loader.readCircuit(dump, CircuitLoader.RC_NO_CENTER);
	rebuildIds();
	return okNoData();
    }

    JavaScriptObject listComponents() {
	syncIds();
	JsArray<JavaScriptObject> arr = createObjectArray();
	for (int i = 0; i < app.elmList.size(); i++) {
	    CircuitElm elm = app.getElm(i);
	    arr.push(componentSummary(elm));
	}
	return okArray(arr);
    }

    JavaScriptObject getComponent(String id) {
	syncIds();
	CircuitElm elm = idToElm.get(id);
	if (elm == null)
	    return error(ERR_NOT_FOUND, "Unknown component id: " + id);
	return ok(componentDetails(elm));
    }

    JavaScriptObject listNets() {
	syncIds();
	JavaScriptObject simError = ensureStamped();
	if (simError != null)
	    return simError;

	HashMap<String, JavaScriptObject> nets = new HashMap<String, JavaScriptObject>();
	JsArray<JavaScriptObject> netArray = createObjectArray();
	for (int i = 0; i < app.elmList.size(); i++) {
	    CircuitElm elm = app.getElm(i);
	    String componentId = getOrCreateId(elm);
	    for (int pin = 0; pin < elm.getPostCount(); pin++) {
		Point p = elm.getPost(pin);
		CircuitNode node = elm.getNode(pin);
		String netKey = (node != null) ? "n" + node.index : "p" + p.x + "," + p.y + "," + p.z;
		JavaScriptObject netObj = nets.get(netKey);
		if (netObj == null) {
		    netObj = createObject();
		    setString(netObj, "id", netKey);
		    if (node != null)
			setNumber(netObj, "nodeIndex", node.index);
		    JsArray<JavaScriptObject> members = createObjectArray();
		    setObject(netObj, "members", members);
		    nets.put(netKey, netObj);
		    netArray.push(netObj);
		}
		@SuppressWarnings("unchecked")
		JsArray<JavaScriptObject> members = (JsArray<JavaScriptObject>) getObject(netObj, "members");
		JavaScriptObject member = createObject();
		setString(member, "componentId", componentId);
		setNumber(member, "pin", pin);
		setNumber(member, "x", p.x);
		setNumber(member, "y", p.y);
		setNumber(member, "z", p.z);
		setNumber(member, "width", elm.getPostWidth(pin));
		members.push(member);
	    }
	}
	return okArray(netArray);
    }

    JavaScriptObject addComponent(String type, int x, int y, int x2, int y2, JavaScriptObject props) {
	if (type == null || type.length() == 0)
	    return error(ERR_INVALID_TYPE, "Component type is required.");
	if (!isOnGrid(x) || !isOnGrid(y) || !isOnGrid(x2) || !isOnGrid(y2))
	    return error(ERR_INVALID_GRID, "Coordinates must align to the simulator grid.");

	CircuitElm elm = CirSim.constructElement(type, x, y);
	if (elm == null)
	    return error(ERR_INVALID_TYPE, "Unknown component type: " + type);
	elm.setPosition(x, y, x2, y2);
	if (elm.creationFailed())
	    return error(ERR_INVALID_ARGUMENT, "Component endpoints must not be identical.");
	if (hasExactOverlap(elm))
	    return error(ERR_OVERLAP, "A component already exists at those endpoints.");

	app.elmList.addElement(elm);
	if (props != null) {
	    JavaScriptObject propResult = applyProperties(elm, props);
	    if (!isOk(propResult)) {
		app.elmList.removeElement(elm);
		return propResult;
	    }
	}
	app.needAnalyze();
	return ok(componentDetails(elm));
    }

    JavaScriptObject addWire(int x1, int y1, int x2, int y2, JavaScriptObject opts) {
	if (!isOnGrid(x1) || !isOnGrid(y1) || !isOnGrid(x2) || !isOnGrid(y2))
	    return error(ERR_INVALID_GRID, "Coordinates must align to the simulator grid.");
	WireElm wire = new WireElm(x1, y1);
	wire.drag(x2, y2);
	if (wire.creationFailed())
	    return error(ERR_INVALID_ARGUMENT, "Wire endpoints must not be identical.");
	if (hasExactOverlap(wire))
	    return error(ERR_OVERLAP, "A wire already exists at those endpoints.");
	app.elmList.addElement(wire);
	app.needAnalyze();
	return ok(componentDetails(wire));
    }

    JavaScriptObject setPropertyNumber(String id, String key, double value) {
	return setProperty(id, key, "number", value, null, false);
    }

    JavaScriptObject setPropertyString(String id, String key, String value) {
	return setProperty(id, key, "string", 0, value, false);
    }

    JavaScriptObject setPropertyBoolean(String id, String key, boolean value) {
	return setProperty(id, key, "boolean", 0, null, value);
    }

    JavaScriptObject unsupportedPropertyType(String id, String key, String type) {
	return error(ERR_UNSUPPORTED, "Unsupported property value type: " + type);
    }

    JavaScriptObject deleteComponent(String id) {
	syncIds();
	CircuitElm elm = idToElm.get(id);
	if (elm == null)
	    return error(ERR_NOT_FOUND, "Unknown component id: " + id);
	elm.delete();
	app.elmList.removeElement(elm);
	elmToId.remove(elm);
	idToElm.remove(id);
	app.scopeManager.deleteUnusedScopeElms();
	app.needAnalyze();
	return okNoData();
    }

    JavaScriptObject connect(String idA, int pinA, String idB, int pinB) {
	syncIds();
	CircuitElm a = idToElm.get(idA);
	CircuitElm b = idToElm.get(idB);
	if (a == null || b == null)
	    return error(ERR_NOT_FOUND, "Unknown component id.");
	if (pinA < 0 || pinA >= a.getPostCount() || pinB < 0 || pinB >= b.getPostCount())
	    return error(ERR_INVALID_PIN, "Pin index out of range.");
	if (a.getPostWidth(pinA) != b.getPostWidth(pinB))
	    return error(ERR_BUS_MISMATCH, "Pin bus widths do not match.");
	Point p1 = a.getPost(pinA);
	Point p2 = b.getPost(pinB);
	if (p1.x == p2.x && p1.y == p2.y && p1.z == p2.z)
	    return okNoData();
	WireElm wire = new WireElm(p1.x, p1.y);
	wire.drag(p2.x, p2.y);
	if (wire.creationFailed())
	    return error(ERR_INVALID_ARGUMENT, "Could not create connection wire.");
	if (hasExactOverlap(wire))
	    return error(ERR_OVERLAP, "A wire already exists at those endpoints.");
	app.elmList.addElement(wire);
	app.needAnalyze();
	return ok(componentDetails(wire));
    }

    JavaScriptObject analyze() {
	JavaScriptObject simError = ensureStamped();
	if (simError != null)
	    return simError;
	return okNoData();
    }

    JavaScriptObject runSteps(int steps) {
	if (steps < 0)
	    return error(ERR_INVALID_ARGUMENT, "steps must be >= 0.");
	JavaScriptObject simError = ensureStamped();
	if (simError != null)
	    return simError;
	int target = app.sim.timeStepCount + steps;
	int guard = Math.max(steps * 20, 100);
	boolean oldRunning = app.simRunning;
	app.simRunning = true;
	try {
	    while (app.sim.timeStepCount < target && guard-- > 0 && app.stopMessage == null) {
		app.sim.lastIterTime = System.currentTimeMillis() - 1000;
		app.sim.runCircuit(true);
	    }
	} finally {
	    app.simRunning = oldRunning;
	}
	if (app.stopMessage != null)
	    return error(ERR_SIMULATION, app.stopMessage);
	return simulationState();
    }

    JavaScriptObject runDuration(double duration) {
	if (duration < 0)
	    return error(ERR_INVALID_ARGUMENT, "duration must be >= 0.");
	JavaScriptObject simError = ensureStamped();
	if (simError != null)
	    return simError;
	double target = app.sim.t + duration;
	int guard = 100000;
	boolean oldRunning = app.simRunning;
	app.simRunning = true;
	try {
	    while (app.sim.t < target && guard-- > 0 && app.stopMessage == null) {
		app.sim.lastIterTime = System.currentTimeMillis() - 1000;
		app.sim.runCircuit(true);
	    }
	} finally {
	    app.simRunning = oldRunning;
	}
	if (app.stopMessage != null)
	    return error(ERR_SIMULATION, app.stopMessage);
	return simulationState();
    }

    JavaScriptObject measureAll() {
	syncIds();
	JavaScriptObject simError = ensureStamped();
	if (simError != null)
	    return simError;
	JsArray<JavaScriptObject> arr = createObjectArray();
	for (int i = 0; i < app.elmList.size(); i++) {
	    CircuitElm elm = app.getElm(i);
	    JavaScriptObject measurement = createObject();
	    setString(measurement, "componentId", getOrCreateId(elm));
	    setString(measurement, "type", elm.getClassName());
	    setNumber(measurement, "current", elm.getCurrent());
	    setNumber(measurement, "voltageDiff", elm.getVoltageDiff());
	    JsArrayNumber volts = createNumberArray();
	    for (int pin = 0; pin < elm.getPostCount(); pin++)
		volts.push(pin < elm.volts.length ? elm.volts[pin] : 0);
	    setObject(measurement, "voltages", volts);
	    arr.push(measurement);
	}
	return okArray(arr);
    }

    private JavaScriptObject simulationState() {
	JavaScriptObject state = createObject();
	setNumber(state, "time", app.sim.t);
	setNumber(state, "timeStep", app.sim.timeStep);
	setNumber(state, "timeStepCount", app.sim.timeStepCount);
	return ok(state);
    }

    private JavaScriptObject setProperty(String id, String key, String kind, double numberValue, String stringValue, boolean boolValue) {
	syncIds();
	CircuitElm elm = idToElm.get(id);
	if (elm == null)
	    return error(ERR_NOT_FOUND, "Unknown component id: " + id);
	if (key == null || key.length() == 0)
	    return error(ERR_INVALID_PROPERTY, "Property key is required.");

	String normalizedTarget = normalizeKey(key);
	for (int i = 0; ; i++) {
	    EditInfo ei = elm.getEditInfo(i);
	    if (ei == null)
		break;
	    String candidate = normalizeEditKey(ei);
	    if (candidate == null || !candidate.equals(normalizedTarget))
		continue;
	    JavaScriptObject result = applyEditInfoValue(elm, i, ei, kind, numberValue, stringValue, boolValue);
	    if (!isOk(result))
		return result;
	    app.needAnalyze();
	    return ok(componentDetails(elm));
	}
	return error(ERR_INVALID_PROPERTY, "Unknown property key: " + key);
    }

    private JavaScriptObject applyProperties(CircuitElm elm, JavaScriptObject props) {
	JsArrayString keys = getKeys(props);
	for (int i = 0; i < keys.length(); i++) {
	    String key = keys.get(i);
	    String valueType = getType(props, key);
	    JavaScriptObject result;
	    if ("boolean".equals(valueType)) {
		result = setProperty(getOrCreateId(elm), key, "boolean", 0, null, getBoolean(props, key));
	    } else if ("number".equals(valueType)) {
		result = setProperty(getOrCreateId(elm), key, "number", getNumber(props, key), null, false);
	    } else if ("string".equals(valueType)) {
		result = setProperty(getOrCreateId(elm), key, "string", 0, getString(props, key), false);
	    } else {
		return error(ERR_UNSUPPORTED, "Unsupported property value type for key " + key + ": " + valueType);
	    }
	    if (!isOk(result))
		return result;
	}
	return okNoData();
    }

    private JavaScriptObject applyEditInfoValue(CircuitElm elm, int index, EditInfo ei, String kind, double numberValue, String stringValue, boolean boolValue) {
	if (ei.checkbox != null) {
	    if (!"boolean".equals(kind))
		return error(ERR_INVALID_PROPERTY_TYPE, "Property requires a boolean value.");
	    ei.checkbox.setState(boolValue);
	} else if (ei.choice != null) {
	    if ("number".equals(kind)) {
		int choiceIndex = (int) numberValue;
		if (choiceIndex < 0 || choiceIndex >= ei.choice.getItemCount())
		    return error(ERR_INVALID_ARGUMENT, "Choice index out of range.");
		ei.choice.setSelectedIndex(choiceIndex);
	    } else if ("string".equals(kind)) {
		int found = -1;
		for (int i = 0; i < ei.choice.getItemCount(); i++) {
		    if (normalizeKey(ei.choice.getItemText(i)).equals(normalizeKey(stringValue))) {
			found = i;
			break;
		    }
		}
		if (found < 0)
		    return error(ERR_INVALID_ARGUMENT, "Choice value not found: " + stringValue);
		ei.choice.setSelectedIndex(found);
	    } else {
		return error(ERR_INVALID_PROPERTY_TYPE, "Property requires a number or string value.");
	    }
	} else if (ei.text != null) {
	    if ("string".equals(kind))
		ei.text = stringValue;
	    else if ("number".equals(kind))
		ei.text = Double.toString(numberValue);
	    else
		return error(ERR_INVALID_PROPERTY_TYPE, "Property requires a string or number value.");
	} else {
	    if (!"number".equals(kind))
		return error(ERR_INVALID_PROPERTY_TYPE, "Property requires a number value.");
	    ei.value = numberValue;
	}
	elm.setEditValue(index, ei);
	if (ei.error != null)
	    return error(ERR_INVALID_ARGUMENT, ei.error);
	return okNoData();
    }

    private String normalizeEditKey(EditInfo ei) {
	if (ei.name != null && ei.name.length() > 0)
	    return normalizeKey(ei.name);
	if (ei.checkbox != null)
	    return normalizeKey(ei.checkbox.getText());
	return null;
    }

    private String normalizeKey(String key) {
	if (key == null)
	    return null;
	return key.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private boolean hasExactOverlap(CircuitElm candidate) {
	for (int i = 0; i < app.elmList.size(); i++) {
	    CircuitElm ce = app.getElm(i);
	    if (ce.x == candidate.x && ce.y == candidate.y && ce.x2 == candidate.x2 && ce.y2 == candidate.y2)
		return true;
	    if (ce.x == candidate.x2 && ce.y == candidate.y2 && ce.x2 == candidate.x && ce.y2 == candidate.y)
		return true;
	}
	return false;
    }

    private boolean isOnGrid(int value) {
	return app.snapGrid(value) == value;
    }

    private JavaScriptObject ensureStamped() {
	app.sim.analyzeCircuit();
	if (app.stopMessage != null)
	    return error(ERR_SIMULATION, app.stopMessage);
	if (app.sim.needsStamp)
	    app.sim.preStampAndStampCircuit();
	if (app.stopMessage != null)
	    return error(ERR_SIMULATION, app.stopMessage);
	return null;
    }

    private JavaScriptObject componentSummary(CircuitElm elm) {
	JavaScriptObject obj = createObject();
	setString(obj, "id", getOrCreateId(elm));
	setString(obj, "type", elm.getClassName());
	setNumber(obj, "x", elm.x);
	setNumber(obj, "y", elm.y);
	setNumber(obj, "x2", elm.x2);
	setNumber(obj, "y2", elm.y2);
	setNumber(obj, "postCount", elm.getPostCount());
	return obj;
    }

    private JavaScriptObject componentDetails(CircuitElm elm) {
	JavaScriptObject obj = componentSummary(elm);
	setNumber(obj, "current", elm.getCurrent());
	setNumber(obj, "voltageDiff", elm.getVoltageDiff());
	JsArray<JavaScriptObject> props = createObjectArray();
	for (int i = 0; ; i++) {
	    EditInfo ei = elm.getEditInfo(i);
	    if (ei == null)
		break;
	    JavaScriptObject prop = createObject();
	    String key = normalizeEditKey(ei);
	    if (key == null)
		key = "property" + i;
	    setString(prop, "key", key);
	    if (ei.checkbox != null) {
		setString(prop, "type", "boolean");
		setBoolean(prop, "value", ei.checkbox.getState());
	    } else if (ei.choice != null) {
		setString(prop, "type", "choice");
		setNumber(prop, "value", ei.choice.getSelectedIndex());
		JsArrayString options = createStringArray();
		for (int j = 0; j < ei.choice.getItemCount(); j++)
		    options.push(ei.choice.getItemText(j));
		setObject(prop, "options", options);
	    } else if (ei.text != null) {
		setString(prop, "type", "string");
		setString(prop, "value", ei.text);
	    } else {
		setString(prop, "type", "number");
		setNumber(prop, "value", ei.value);
	    }
	    props.push(prop);
	}
	setObject(obj, "properties", props);
	return obj;
    }

    private String getOrCreateId(CircuitElm elm) {
	String id = elmToId.get(elm);
	if (id != null)
	    return id;
	id = "e" + nextId++;
	elmToId.put(elm, id);
	idToElm.put(id, elm);
	return id;
    }

    private void syncIds() {
	Iterator<Map.Entry<CircuitElm, String>> it = elmToId.entrySet().iterator();
	while (it.hasNext()) {
	    Map.Entry<CircuitElm, String> entry = it.next();
	    if (!app.elmList.contains(entry.getKey())) {
		idToElm.remove(entry.getValue());
		it.remove();
	    }
	}
	for (int i = 0; i < app.elmList.size(); i++)
	    getOrCreateId(app.getElm(i));
    }

    private void rebuildIds() {
	elmToId.clear();
	idToElm.clear();
	for (int i = 0; i < app.elmList.size(); i++)
	    getOrCreateId(app.getElm(i));
    }

    private JavaScriptObject okNoData() {
	JavaScriptObject obj = createObject();
	setBoolean(obj, "ok", true);
	return obj;
    }

    private JavaScriptObject okArray(JsArray<JavaScriptObject> arr) {
	JavaScriptObject obj = okNoData();
	setObject(obj, "data", arr);
	return obj;
    }

    private JavaScriptObject ok(JavaScriptObject data) {
	JavaScriptObject obj = okNoData();
	setObject(obj, "data", data);
	return obj;
    }

    private JavaScriptObject error(String code, String message) {
	JavaScriptObject obj = createObject();
	setBoolean(obj, "ok", false);
	setString(obj, "code", code);
	setString(obj, "message", message);
	return obj;
    }

    private native boolean isOk(JavaScriptObject obj) /*-{
	return !!obj && !!obj.ok;
    }-*/;

    private native JavaScriptObject createObject() /*-{ return {}; }-*/;
    private native JsArray<JavaScriptObject> createObjectArray() /*-{ return []; }-*/;
    private native JsArrayNumber createNumberArray() /*-{ return []; }-*/;
    private native JsArrayString createStringArray() /*-{ return []; }-*/;
    private native JavaScriptObject getObject(JavaScriptObject obj, String key) /*-{ return obj[key]; }-*/;
    private native void setString(JavaScriptObject obj, String key, String value) /*-{ obj[key] = value; }-*/;
    private native void setNumber(JavaScriptObject obj, String key, double value) /*-{ obj[key] = value; }-*/;
    private native void setBoolean(JavaScriptObject obj, String key, boolean value) /*-{ obj[key] = value; }-*/;
    private native void setObject(JavaScriptObject obj, String key, JavaScriptObject value) /*-{ obj[key] = value; }-*/;
    private native JsArrayString getKeys(JavaScriptObject obj) /*-{
	var out = [];
	if (!obj)
	    return out;
	for (var k in obj)
	    if (Object.prototype.hasOwnProperty.call(obj, k))
		out.push(k);
	return out;
    }-*/;
    private native String getType(JavaScriptObject obj, String key) /*-{ return typeof obj[key]; }-*/;
    private native double getNumber(JavaScriptObject obj, String key) /*-{ return +obj[key]; }-*/;
    private native String getString(JavaScriptObject obj, String key) /*-{ return String(obj[key]); }-*/;
    private native boolean getBoolean(JavaScriptObject obj, String key) /*-{ return !!obj[key]; }-*/;
}
