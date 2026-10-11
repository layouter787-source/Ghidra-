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
package ghidra.app.plugin.core.datamgr;

import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

import docking.action.DockingActionIf;
import docking.widgets.dialogs.NumberRangeInputDialog;
import docking.widgets.tree.GTreeNode;
import ghidra.app.plugin.core.datamgr.actions.FindEnumsByValueAction;
import ghidra.app.plugin.core.datamgr.tree.DataTypeNode;
import ghidra.program.database.ProgramBuilder;
import ghidra.program.model.data.EnumDataType;
import ghidra.program.model.data.Enum;
import ghidra.test.*;

public class FindEnumsByValueWidthTest extends AbstractGhidraHeadedIntegrationTest {
	private TestEnv env;
	private ProgramBuilder builder;
	private DataTypeManagerPlugin plugin;

	@Before
	public void setUp() throws Exception {
		env = new TestEnv();
		builder = new ProgramBuilder("EnumWidths", ProgramBuilder._TOY);
		builder.createMemory(".text", "0x1000", 0x100);
		env.launchDefaultTool(builder.getProgram());
		plugin = env.getPlugin(DataTypeManagerPlugin.class);
	}

	@After
	public void tearDown() {
		if (env != null) {
			env.dispose();
		}
	}

	@Test
	public void testLargePositiveEnumValueDoesNotAliasZero() {
		addEnum("TrueZero", 0);
		addEnum("LargePositive", 0x100000000L);
		assertMatches("0", "TrueZero");
	}

	@Test
	public void testLargeNegativeEnumValueDoesNotAliasZero() {
		addEnum("TrueZero", 0);
		addEnum("LargeNegative", Long.MIN_VALUE);
		assertMatches("0", "TrueZero");
	}

	@Test
	public void testUnsigned32AndSignedNegativeAliasesRemainAccepted() {
		addEnum("SignedNegative", -1);
		addEnum("Unsigned32", 0xffffffffL);
		addEnum("Outside32", 0x1ffffffffL);
		assertMatches("0xffffffff", "SignedNegative", "Unsigned32");
	}

	@Test
	public void testOrdinaryRangeAndMixedEnumValues() {
		addEnum("Ordinary", 4, 8);
		addEnum("Mixed", 0x100000004L, 6);
		addEnum("Unrelated", 100);
		assertMatches("4:8", "Ordinary", "Mixed");
	}

	private void addEnum(String name, long... values) {
		EnumDataType enumeration = new EnumDataType(name, 8);
		for (int i = 0; i < values.length; i++) {
			enumeration.add("VALUE" + i, values[i]);
		}
		builder.addDataType(enumeration);
	}

	private void assertMatches(String query, String... expectedNames) {
		DockingActionIf action = getAction(plugin, FindEnumsByValueAction.NAME);
		performAction(action, false);
		NumberRangeInputDialog dialog = waitForDialogComponent(NumberRangeInputDialog.class);
		runSwing(() -> dialog.setValue(query));
		pressButtonByText(dialog, "OK");
		DataTypesProvider results =
			waitForComponentProvider(DataTypesProvider.class, FindEnumsByValueAction.NAME);
		waitForTree(results.getGTree());
		Set<String> actualNames = new HashSet<>();
		Iterator<GTreeNode> nodes = results.getGTree().getViewRoot().iterator(true);
		while (nodes.hasNext()) {
			GTreeNode node = nodes.next();
			if (node instanceof DataTypeNode dataNode && dataNode.getDataType() instanceof Enum) {
				actualNames.add(dataNode.getDataType().getName());
			}
		}
		assertEquals(new HashSet<>(Arrays.asList(expectedNames)), actualNames);
	}
}
