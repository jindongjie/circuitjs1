package com.lushprojects.circuitjs1.client;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;

public class JSInterface {

    CirSim app;
    AICircuitService aiService;

    JSInterface(CirSim app) {
	this.app = app;
	this.aiService = new AICircuitService(app);
    }

    void setExtVoltage(String name, double v) {
	int i;
	for (i = 0; i != app.elmList.size(); i++) {
	    CircuitElm ce = app.getElm(i);
	    if (ce instanceof ExtVoltageElm) {
		ExtVoltageElm eve = (ExtVoltageElm) ce;
		if (eve.getName().equals(name))
		    eve.setVoltage(v);
	    }
	}
    }

    native JsArray<JavaScriptObject> getJSArray() /*-{ return []; }-*/;

    JsArray<JavaScriptObject> getJSElements() {
	int i;
	JsArray<JavaScriptObject> arr = getJSArray();
	for (i = 0; i != app.elmList.size(); i++) {
	    CircuitElm ce = app.getElm(i);
	    ce.addJSMethods();
	    arr.push(ce.getJavaScriptObject());
	}
	return arr;
    }

    double getLabeledNodeVoltage(String name) { return app.sim.getLabeledNodeVoltage(name); }

    // Delegate methods for JSNI access
    void setSimRunning(boolean run) { app.setSimRunning(run); }
    boolean simIsRunning() { return app.simIsRunning(); }
    void doExportAsSVGFromAPI() { app.imageExporter.doExportAsSVGFromAPI(); }
    String dumpCircuit() { return app.dumpCircuit(); }
    void importCircuitFromText(String t, boolean s) { app.importCircuitFromText(t, s); }
    double getTime() { return app.sim.t; }
    double getTimeStep() { return app.sim.timeStep; }
    void setTimeStep(double ts) { app.sim.timeStep = ts; }
    double getMaxTimeStep() { return app.sim.maxTimeStep; }
    void setMaxTimeStep(double ts) { app.sim.maxTimeStep = app.sim.timeStep = ts; }
    JavaScriptObject aiBeginTransaction() { return aiService.beginTransaction(); }
    JavaScriptObject aiCommitTransaction() { return aiService.commitTransaction(); }
    JavaScriptObject aiRollbackTransaction() { return aiService.rollbackTransaction(); }
    JavaScriptObject aiListComponents() { return aiService.listComponents(); }
    JavaScriptObject aiListNets() { return aiService.listNets(); }
    JavaScriptObject aiGetComponent(String id) { return aiService.getComponent(id); }
    JavaScriptObject aiAddComponent(String type, int x, int y, int x2, int y2, JavaScriptObject props) { return aiService.addComponent(type, x, y, x2, y2, props); }
    JavaScriptObject aiAddWire(int x1, int y1, int x2, int y2, JavaScriptObject opts) { return aiService.addWire(x1, y1, x2, y2, opts); }
    JavaScriptObject aiSetPropertyNumber(String id, String key, double value) { return aiService.setPropertyNumber(id, key, value); }
    JavaScriptObject aiSetPropertyString(String id, String key, String value) { return aiService.setPropertyString(id, key, value); }
    JavaScriptObject aiSetPropertyBoolean(String id, String key, boolean value) { return aiService.setPropertyBoolean(id, key, value); }
    JavaScriptObject aiUnsupportedPropertyType(String id, String key, String type) { return aiService.unsupportedPropertyType(id, key, type); }
    JavaScriptObject aiDelete(String id) { return aiService.deleteComponent(id); }
    JavaScriptObject aiConnect(String idA, int pinA, String idB, int pinB) { return aiService.connect(idA, pinA, idB, pinB); }
    JavaScriptObject aiAnalyze() { return aiService.analyze(); }
    JavaScriptObject aiRunSteps(int steps) { return aiService.runSteps(steps); }
    JavaScriptObject aiRunDuration(double duration) { return aiService.runDuration(duration); }
    JavaScriptObject aiMeasureAll() { return aiService.measureAll(); }

