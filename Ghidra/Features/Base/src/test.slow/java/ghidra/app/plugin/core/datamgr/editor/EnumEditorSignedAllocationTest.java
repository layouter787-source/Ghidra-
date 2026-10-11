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
package ghidra.app.plugin.core.datamgr.editor;

import static org.junit.Assert.*;

import org.junit.*;

import docking.DefaultActionContext;
import docking.action.DockingActionIf;
import ghidra.app.plugin.core.datamgr.DataTypeManagerPlugin;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.database.ProgramDB;
import ghidra.program.model.data.*;
import ghidra.test.*;

public class EnumEditorSignedAllocationTest extends AbstractGhidraHeadedIntegrationTest {
	private TestEnv env;
	private ToyProgramBuilder builder;
	private ProgramDB program;
	private PluginTool tool;
	private DataTypeManagerPlugin plugin;
	private EnumEditorPanel panel;
	private EnumTableModel model;

	@Before
	public void setUp() throws Exception {
		builder = new ToyProgramBuilder();
		program = builder.getProgram();
		env = new TestEnv();
		tool = env.showTool(program);
		tool.addPlugin(DataTypeManagerPlugin.class.getName());
		plugin = getPlugin(tool, DataTypeManagerPlugin.class);
	}

	@After
	public void tearDown() {
		if (env != null) {
			env.dispose();
		}
		if (builder != null) {
			builder.dispose();
		}
	}

	@Test
	public void testSignedEnumWithFilledNonnegativeRangeUsesNegativeValue() throws Exception {
		EnumDataType values = nonnegativeFilled();
		open(values);
		assertAddedAfter(127, -128);
	}

	@Test
	public void testSignedEnumFindsLastNegativeHole() throws Exception {
		EnumDataType values = new EnumDataType("Values", 1);
		for (int value = -128; value <= 127; value++) {
			if (value != -2) {
				values.add("Value_" + value, value);
			}
		}
		open(values);
		assertAddedAfter(127, -2);
	}

	@Test
	public void testSignedAliasesDoNotHideNegativeCapacity() throws Exception {
		EnumDataType values = nonnegativeFilled();
		values.add("MinusOneAlias", -1);
		open(values);
		assertAddedAfter(127, -128);
	}

	@Test
	public void testOrdinarySelectedSuccessorIsPreserved() throws Exception {
		EnumDataType values = new EnumDataType("Values", 1);
		values.add("Selected", 2);
		open(values);
		assertAddedAfter(2, 3);
	}

	@Test
	public void testUnsignedMaximumWrapsToZero() throws Exception {
		EnumDataType values = new EnumDataType("Values", 1);
		values.add("Selected", 255);
		open(values);
		assertAddedAfter(255, 0);
	}

	@Test
	public void testNegativeSelectedSuccessorIsPreserved() throws Exception {
		EnumDataType values = new EnumDataType("Values", 1);
		values.add("Selected", -3);
		open(values);
		assertAddedAfter(-3, -2);
	}

	@Test
	public void testEmptyEnumStartsAtZero() throws Exception {
		open(new EnumDataType("Values", 1));
		assertPublicAdd(0);
	}

	@Test
	public void testEightByteMaximumWrapsWithoutOverflow() throws Exception {
		EnumDataType values = new EnumDataType("Values", 8);
		values.add("Negative", -1);
		values.add("Selected", Long.MAX_VALUE);
		open(values);
		assertAddedAfter(Long.MAX_VALUE, Long.MIN_VALUE);
	}

	@Test
	public void testGenuinelyFullSignedEnumDoesNotAddAlias() throws Exception {
		EnumDataType values = new EnumDataType("Values", 1);
		for (int value = -128; value <= 127; value++) {
			values.add("Value_" + value, value);
		}
		open(values);
		int[] result = new int[1];
		runSwing(() -> result[0] = model.addEntry(model.getRow("Value_127")));
		assertEquals(-1, result[0]);
		assertEquals(256, model.getEnum().getCount());
		assertNull(model.getEnum().getName(128));
	}

	private EnumDataType nonnegativeFilled() {
		EnumDataType values = new EnumDataType("Values", 1);
		values.add("MinusOne", -1);
		for (int value = 0; value <= 127; value++) {
			values.add("Value_" + value, value);
		}
		assertTrue(values.isSigned());
		assertEquals(-128, values.getMinPossibleValue());
		return values;
	}

	private void open(EnumDataType values) throws Exception {
		int transaction = program.startTransaction("Create enum");
		ghidra.program.model.data.Enum stored;
		try {
			stored = (ghidra.program.model.data.Enum) program.getDataTypeManager()
				.addDataType(values, DataTypeConflictHandler.DEFAULT_HANDLER);
		}
		finally {
			program.endTransaction(transaction, true);
		}
		program.flushEvents();
		runSwing(() -> plugin.edit(stored));
		EnumEditorProvider provider = (EnumEditorProvider) tool.getComponentProvider("Enum Editor");
		assertNotNull(provider);
		panel = (EnumEditorPanel) provider.getComponent();
		model = (EnumTableModel) panel.getTable().getModel();
		waitForSwing();
	}

	private void assertAddedAfter(long selected, long expected) throws Exception {
		String name = model.getEnum().getName(selected);
		assertNotNull(name);
		int row = model.getRow(name);
		assertTrue(row >= 0);
		runSwing(() -> panel.getTable().setRowSelectionInterval(row, row));
		assertPublicAdd(expected);
	}

	private void assertPublicAdd(long expected) throws Exception {
		int originalCount = model.getEnum().getCount();
		assertNull(model.getEnum().getName(expected));
		DockingActionIf action = getAction(plugin, "Add Enum Value");
		assertNotNull(action);
		runSwingLater(() -> action.actionPerformed(new DefaultActionContext()));
		waitForSwing();
		assertEquals(originalCount + 1, model.getEnum().getCount());
		assertEquals(expected, model.getEnum().getValue("New_Name"));
	}
}
