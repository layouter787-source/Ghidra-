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
package ghidra.app.util.viewer.field;

import static org.junit.Assert.*;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

import org.junit.*;

import docking.widgets.fieldpanel.field.FieldElement;
import ghidra.app.plugin.core.codebrowser.CodeBrowserPlugin;
import ghidra.app.util.viewer.field.ListingColors.XrefColors;
import ghidra.program.database.ProgramDB;
import ghidra.program.model.data.ByteDataType;
import ghidra.program.model.symbol.*;
import ghidra.test.*;

public class XRefIndirectTypeTest extends AbstractGhidraHeadedIntegrationTest {
	private ToyProgramBuilder builder;
	private TestEnv env;
	private ProgramDB program;
	private CodeBrowserPlugin browser;

	@Before
	public void setUp() throws Exception {
		builder = new ToyProgramBuilder();
		builder.createMemory(".text", "1001000", 32);
		builder.applyDataType("1001010", ByteDataType.dataType);
		program = builder.getProgram();
		env = new TestEnv();
		env.launchDefaultTool(program);
		browser = env.getPlugin(CodeBrowserPlugin.class);
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
	public void testIndirectWriteUsesWriteTextAndColor() {
		check(RefType.WRITE_IND, "(W)", XrefColors.WRITE, null);
	}

	@Test
	public void testIndirectDataUsesDataTextAndColor() {
		check(RefType.DATA_IND, "(*)", XrefColors.OTHER, null);
	}

	@Test
	public void testDirectRead() {
		check(RefType.READ, "(R)", XrefColors.READ, null);
	}

	@Test
	public void testDirectWrite() {
		check(RefType.WRITE, "(W)", XrefColors.WRITE, null);
	}

	@Test
	public void testDirectData() {
		check(RefType.DATA, "(*)", XrefColors.OTHER, null);
	}

	@Test
	public void testIndirectRead() {
		check(RefType.READ_IND, "(R)", XrefColors.READ, null);
	}

	@Test
	public void testDirectReadWrite() {
		check(RefType.READ_WRITE, "(RW)", XrefColors.READ, XrefColors.WRITE);
	}

	@Test
	public void testIndirectReadWrite() {
		check(RefType.READ_WRITE_IND, "(RW)", XrefColors.READ, XrefColors.WRITE);
	}

	private void check(RefType type, String suffix, Color first, Color second) {
		int transaction = program.startTransaction("Create actual reference");
		try {
			Reference reference = program.getReferenceManager().addMemoryReference(
				builder.addr("1001000"), builder.addr("1001010"), type,
				SourceType.USER_DEFINED, 0);
			assertEquals(type, reference.getReferenceType());
		}
		finally {
			program.endTransaction(transaction, true);
		}
		waitForSwing();
		browser.updateNow();
		assertTrue(browser.goToField(builder.addr("1001010"),
			XRefFieldFactory.FIELD_NAME, 0, 0));
		ListingTextField field = (ListingTextField) browser.getCurrentField();
		Set<FieldElement> seen = new HashSet<>();
		for (int row = 0; row < field.getNumRows(); row++) {
			for (int col = 0; col < field.getNumCols(row); col++) {
				FieldElement element = field.getFieldElement(row, col);
				if (!seen.add(element)) {
					continue;
				}
				String text = element.getText();
				int index = text.indexOf(suffix);
				if (index >= 0) {
					assertEquals(first, element.getColor(index + 1));
					if (second != null) {
						assertEquals(second, element.getColor(index + 2));
					}
					return;
				}
			}
		}
		fail("Actual XRef field lacks " + suffix + " for " + type);
	}
}
