# CircuitJS1 AI Interface

CircuitJS1 now exposes a structured AI API at `window.CircuitJS1.ai`.
The existing `window.CircuitJS1` methods remain unchanged for compatibility.

## Response schema

All AI methods return a JSON object:

- Success:
  - `{"ok": true}`
  - `{"ok": true, "data": ...}`
- Error:
  - `{"ok": false, "code": "ERROR_CODE", "message": "Human-readable message"}`

Common error codes:

- `TRANSACTION_ACTIVE`
- `TRANSACTION_MISSING`
- `NOT_FOUND`
- `INVALID_GRID`
- `INVALID_COMPONENT_TYPE`
- `OVERLAP`
- `INVALID_PIN`
- `BUS_WIDTH_MISMATCH`
- `INVALID_PROPERTY`
- `INVALID_PROPERTY_TYPE`
- `INVALID_ARGUMENT`
- `SIMULATION_ERROR`
- `UNSUPPORTED`

## API surface

### Model API

- `listComponents()`
- `listNets()`
- `getComponent(id)`

Component objects include stable session IDs (`e1`, `e2`, ...) and geometry.
Detailed component payloads include structured editable properties.

### Edit API

- `addComponent(type, x, y, x2, y2, props?)`
- `addWire(x1, y1, x2, y2, opts?)`
- `setProperty(id, key, value)` where `value` is number/string/boolean
- `delete(id)`
- `connect(componentAId, pinA, componentBId, pinB)`

Validation includes:

- grid alignment checks
- unknown component type detection
- exact-endpoint overlap checks
- invalid pin index checks
- bus width mismatch checks in `connect`

### Simulation API

- `analyze()`
- `runSteps(steps)`
- `runDuration(seconds)`
- `measureAll()`

`measureAll()` returns structured measurements:

- `componentId`
- `type`
- `current`
- `voltageDiff`
- `voltages[]`

### Transactions

- `beginTransaction()`
- `commitTransaction()`
- `rollbackTransaction()`

Rollback restores the full circuit snapshot captured at `beginTransaction()`.

## Example

```js
const ai = window.CircuitJS1.ai;

ai.beginTransaction();
const r1 = ai.addComponent("ResistorElm", 96, 96, 160, 96, { resistance: 1000 });
const v1 = ai.addComponent("DCVoltageElm", 96, 160, 96, 96, { voltage: 5 });
if (!r1.ok || !v1.ok) {
  ai.rollbackTransaction();
} else {
  ai.connect(v1.data.id, 0, r1.data.id, 0);
  ai.analyze();
  const m = ai.measureAll();
  ai.commitTransaction();
}
```
