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
package ghidra.app.plugin.core.commentwindow;

import static org.junit.Assert.*;

import org.junit.*;

import ghidra.framework.plugintool.PluginTool;
import ghidra.program.database.ProgramDB;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.StringDataType;
import ghidra.program.model.listing.CommentType;
import ghidra.test.*;

public class CommentWindowOffcutDataTest extends AbstractGhidraHeadedIntegrationTest {
	private TestEnv env;
	private ToyProgramBuilder builder;
	private ProgramDB program;
	private PluginTool tool;
	private CommentWindowProvider provider;
	private CommentTableModel model;

	@Before
	public void setUp() throws Exception {
		builder = new ToyProgramBuilder();
		builder.createMemory(".text", "1001000", 32);
		builder.setBytes("1001000", "61 62 63 64 00");
		builder.applyDataType("1001000", new StringDataType());
		builder.addBytesFallthrough("1001010");
		builder.disassemble("1001010", 2);
		program = builder.getProgram();
		env = new TestEnv();
		tool = env.showTool(program);
		tool.addPlugin(CommentWindowPlugin.class.getName());
		CommentWindowPlugin plugin = env.getPlugin(CommentWindowPlugin.class);
		provider = plugin.getProvider();
		model = (CommentTableModel) provider.getTable().getModel();
		runSwing(() -> tool.showComponentProvider(provider, true));
		waitForTableModel(model);
		assertEquals(5, program.getListing().getDefinedDataAt(address("1001000")).getLength());
		assertNull(program.getListing().getCodeUnitAt(address("1001001")));
		assertNotNull(program.getListing().getDataContaining(address("1001001")));
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
	public void testOffcutOnlyCommentSurvivesWindowReopen() throws Exception {
		setComment("1001001", CommentType.EOL, "offcut");
		reopenWindow();
		assertEquals(1, model.getRowCount());
		assertRow("1001001", CommentType.EOL, "offcut");
	}

	@Test
	public void testParentCommentDoesNotSupplyOffcutType() throws Exception {
		setComment("1001000", CommentType.PRE, "parent");
		setComment("1001001", CommentType.EOL, "offcut");
		reopenWindow();
		assertEquals(2, model.getRowCount());
		assertRow("1001000", CommentType.PRE, "parent");
		assertRow("1001001", CommentType.EOL, "offcut");
	}

	@Test
	public void testAllOffcutCommentTypesRemainAtActualAddress() throws Exception {
		for (CommentType type : CommentType.values()) {
			setComment("1001001", type, type.name());
		}
		reopenWindow();
		assertEquals(CommentType.values().length, model.getRowCount());
		for (CommentType type : CommentType.values()) {
			assertRow("1001001", type, type.name());
		}
	}

	@Test
	public void testLiveAddedOffcutCommentSurvivesReload() throws Exception {
		setComment("1001001", CommentType.POST, "live");
		waitForTableModel(model);
		assertEquals("Live public comment event must really add the row", 1, model.getRowCount());
		assertRow("1001001", CommentType.POST, "live");
		reopenWindow();
		assertEquals(1, model.getRowCount());
		assertRow("1001001", CommentType.POST, "live");
	}

	@Test
	public void testParentCommentKeepsActualTypeAndText() throws Exception {
		setComment("1001000", CommentType.PLATE, "parent");
		reopenWindow();
		assertEquals(1, model.getRowCount());
		assertRow("1001000", CommentType.PLATE, "parent");
	}

	@Test
	public void testParentAndOffcutSameTypeKeepSeparateText() throws Exception {
		setComment("1001000", CommentType.EOL, "parent");
		setComment("1001001", CommentType.EOL, "offcut");
		reopenWindow();
		assertEquals(2, model.getRowCount());
		assertRow("1001000", CommentType.EOL, "parent");
		assertRow("1001001", CommentType.EOL, "offcut");
	}

	@Test
	public void testOffcutInstructionCommentRemainsExcluded() throws Exception {
		assertEquals(2, program.getListing().getInstructionAt(address("1001010")).getLength());
		assertNull(program.getListing().getCodeUnitAt(address("1001011")));
		setComment("1001011", CommentType.EOL, "instruction offcut");
		reopenWindow();
		assertEquals(0, model.getRowCount());
	}

	private void reopenWindow() {
		runSwing(() -> tool.showComponentProvider(provider, false));
		waitForTableModel(model);
		assertEquals(0, model.getRowCount());
		runSwing(() -> tool.showComponentProvider(provider, true));
		waitForTableModel(model);
	}

	private void setComment(String address, CommentType type, String comment) {
		int transaction = program.startTransaction("Set comment");
		try {
			program.getListing().setComment(address(address), type, comment);
		}
		finally {
			program.endTransaction(transaction, true);
		}
		waitForSwing();
		waitForTableModel(model);
	}

	private void assertRow(String address, CommentType type, String comment) {
		for (int row = 0; row < model.getRowCount(); row++) {
			CommentRowObject item = model.getRowObject(row);
			if (item.getAddress().equals(address(address)) && item.getCommentType() == type) {
				assertEquals(comment, model.getValueAt(row, CommentTableModel.COMMENT_COL));
				return;
			}
		}
		fail("Missing comment at " + address + " with type " + type);
	}

	private Address address(String value) {
		return program.getAddressFactory().getAddress(value);
	}
}
