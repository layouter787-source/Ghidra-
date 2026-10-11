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
package ghidra.app.plugin.core.sourcefilestable;

import static org.junit.Assert.*;

import java.awt.event.MouseEvent;

import javax.swing.JTable;

import org.junit.*;

import docking.ActionContext;
import docking.action.DockingActionIf;
import docking.widgets.table.threaded.ThreadedTableModel;
import docking.widgets.values.ValuesMapDialog;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.database.sourcemap.UserDataPathTransformer;
import ghidra.program.model.sourcemap.SourcePathTransformer;
import ghidra.test.*;
import ghidra.util.table.GhidraFilterTable;

public class SourceTransformActionTest extends AbstractGhidraHeadedIntegrationTest {
	private TestEnv env;
	private ToyProgramBuilder builder;
	private SourceFilesTableProvider provider;
	private JTable table;
	private DockingActionIf edit;
	private DockingActionIf remove;

	@Before
	public void setUp() throws Exception {
		env = new TestEnv();
		PluginTool tool = env.getTool();
		tool.addPlugin(SourceFilesTablePlugin.class.getName());
		SourceFilesTablePlugin plugin = env.getPlugin(SourceFilesTablePlugin.class);
		provider = (SourceFilesTableProvider) getInstanceField("provider", plugin);
		GhidraFilterTable<?> filterTable =
			(GhidraFilterTable<?>) getInstanceField("transformsTable", provider);
		table = filterTable.getTable();
		edit = getAction(tool, provider.getName(), "Edit Transform");
		remove = getAction(tool, provider.getName(), "Remove Transform");
		assertNotNull(edit);
		assertNotNull(remove);
		builder = new ToyProgramBuilder("SourceTransforms", true, this);
		env.showTool(builder.getProgram());
		runSwing(() -> provider.setVisible(true));
		waitForTableModel((ThreadedTableModel<?, ?>) table.getModel());
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
	public void testEmptyTransformTableCannotEdit() {
		assertEquals(0, table.getRowCount());
		assertFalse(remove.isEnabledForContext(context()));
		assertFalse(edit.isEnabledForContext(context()));
	}

	@Test
	public void testClearedTransformSelectionCannotEdit() {
		addTransforms();
		runSwing(() -> table.clearSelection());
		assertEquals(0, table.getSelectedRowCount());
		assertFalse(remove.isEnabledForContext(context()));
		assertFalse(edit.isEnabledForContext(context()));
	}

	@Test
	public void testMultipleTransformsCannotEdit() {
		addTransforms();
		runSwing(() -> table.setRowSelectionInterval(0, 1));
		assertEquals(2, table.getSelectedRowCount());
		assertFalse(remove.isEnabledForContext(context()));
		assertFalse(edit.isEnabledForContext(context()));
	}

	@Test
	public void testSingleTransformOpensEditor() {
		addTransforms();
		runSwing(() -> table.setRowSelectionInterval(0, 0));
		ActionContext context = context();
		assertTrue(remove.isEnabledForContext(context));
		assertTrue(edit.isEnabledForContext(context));
		runSwingLater(() -> edit.actionPerformed(context));
		ValuesMapDialog dialog = waitForDialogComponent(ValuesMapDialog.class);
		assertEquals("Enter Directory Transform", dialog.getTitle());
		pressButtonByText(dialog, "Cancel");
		waitForSwing();
		assertEquals(2, table.getRowCount());
	}

	private void addTransforms() {
		SourcePathTransformer transformer =
			UserDataPathTransformer.getPathTransformer(builder.getProgram());
		transformer.addDirectoryTransform("/source/", "/target/");
		transformer.addDirectoryTransform("/other/", "/destination/");
		runSwing(() -> provider.componentShown());
		waitForTableModel((ThreadedTableModel<?, ?>) table.getModel());
		assertEquals(2, table.getRowCount());
	}

	private ActionContext context() {
		MouseEvent event = new MouseEvent(table, MouseEvent.MOUSE_RELEASED,
			System.currentTimeMillis(), 0, 1, 1, 1, true, MouseEvent.BUTTON3);
		ActionContext context = runSwing(() -> provider.getActionContext(event));
		assertNotNull(context);
		return context;
	}
}
