/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.feature.vt.gui.provider.functionassociation;

import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;

import org.junit.*;
import generic.test.TestUtils;
import docking.widgets.table.threaded.ThreadedTableModel;
import ghidra.feature.vt.api.correlator.program.ExactMatchInstructionsProgramCorrelatorFactory;
import ghidra.feature.vt.api.main.*;
import ghidra.feature.vt.api.util.VTOptions;
import ghidra.feature.vt.gui.VTTestEnv;
import ghidra.feature.vt.gui.plugin.VTPlugin;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.database.ProgramBuilder;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.SourceType;
import ghidra.test.AbstractGhidraHeadedIntegrationTest;
import ghidra.util.task.TaskMonitor;
import ghidra.util.table.GhidraTable;
import ghidra.util.table.GhidraTableFilterPanel;

/** Fork-only real name-filter observations through the Version Tracking provider. */
public class VTFunctionRenameCharacterizationTest extends AbstractGhidraHeadedIntegrationTest {
    private VTTestEnv env;
    private PluginTool tool;
    private VTFunctionAssociationProvider provider;
    private Program sourceProgram;
    private Program destinationProgram;

    @Before
    public void setUp() throws Exception {
        setErrorGUIEnabled(false);
        env = new VTTestEnv();
        tool = env.showTool();
        tool.setSize(800, 800);
        sourceProgram = createProgram("Source");
        destinationProgram = createProgram("Destination");
        env.createSession(sourceProgram, destinationProgram);
        env.correlate(new ExactMatchInstructionsProgramCorrelatorFactory(), null, TaskMonitor.DUMMY);
        VTPlugin plugin = env.getVersionTrackingPlugin();
        provider = (VTFunctionAssociationProvider) TestUtils.getInstanceField("functionAssociationProvider", plugin);
        runSwing(() -> tool.showComponentProvider(provider, true));
        waitForSwing();
        waitForTasks();
        waitForSwing();
        assertTrue(provider.isVisible());
    }

    private Program createProgram(String name) throws Exception {
        ProgramBuilder builder = new ProgramBuilder(name, ProgramBuilder._X86, env);
        try {
            builder.createMemory(".text", "0x1000", 0x100);
            builder.setBytes("0x1000", "55 89 e5 90 5d c3", true);
            builder.setBytes("0x1040", "55 89 e5 40 5d c3", true);
            assertNotNull(builder.createFunction("0x1000"));
            assertNotNull(builder.createFunction("0x1040"));
            Program program = builder.getProgram();
            assertEquals(2, program.getFunctionManager().getFunctionCount());
            assertNotNull(program.getListing().getInstructionAt(builder.addr("0x1000")));
            assertNotNull(program.getListing().getInstructionAt(builder.addr("0x1040")));
            return program;
        }
        catch (Exception | AssertionError failure) {
            if (builder.getProgram().isUsedBy(env)) { builder.getProgram().release(env); }
            throw failure;
        }
        finally { builder.dispose(); }
    }

    @After
    public void tearDown() {
        if (tool != null) { runSwing(() -> tool.close()); }
        try { if (env != null) { env.dispose(); } }
        finally {
            if (sourceProgram != null && sourceProgram.isUsedBy(env)) { sourceProgram.release(env); }
            if (destinationProgram != null && destinationProgram.isUsedBy(env)) { destinationProgram.release(env); }
        }
    }

    @Test
    public void testSourceRenameUpdatesActiveFilter() throws Exception { check(true, true); }
    @Test
    public void testDestinationRenameUpdatesActiveFilter() throws Exception { check(false, true); }
    @Test
    public void testSourceUnchangedFilterControl() throws Exception { check(true, false); }
    @Test
    public void testDestinationUnchangedFilterControl() throws Exception { check(false, false); }

    @Test
    public void testSourceRenameEntersActiveFilter() throws Exception { checkEntering(true); }
    @Test
    public void testDestinationRenameEntersActiveFilter() throws Exception { checkEntering(false); }
    @Test
    public void testSourceUndoRedoPreservesFilter() throws Exception { checkUndoRedo(true); }
    @Test
    public void testDestinationUndoRedoPreservesFilter() throws Exception { checkUndoRedo(false); }
    @Test
    public void testSourceUnrelatedLabelRenameControl() throws Exception { checkLabel(true); }
    @Test
    public void testDestinationUnrelatedLabelRenameControl() throws Exception { checkLabel(false); }

