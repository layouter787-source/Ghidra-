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
package ghidra.app.plugin.core.progmgr;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.*;

import docking.ActionContext;
import docking.action.DockingActionIf;
import docking.widgets.OkDialog;
import docking.widgets.OptionDialog;
import ghidra.app.plugin.core.codebrowser.CodeBrowserPlugin;
import ghidra.app.plugin.core.datamgr.DataTypeManagerPlugin;
import ghidra.app.services.*;
import ghidra.framework.model.*;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.database.ProgramBuilder;
import ghidra.program.database.dtarchive.DataTypeArchiveFactory;
import ghidra.program.model.data.CategoryPath;
import ghidra.program.model.data.DataTypeManager;
import ghidra.program.model.dtarchive.ProjectDataTypeArchive;
import ghidra.program.model.listing.Program;
import ghidra.test.*;
import ghidra.util.task.TaskMonitor;

/** Exercises the public Save All action with real project datatype archives. */
public class SaveAllProjectArchiveTest extends AbstractGhidraHeadedIntegrationTest {
	private TestEnv env;
	private PluginTool tool;
	private ProgramBuilder builder;
	private Program program;
	private ProjectDataTypeArchive archive;
	private DataTypeArchiveService archives;
	private CodeBrowserPlugin browser;
	private DockingActionIf saveAll;
	private final AtomicBoolean saveCompleted = new AtomicBoolean();
	private final AtomicReference<Throwable> saveFailure = new AtomicReference<>();

	@Before
	public void setUp() throws Exception {
		env = new TestEnv();
		tool = env.getTool();
		tool.addPlugin(CodeBrowserPlugin.class.getName());
		tool.addPlugin(DataTypeManagerPlugin.class.getName());
		DomainFolder folder = tool.getProject().getProjectData().getRootFolder();
		builder = new ProgramBuilder("SaveAllProgram", ProgramBuilder._TOY);
		DomainFile programFile = folder.createFile("SaveAllProgram", builder.getProgram(),
			TaskMonitor.DUMMY);
		builder.dispose();
		program = tool.getService(ProgramManager.class).openProgram(programFile);
		assertNotNull(program);
		assertFalse(program.isChanged());
		ProjectDataTypeArchive created =
			DataTypeArchiveFactory.createProjectArchive(folder, "SaveAllArchive", this);
		DomainFile archiveFile = created.getDomainFile();
		created.release(this);
		archives = tool.getService(DataTypeArchiveService.class);
		assertNotNull(archives);
		archive = archives.openProjectArchive(archiveFile, Upgrade.NO, Recover.NO,
			TaskMonitor.DUMMY);
		assertTrue(archive.isChangeable());
		assertTrue(archive.canSave());
		assertFalse(archive.isChanged());
		assertTrue(Arrays.asList(tool.getDomainFiles()).contains(archiveFile));
		browser = getPlugin(tool, CodeBrowserPlugin.class);
		saveAll = getAction(tool, ProgramManagerPlugin.class.getSimpleName(), "Save All Files");
		assertNotNull(saveAll);
		env.showTool();
		waitForSwing();
	}

	@After
	public void tearDown() {
		if (archive != null && archives != null) {
			archives.closeArchive(archive);
		}
		if (env != null) {
			env.dispose();
		}
		if (builder != null) {
			builder.dispose();
		}
	}

	@Test
	public void testSaveAllSavesDirtyProjectArchiveWithoutDirtyProgram() throws Exception {
		dirty(archive.getDataTypeManager(), "ArchiveOnly");
		assertTrue(archive.isChanged());
		assertFalse(program.isChanged());
		assertSaveAllEnabled();
		performAction(saveAll, browser.getProvider(), false);
		assertAndCloseSaveResult("Saved 1 modified data type archives.");
		assertFalse(archive.isChanged());
		DomainFile file = archive.getDomainFile();
		archives.closeArchive(archive);
		archive = archives.openProjectArchive(file, Upgrade.NO, Recover.NO, TaskMonitor.DUMMY);
		assertNotNull(archive.getDataTypeManager().getCategory(new CategoryPath("/ArchiveOnly")));
		assertFalse(archive.isChanged());
		assertFalse(program.isChanged());
	}

	@Test
	public void testSaveAllSavesProgramAndProjectArchive() {
		dirty(archive.getDataTypeManager(), "ArchiveMixed");
		dirty(program.getDataTypeManager(), "ProgramMixed");
		assertTrue(archive.isChanged());
		assertTrue(program.isChanged());
		assertSaveAllEnabled();
		performAction(saveAll, browser.getProvider(), false);
		assertAndCloseSaveResult("Saved 1 modified programs and 1 modified data type archives.");
		assertFalse(program.isChanged());
		assertFalse(archive.isChanged());
		assertNotNull(program.getDataTypeManager().getCategory(new CategoryPath("/ProgramMixed")));
		assertNotNull(archive.getDataTypeManager().getCategory(new CategoryPath("/ArchiveMixed")));
	}

