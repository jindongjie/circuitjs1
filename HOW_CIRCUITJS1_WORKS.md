# How CircuitJS1 Works

This document explains:

1. how the project is organized, and  
2. how the simulator computes circuit behavior over time.

It complements `INTERNALS.md` with a code-oriented walkthrough.

## 1) High-level architecture

CircuitJS1 is a browser application compiled from Java to JavaScript with GWT.

- Entry point: `src/com/lushprojects/circuitjs1/client/circuitjs1.java`
  - `onModuleLoad()` loads localization and creates the app.
- Main app object: `src/com/lushprojects/circuitjs1/client/CirSim.java`
  - Owns UI, commands, undo/redo, loading/saving, and simulation lifecycle.
- Simulation engine: `src/com/lushprojects/circuitjs1/client/SimulationManager.java`
  - Builds and solves the circuit equations.
- Elements: many `*Elm.java` classes in the same package
  - Each component (resistor, capacitor, MOSFET, op-amp, etc.) implements stamping and stepping logic.

## 2) Runtime flow (from page load to simulation loop)

### Startup

1. `circuitjs1.onModuleLoad()` runs.
2. Locale is loaded (`loadLocale()`), then `loadSimulator()` creates `CirSim`.
3. `CirSim.init()` initializes UI, command handlers, models, and loads the initial circuit.
4. `CirSim.updateCircuit()` is called by a timer to refresh and advance simulation.

### Main loop concept

On each update:

- If the circuit changed, simulation is re-analyzed.
- The simulator performs one or more numerical solve steps.
- Node voltages and element currents are pushed back to elements.
- UI draws voltages/current animations/scopes using latest values.

## 3) How circuit equations are built

CircuitJS1 uses **Modified Nodal Analysis (MNA)**.

- Unknowns are:
  - node voltages (except ground),
  - currents through independent voltage sources.
- The engine builds a linear system:
  - `A * x = b`
  - `x` contains node voltages and source currents.

Each element contributes (“stamps”) terms into matrix/vector:

- resistor → conductance terms in `A`
- current source → terms in `b`
- voltage source → extra row/column (constraint + source current unknown)
- controlled/nonlinear devices → linearized contributions for current iteration point

Relevant APIs are in `SimulationManager` (`stampResistor`, `stampCurrentSource`, `stampVoltageSource`, `stampNonLinear`, etc.), while per-element behavior lives in each `*Elm` class.

## 4) Node and topology processing

Before solving, the engine prepares topology:

1. **Wire closure / node merging** (`calculateWireClosure`)
   - Points connected by ideal wires are merged into one simulation node.
   - Reduces matrix size and improves speed.
2. **Node allocation** (`makeNodeList`)
   - Assigns row indices to physical and internal nodes.
   - Allocates voltage source indices.
3. **Matrix setup** (`stampCircuit`)
   - Creates per-matrix structures and stamps all linear/static contributions.

This step runs when the circuit is edited or when a re-stamp is needed.

## 5) Solving at each timestep

`SimulationManager.runCircuit()` executes the transient solve loop.

For each timestep:

1. Save state needed for rollback (if timestep must be reduced).
2. Repeat sub-iterations until converged (important for nonlinear devices):
   - reset right side / dynamic entries,
   - call `startIteration()` and `doStep()` on elements,
   - solve matrix,
   - update node voltages/currents,
   - check convergence.
3. If convergence fails:
   - reduce timestep,
   - restore previous state,
   - re-stamp and retry.
4. If convergence is good:
   - possibly increase timestep (adaptive stepping).

## 6) Why nonlinear devices need iteration

Devices like diodes/transistors are nonlinear (`I-V` curve is not a straight line).

So at each sub-iteration the simulator:

- linearizes the device around current guess (small-signal approximation),
- solves the resulting linear system,
- compares with previous guess,
- repeats until the change is below tolerance.

This is the core reason simulation is “solve + iterate”, not a single solve.

## 7) Capacitors, inductors, and time-domain simulation

Reactive components are converted into timestep-dependent companion models:

- capacitor / inductor behavior is represented with equivalent resistor + source terms for the selected integration method,
- values depend on previous timestep state,
- after solve, internal state is updated for next timestep.

CircuitJS1 supports stable transient simulation with selectable integration behavior (see simulator options and `INTERNALS.md` discussion).

## 8) Dense vs sparse solving

`SimulationManager` can choose dense or sparse methods depending on matrix size/settings.

- Sparse solving uses classes under
  `src/com/lushprojects/circuitjs1/client/matrix/`
  (e.g., `SparseLU`, `DMatrixSparseCSC`).
- This is critical for large circuits, where sparse methods are much faster and lower memory.

## 9) How visual animation is tied to simulation

After each successful solve:

- element terminal voltages are updated (`setNodeVoltages` path),
- element currents are updated (including solved voltage-source currents),
- wire current info is computed for animated moving dots and overlays,
- scopes read the updated values to display waveforms.

So the UI animation is not separate physics; it is rendered from the computed electrical state.

## 10) Practical “mental model”

When thinking about CircuitJS1 internals, use this sequence:

1. **Load/parse circuit**
2. **Build node map**
3. **Stamp matrix/vector**
4. **Solve (and iterate if nonlinear)**
5. **Advance time**
6. **Push values to elements/UI**
7. **Repeat**

That loop is the heart of how CircuitJS1 turns a schematic into live simulation.
