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
package ghidra.app.plugin.core.bookmark;

import static org.junit.Assert.*;

import org.junit.*;

import docking.action.DockingActionIf;
import ghidra.framework.options.SaveState;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.database.ProgramDB;
import ghidra.program.model.listing.*;
import ghidra.test.*;
import ghidra.util.table.GhidraTable;
import docking.widgets.table.threaded.ThreadedTableModel;

public class BookmarkEmptyFilterTest extends AbstractGhidraHeadedIntegrationTest {
	private TestEnv env;
	private ToyProgramBuilder builder;
	private ProgramDB program;
	private PluginTool tool;
	private BookmarkPlugin plugin;
	private BookmarkProvider provider;
	private BookmarkTableModel model;

	@Before
	public void setUp() throws Exception {
		builder = new ToyProgramBuilder();
		builder.createMemory(".text", "1001000", 16);
		program = builder.getProgram();
		addBookmark("First");
		addBookmark("Second");
		env = new TestEnv();
		tool = env.showTool(program);
		tool.addPlugin(BookmarkPlugin.class.getName());
		plugin = env.getPlugin(BookmarkPlugin.class);
		provider = (BookmarkProvider) getInstanceField("provider", plugin);
		model = (BookmarkTableModel) provider.getBookmarkTable().getModel();
		performAction(getAction(plugin, "Bookmarks"), true);
		waitForTable();
		assertTrue(provider.isShowingType("First"));
		assertTrue(provider.isShowingType("Second"));
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
	public void testHideAllIsAnAppliedFilter() throws Exception {
		hideAll();
		assertTrue(provider.hasTypeFilterApplied());
	}

	@Test
	public void testHideAllSurvivesAddedType() throws Exception {
		hideAll();
		addBookmark("NewType");
		waitForSwing();
		waitForTable();
		assertEquals(0, model.getRowCount());
		assertFalse(provider.isShowingType("First"));
		assertFalse(provider.isShowingType("NewType"));
	}

	@Test
	public void testHideAllSurvivesXmlStateRoundTrip() throws Exception {
		hideAll();
		SaveState saved = saveAndParse();
		assertArrayEquals(new String[0], saved.getStrings("BOOKMARK_TYPES", null));
		runSwing(() -> provider.setFilterTypes(java.util.List.of("First", "Second")));
		runSwing(() -> provider.readConfigState(saved));
		waitForTable();
		assertEquals(0, model.getRowCount());
	}

	@Test
	public void testHideAllSurvivesTransientStateAndReload() throws Exception {
		hideAll();
		BookmarkPlugin.BookmarkTransientState state = plugin.getTransientState();
		runSwing(() -> provider.setFilterTypes(java.util.List.of("First")));
		runSwing(() -> plugin.restoreTransientState(state));
		runSwing(() -> provider.setProgram(program));
		waitForTable();
		assertEquals(0, model.getRowCount());
	}

	@Test
	public void testAbsentStateKeepsDefaultTypes() throws Exception {
		runSwing(() -> provider.readConfigState(new SaveState()));
		assertTrue(provider.isShowingType("First"));
		assertTrue(provider.isShowingType("Second"));
	}

	@Test
	public void testNonemptySelectionSurvivesXmlStateRoundTrip() throws Exception {
		runSwing(() -> provider.setFilterTypes(java.util.List.of("First")));
		SaveState saved = saveAndParse();
		runSwing(() -> provider.setFilterTypes(java.util.List.of("First", "Second")));
		runSwing(() -> provider.readConfigState(saved));
		assertTrue(provider.isShowingType("First"));
		assertFalse(provider.isShowingType("Second"));
	}

	@Test
	public void testNewTypeRemainsDisabledForNonemptyFilter() throws Exception {
		runSwing(() -> provider.setFilterTypes(java.util.List.of("First")));
		addBookmark("NewType");
		waitForSwing();
		waitForTable();
		assertTrue(provider.isShowingType("First"));
		assertFalse(provider.isShowingType("NewType"));
	}

	@Test
	public void testMissingSelectedTypeKeepsExistingFallback() throws Exception {
		SaveState saved = new SaveState();
		saved.putStrings("BOOKMARK_TYPES", new String[] { "AbsentType" });
		runSwing(() -> provider.readConfigState(new SaveState(saved.saveToXml())));
		runSwing(() -> provider.setProgram(program));
		runSwing(() -> provider.setProgram(program));
		waitForTable();
		assertTrue(provider.isShowingType("First"));
		assertTrue(provider.isShowingType("Second"));
	}

	private SaveState saveAndParse() {
		SaveState saved = new SaveState();
		runSwing(() -> provider.writeConfigState(saved));
		return new SaveState(saved.saveToXml());
	}

	private void hideAll() throws Exception {
		DockingActionIf action = getAction(plugin, "Filter Bookmarks");
		performAction(action, false);
		FilterDialog dialog = waitForDialogComponent(FilterDialog.class);
		runSwing(() -> {
			for (BookmarkType type : program.getBookmarkManager().getBookmarkTypes()) {
				dialog.setFilter(type.getTypeString(), false);
			}
			dialog.okCallback();
		});
		waitForTable();
		assertEquals("Dialog must really hide all bookmarks", 0, model.getRowCount());
	}

	private void addBookmark(String type) {
		int transaction = program.startTransaction("Add bookmark");
		try {
			program.getBookmarkManager().setBookmark(
				program.getAddressFactory().getAddress("1001000"), type, "test", "comment");
		}
		finally {
			program.endTransaction(transaction, true);
		}
	}

	private void waitForTable() {
		waitForTableModel(model);
	}
}