    native void setupJSInterface() /*-{
	var that = this;
	$wnd.CircuitJS1 = {
	    setSimRunning: $entry(function(run) { that.@com.lushprojects.circuitjs1.client.JSInterface::setSimRunning(Z)(run); } ),
	    getTime: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::getTime()(); } ),
	    getTimeStep: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::getTimeStep()(); } ),
	    setTimeStep: $entry(function(ts) { that.@com.lushprojects.circuitjs1.client.JSInterface::setTimeStep(D)(ts); } ), // don't use this, see #843
	    getMaxTimeStep: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::getMaxTimeStep()(); } ),
	    setMaxTimeStep: $entry(function(ts) { that.@com.lushprojects.circuitjs1.client.JSInterface::setMaxTimeStep(D)(ts); } ),
	    isRunning: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::simIsRunning()(); } ),
	    getNodeVoltage: $entry(function(n) { return that.@com.lushprojects.circuitjs1.client.JSInterface::getLabeledNodeVoltage(Ljava/lang/String;)(n); } ),
	    setExtVoltage: $entry(function(n, v) { that.@com.lushprojects.circuitjs1.client.JSInterface::setExtVoltage(Ljava/lang/String;D)(n, v); } ),
	    getElements: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::getJSElements()(); } ),
	    getCircuitAsSVG: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::doExportAsSVGFromAPI()(); } ),
	    exportCircuit: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::dumpCircuit()(); } ),
	    importCircuit: $entry(function(circuit, subcircuitsOnly) { return that.@com.lushprojects.circuitjs1.client.JSInterface::importCircuitFromText(Ljava/lang/String;Z)(circuit, subcircuitsOnly); })
	};
	$wnd.CircuitJS1.ai = {
	    beginTransaction: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::aiBeginTransaction()(); }),
	    commitTransaction: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::aiCommitTransaction()(); }),
	    rollbackTransaction: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::aiRollbackTransaction()(); }),
	    listComponents: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::aiListComponents()(); }),
	    listNets: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::aiListNets()(); }),
	    getComponent: $entry(function(id) { return that.@com.lushprojects.circuitjs1.client.JSInterface::aiGetComponent(Ljava/lang/String;)(id); }),
	    addComponent: $entry(function(type, x, y, x2, y2, props) {
		return that.@com.lushprojects.circuitjs1.client.JSInterface::aiAddComponent(Ljava/lang/String;IIIILcom/google/gwt/core/client/JavaScriptObject;)(type, x|0, y|0, x2|0, y2|0, props || null);
	    }),
	    addWire: $entry(function(x1, y1, x2, y2, opts) {
		return that.@com.lushprojects.circuitjs1.client.JSInterface::aiAddWire(IIIILcom/google/gwt/core/client/JavaScriptObject;)(x1|0, y1|0, x2|0, y2|0, opts || null);
	    }),
	    setProperty: $entry(function(id, key, value) {
		var t = typeof value;
		if (t === "number")
		    return that.@com.lushprojects.circuitjs1.client.JSInterface::aiSetPropertyNumber(Ljava/lang/String;Ljava/lang/String;D)(id, key, value);
		if (t === "boolean")
		    return that.@com.lushprojects.circuitjs1.client.JSInterface::aiSetPropertyBoolean(Ljava/lang/String;Ljava/lang/String;Z)(id, key, value);
		if (t === "string")
		    return that.@com.lushprojects.circuitjs1.client.JSInterface::aiSetPropertyString(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)(id, key, value);
		return that.@com.lushprojects.circuitjs1.client.JSInterface::aiUnsupportedPropertyType(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)(id, key, t);
	    }),
	    delete: $entry(function(id) { return that.@com.lushprojects.circuitjs1.client.JSInterface::aiDelete(Ljava/lang/String;)(id); }),
	    connect: $entry(function(idA, pinA, idB, pinB) {
		return that.@com.lushprojects.circuitjs1.client.JSInterface::aiConnect(Ljava/lang/String;ILjava/lang/String;I)(idA, pinA|0, idB, pinB|0);
	    }),
	    analyze: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::aiAnalyze()(); }),
	    runSteps: $entry(function(steps) { return that.@com.lushprojects.circuitjs1.client.JSInterface::aiRunSteps(I)(steps|0); }),
	    runDuration: $entry(function(duration) { return that.@com.lushprojects.circuitjs1.client.JSInterface::aiRunDuration(D)(duration); }),
	    measureAll: $entry(function() { return that.@com.lushprojects.circuitjs1.client.JSInterface::aiMeasureAll()(); })
	};
	var hook = $wnd.oncircuitjsloaded;
	if (hook)
	    hook($wnd.CircuitJS1);
    }-*/;

    native void callUpdateHook() /*-{
	var hook = $wnd.CircuitJS1.onupdate;
	if (hook)
	    hook($wnd.CircuitJS1);
    }-*/;

    native void callAnalyzeHook() /*-{
	var hook = $wnd.CircuitJS1.onanalyze;
	if (hook)
	    hook($wnd.CircuitJS1);
    }-*/;

    native void callTimeStepHook() /*-{
	var hook = $wnd.CircuitJS1.ontimestep;
	if (hook)
	    hook($wnd.CircuitJS1);
    }-*/;

    native void callSVGRenderedHook(String svgData) /*-{
	var hook = $wnd.CircuitJS1.onsvgrendered;
	if (hook)
	    hook($wnd.CircuitJS1, svgData);
    }-*/;
}