    @Test
    public void testSourceAcceptedAssociationRenameControl() throws Exception { checkAssociation(true); }
    @Test
    public void testDestinationAcceptedAssociationRenameControl() throws Exception { checkAssociation(false); }

    private void checkAssociation(boolean source) throws Exception {
        ExactMatchInstructionsProgramCorrelatorFactory factory =
            new ExactMatchInstructionsProgramCorrelatorFactory();
        VTOptions options = factory.createDefaultOptions();
        options.setInt(ExactMatchInstructionsProgramCorrelatorFactory.FUNCTION_MINIMUM_SIZE, 1);
        env.correlate(factory, options, TaskMonitor.DUMMY);
        VTSession session = env.getSession();
        VTMatch target = null;
        for (VTMatchSet set : session.getMatchSets()) {
            for (VTMatch match : set.getMatches()) {
                if (match.getSourceAddress().equals(function(true).getEntryPoint()) &&
                    match.getDestinationAddress().equals(function(false).getEntryPoint())) {
                    target = match;
                }
            }
        }
        assertNotNull("Actual instruction correlation must produce this function match", target);
        int tx = session.startTransaction("accept real function association");
        try { target.getAssociation().setAccepted(); }
        finally { session.endTransaction(tx, true); }
        session.flushEvents();
        String side = source ? "source" : "destination";
        String prefix = "__vt_association_" + side;
        rename(function(source), prefix + "_original");
        runSwing(() -> filter(source).setFilterText(prefix));
        waitForRowCount(model(source), 1);
        assertEquals(1, (int) runSwing(() -> model(source).getRowCount()));
        rename(function(source), prefix + "_updated");
        observe(source, "accepted-association", prefix, prefix + "_updated", 1);
        FunctionAssociationInfo info = model(source).getRowObject(0).getInfo();
        assertEquals(function(source).getID(), info.getFunctionID());
        assertTrue(info.isInAssociation());
        assertTrue(info.isInAcceptedAssociation());
        assertEquals(VTAssociationStatus.ACCEPTED, target.getAssociation().getStatus());
    }

    private Program program(boolean source) {
        return source ? env.getSourceProgram() : env.getDestinationProgram();
    }

    private VTFunctionAssociationTableModel model(boolean source) {
        GhidraTable table = (GhidraTable) TestUtils.getInstanceField(
            (source ? "source" : "destination") + "FunctionsTable", provider);
        return (VTFunctionAssociationTableModel) table.getModel();
    }

    private GhidraTableFilterPanel<?> filter(boolean source) {
        return (GhidraTableFilterPanel<?>) TestUtils.getInstanceField(
            (source ? "source" : "destination") + "TableFilterPanel", provider);
    }

    private Function function(boolean source) {
        return program(source).getFunctionManager().getFunctionAt(
            program(source).getAddressFactory().getAddress("1000"));
    }

    private void observe(boolean source, String action, String filterName, String functionName,
            int expected) {
        VTFunctionAssociationTableModel model = model(source);
        waitForSwing();
        waitForTasks();
        waitForRowCount(model, expected);
        waitForTableModel(model);
        assertEquals(filterName, runSwing(() -> filter(source).getFilterText()));
        assertEquals(functionName, function(source).getName());
        int rows = runSwing(() -> model.getRowCount());
        System.out.println("VT_RENAME_ACTION side=" + (source ? "source" : "destination") +
            " action=" + action + " rows=" + rows + " function=" + functionName +
            " filter=" + filterName + " visible=" + provider.isVisible());
        assertEquals("Actual function rename must update active filter membership", expected, rows);
    }

    private void checkEntering(boolean source) throws Exception {
        String side = source ? "source" : "destination";
        String target = "__vt_enter_" + side + "_target";
        rename(function(source), "__vt_enter_" + side + "_outside");
        runSwing(() -> filter(source).setFilterText(target));
        waitForRowCount(model(source), 0);
        // Observe the deferred initial filter before sending the real rename event.
        waitForTableModel(model(source));
        assertEquals(0, (int) runSwing(() -> model(source).getRowCount()));
        rename(function(source), target);
        observe(source, "enter", target, target, 1);
    }

