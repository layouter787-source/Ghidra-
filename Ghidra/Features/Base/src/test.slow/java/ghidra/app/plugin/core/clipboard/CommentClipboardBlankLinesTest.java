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
package ghidra.app.plugin.core.clipboard;

import static org.junit.Assert.*;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

import org.junit.*;

import ghidra.app.plugin.core.codebrowser.CodeBrowserPlugin;
import ghidra.app.util.viewer.field.PreCommentFieldFactory;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.database.ProgramDB;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.CommentType;
import ghidra.program.util.CommentFieldLocation;
import ghidra.test.*;
import ghidra.util.task.TaskMonitor;

public class CommentClipboardBlankLinesTest extends AbstractGhidraHeadedIntegrationTest {
	private TestEnv env;
	private ToyProgramBuilder builder;
	private ProgramDB program;
	private CodeBrowserPlugin browser;
	private CodeBrowserClipboardProvider provider;
	private Address address;

	@Before
	public void setUp() throws Exception {
		builder = new ToyProgramBuilder();
		builder.createMemory(".text", "1001000", 16);
		program = builder.getProgram();
		address = program.getAddressFactory().getAddress("1001000");
		env = new TestEnv();
		PluginTool tool = env.showTool(program);
		tool.addPlugin(CodeBrowserPlugin.class.getName());
		browser = env.getPlugin(CodeBrowserPlugin.class);
		provider = new CodeBrowserClipboardProvider(tool, null);
		provider.setProgram(program);
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
	public void testLeadingBlankLine() throws Exception {
		assertCopied("\ntext");
	}

	@Test
	public void testMultipleLeadingBlankLines() throws Exception {
		assertCopied("\n\ntext");
	}

	@Test
	public void testAllBlankLines() throws Exception {
		assertCopied("\n\n");
	}

	@Test
	public void testOrdinaryComment() throws Exception {
		assertCopied("text");
	}

	@Test
	public void testInteriorBlankLines() throws Exception {
		assertCopied("first\n\nlast");
	}

	@Test
	public void testTrailingBlankLines() throws Exception {
		assertCopied("text\n\n");
	}

	private void assertCopied(String comment) throws Exception {
		int transaction = program.startTransaction("Set comment");
		try {
			program.getListing().setComment(address, CommentType.PRE, comment);
		}
		finally {
			program.endTransaction(transaction, true);
		}
		waitForSwing();
		browser.updateNow();
		assertTrue(browser.goToField(address, PreCommentFieldFactory.FIELD_NAME, 0, 0));
		assertTrue(browser.getCurrentLocation() instanceof CommentFieldLocation);
		CommentFieldLocation location = (CommentFieldLocation) browser.getCurrentLocation();
		assertEquals("Stored comment must preserve the input", comment,
			program.getListing().getComment(CommentType.PRE, address));
		assertArrayEquals(comment.split("\n", -1), location.getComment());
		provider.setLocation(location);
		Transferable copied = provider.copy(TaskMonitor.DUMMY);
		assertTrue(copied.isDataFlavorSupported(DataFlavor.stringFlavor));
		assertEquals(comment, copied.getTransferData(DataFlavor.stringFlavor));
	}
}