	@Test
	public void testSaveAllPreservesProgramOnlyResultWithCleanArchive() {
		dirty(program.getDataTypeManager(), "ProgramOnly");
		assertFalse(archive.isChanged());
		assertSaveAllEnabled();
		performAction(saveAll, browser.getProvider(), false);
		assertAndCloseSaveResult("Saved 1 modified programs.");
		assertFalse(program.isChanged());
		assertFalse(archive.isChanged());
		ActionContext context = runSwing(() -> browser.getProvider().getActionContext(null));
		assertFalse(runSwing(() -> saveAll.isEnabledForContext(context)));
	}

	@Test
	public void testSaveAllReportsReadOnlyProjectArchiveWithoutLosingChanges() throws Exception {
		dirty(archive.getDataTypeManager(), "ReadOnlyArchive");
		dirty(program.getDataTypeManager(), "ProgramMixed");
		DomainFile file = archive.getDomainFile();
		file.setReadOnly(true);
		try {
			assertTrue(archive.isChanged());
			assertFalse(archive.canSave());
			assertSaveAllEnabled();
			startSaveAll();
			assertAndCloseSaveResult("Saved 1 modified programs.");
			OptionDialog warning = nextSaveDialog();
			assertNotNull("Save All completed without reporting the unsaved project archive", warning);
			assertEquals("Save All...", warning.getTitle());
			assertEquals("Unable to save 1 modified data type archives.", warning.getMessage());
			pressButtonByText(warning, "OK");
			waitForCondition(saveCompleted::get);
			assertNull("Public Save All action failed", saveFailure.get());
			assertFalse(program.isChanged());
			assertTrue(archive.isChanged());
			assertNotNull(archive.getDataTypeManager().getCategory(new CategoryPath("/ReadOnlyArchive")));
		}
		finally {
			file.setReadOnly(false);
		}
	}

	@Test
	public void testSaveAllDeclinedBusyArchiveRetainsChanges() {
		dirty(archive.getDataTypeManager(), "BusyArchive");
		dirty(program.getDataTypeManager(), "ProgramMixed");
		int transaction = archive.startTransaction("Outstanding archive editor transaction");
		try {
			assertTrue(archive.isChanged());
			assertTrue(archive.canSave());
			assertSaveAllEnabled();
			startSaveAll();
			OptionDialog busy = nextSaveDialog();
			assertNotNull("Save All completed without the busy archive prompt", busy);
			assertFalse("The program save precedes the archive save", program.isChanged());
			assertTrue(archive.isChanged());
			assertEquals("Save Data Type Archive (Busy)", busy.getTitle());
			assertTrue(busy.getMessage().contains("Outstanding archive editor transaction"));
			pressButtonByText(busy, "Cancel");
			assertAndCloseSaveResult("Saved 1 modified programs.");
			assertAndCloseSaveResult("Unable to save 1 modified data type archives.");
			waitForCondition(saveCompleted::get);
			assertNull("Public Save All action failed", saveFailure.get());
			assertFalse(program.isChanged());
			assertTrue(archive.isChanged());
			assertNotNull(archive.getDataTypeManager().getCategory(new CategoryPath("/BusyArchive")));
		}
		finally {
			archive.endTransaction(transaction, false);
		}
	}

	private void startSaveAll() {
		ActionContext context = runSwing(() -> browser.getProvider().getActionContext(null));
		runSwing(() -> {
			try {
				saveAll.actionPerformed(context);
			}
			catch (Throwable t) {
				saveFailure.set(t);
			}
			finally {
				saveCompleted.set(true);
			}
		}, false);
	}

	private OptionDialog nextSaveDialog() {
		waitForCondition(() -> saveCompleted.get() || getDialogComponent(OptionDialog.class) != null);
		assertNull("Public Save All action failed", saveFailure.get());
		return getDialogComponent(OptionDialog.class);
	}

	private void assertSaveAllEnabled() {
		ActionContext context = runSwing(() -> browser.getProvider().getActionContext(null));
		assertTrue(saveAll.isValidContext(context));
		assertTrue(runSwing(() -> saveAll.isEnabledForContext(context)));
	}

	private void assertAndCloseSaveResult(String message) {
		OkDialog dialog = waitForDialogComponent(OkDialog.class);
		assertEquals("Save All...", dialog.getTitle());
		assertEquals(message, dialog.getMessage());
		pressButtonByText(dialog, "OK");
		waitForSwing();
	}

	private void dirty(DataTypeManager manager, String name) {
		int transaction = manager.startTransaction("Add " + name);
		try {
			manager.createCategory(new CategoryPath("/" + name));
		}
		finally {
			manager.endTransaction(transaction, true);
		}
		waitForSwing();
	}
}