    private void checkUndoRedo(boolean source) throws Exception {
        String side = source ? "source" : "destination";
        String oldName = "__vt_history_" + side + "_original";
        String newName = "__vt_history_" + side + "_updated";
        rename(function(source), oldName);
        runSwing(() -> filter(source).setFilterText("__vt_history_" + side));
        waitForRowCount(model(source), 1);
        assertEquals(1, (int) runSwing(() -> model(source).getRowCount()));
        program(source).clearUndo();
        rename(function(source), newName);
        observe(source, "history-renamed", "__vt_history_" + side, newName, 1);
        assertTrue(program(source).canUndo());
        program(source).undo();
        program(source).flushEvents();
        observe(source, "undo", "__vt_history_" + side, oldName, 1);
        assertTrue(program(source).canRedo());
        program(source).redo();
        program(source).flushEvents();
        observe(source, "redo", "__vt_history_" + side, newName, 1);
    }

    private void checkLabel(boolean source) throws Exception {
        String side = source ? "source" : "destination";
        String name = "__vt_label_control_" + side;
        rename(function(source), name);
        runSwing(() -> filter(source).setFilterText(name));
        waitForRowCount(model(source), 1);
        assertEquals(1, (int) runSwing(() -> model(source).getRowCount()));
        Program program = program(source);
        int tx = program.startTransaction("real unrelated label");
        try {
            ghidra.program.model.symbol.Symbol label = program.getSymbolTable().createLabel(
                program.getAddressFactory().getAddress("1080"), "unrelated_before", SourceType.USER_DEFINED);
            label.setName("unrelated_after", SourceType.USER_DEFINED);
        }
        finally { program.endTransaction(tx, true); }
        program.flushEvents();
        observe(source, "unrelated-label", name, name, 1);
    }

    private void check(boolean source, boolean rename) throws Exception {
        Program program = source ? env.getSourceProgram() : env.getDestinationProgram();
        String side = source ? "source" : "destination";
        GhidraTable table = (GhidraTable) TestUtils.getInstanceField(side + "FunctionsTable", provider);
        GhidraTableFilterPanel<?> filter = (GhidraTableFilterPanel<?>)
            TestUtils.getInstanceField(side + "TableFilterPanel", provider);
        VTFunctionAssociationTableModel model = (VTFunctionAssociationTableModel) table.getModel();
        waitForTableModel((ThreadedTableModel<?, ?>) model);
        assertTrue(model.getRowCount() > 0);
        Function function = model.getFunction(0);
        assertNotNull(function);
        String oldName = "__vt_rename_" + side + "_original";
        rename(function, oldName);
        runSwing(() -> filter.setFilterText(oldName));
        waitForRowCount(model, 1);
        waitForTableModel((ThreadedTableModel<?, ?>) model);
        assertEquals("Real initial name filter must select its one function", 1,
            (int) runSwing(() -> model.getRowCount()));
        assertEquals(function.getEntryPoint(), model.getFunction(0).getEntryPoint());
        assertEquals(oldName, runSwing(() -> filter.getFilterText()));
        if (rename) { rename(function, "__vt_rename_" + side + "_updated"); }
        else { program.flushEvents(); }
        waitForSwing();
        waitForTasks();
        waitForSwing();
        waitForRowCount(model, rename ? 0 : 1);
        waitForTableModel((ThreadedTableModel<?, ?>) model);
        assertEquals(oldName, runSwing(() -> filter.getFilterText()));
        assertEquals(rename ? "__vt_rename_" + side + "_updated" : oldName, function.getName());
        int rows = runSwing(() -> model.getRowCount());
        System.out.println("VT_RENAME_FILTER side=" + side + " rename=" + rename + " rows=" + rows +
            " function=" + function.getName() + " filter=" + oldName + " visible=" + provider.isVisible());
        assertEquals("Active name filter must reflect the actual function name", rename ? 0 : 1, rows);
    }

    private void waitForRowCount(VTFunctionAssociationTableModel model, int expected) {
        // A filter edit uses a deferred Swing update; the model can initially appear idle.
        // Observe natural filtering and program-event work without reapplying the filter.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            boolean settled = runSwing(() ->
                !model.isBusy() && model.getRowCount() == expected);
            if (settled) {
                return;
            }
            sleep(50);
        }
        while (System.nanoTime() < deadline);
        // The caller records the actual row count and makes the numeric assertion.
    }

    private void rename(Function function, String name) throws Exception {
        Program program = function.getProgram();
        int transaction = program.startTransaction("real function rename");
        try { function.setName(name, SourceType.USER_DEFINED); }
        finally { program.endTransaction(transaction, true); }
        program.flushEvents();
        waitForSwing();
    }
}
