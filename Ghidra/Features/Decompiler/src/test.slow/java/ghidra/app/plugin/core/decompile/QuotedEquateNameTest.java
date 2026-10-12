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
package ghidra.app.plugin.core.decompile;

import static org.junit.Assert.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import org.junit.Test;

import ghidra.app.decompiler.*;
import ghidra.app.plugin.core.decompile.actions.ConvertCharAction;
import ghidra.framework.Application;
import ghidra.framework.Platform;
import ghidra.program.database.ProgramBuilder;
import ghidra.program.model.data.IntegerDataType;
import ghidra.program.model.listing.*;
import ghidra.program.model.pcode.*;
import ghidra.program.model.scalar.Scalar;
import ghidra.program.model.symbol.*;
import ghidra.test.*;
import ghidra.util.task.TaskMonitor;

/** Exercises genuine listing equates consumed by the native decompiler. */
public class QuotedEquateNameTest extends AbstractGhidraHeadedIntegrationTest {
	@Test
	public void testSingleQuoteCustomNameRemainsSymbol() throws Exception {
		check("'CUSTOM", false, 65);
	}

	@Test
	public void testDoubleQuoteCustomNameRemainsSymbol() throws Exception {
		check("\"CUSTOM", false, 65);
	}

	@Test
	public void testOrdinaryCustomNameControl() throws Exception {
		check("CUSTOM", false, 65);
	}

	@Test
	public void testActualCharacterConversionControl() throws Exception {
		check(null, true, 65);
	}

	@Test
	public void testNullCharacterConversionControl() throws Exception {
		check(null, true, 0);
	}

	@Test
	public void testNewlineCharacterConversionControl() throws Exception {
		check(null, true, 10);
	}

	@Test
	public void testQuoteCharacterConversionControl() throws Exception {
		check(null, true, 39);
	}

	@Test
	public void testBackslashCharacterConversionControl() throws Exception {
		check(null, true, 92);
	}

	@Test
	public void testPackedCharacterConversionControl() throws Exception {
		check(null, true, 0x4142);
		check(null, true, 0x41424344);
	}

	@Test
	public void testMixedRawByteCharacterConversionControl() throws Exception {
		check(null, true, 0x414141ff);
	}

	@Test
	public void testEscapedSingleQuoteCustomNameRemainsSymbol() throws Exception {
		check("'CUSTOM\\'NAME", false, 65);
	}

	@Test
	public void testEscapedDoubleQuoteCustomNameRemainsSymbol() throws Exception {
		check("\"CUSTOM\\\"NAME", false, 65);
	}

	private void check(String requestedName, boolean character, int value) throws Exception {
		ProgramBuilder builder = new ProgramBuilder("equate", ProgramBuilder._X86);
		TestEnv env = null;
		DecompInterface decompiler = new DecompInterface();
		try {
			builder.createMemory("code", "0x1000", 0x100);
			builder.setBytes("0x1000", String.format("b8 %02x %02x %02x %02x c3",
				value & 0xff, (value >>> 8) & 0xff, (value >>> 16) & 0xff, (value >>> 24) & 0xff));
			builder.disassemble("0x1000", 6);
			Program program = builder.getProgram();
			Function function = program.getFunctionManager().getFunctionAt(builder.addr("0x1000"));
			if (function == null) {
				function = builder.createFunction("0x1000");
			}
			assertNotNull(function);
			assertEquals(6L, function.getBody().getNumAddresses());
			Instruction instruction = program.getListing().getInstructionAt(builder.addr("0x1000"));
			Scalar scalar = instruction.getScalar(1);
			assertNotNull("Real MOV immediate operand must be available", scalar);
			assertEquals(Integer.toUnsignedLong(value), scalar.getUnsignedValue());
			assertEquals(32, scalar.bitLength());
			String name = requestedName;
			String characterLiteral = null;
			if (character) {
				env = new TestEnv();
				env.getTool().addPlugin(DecompilePlugin.class.getName());
				DecompilePlugin plugin = getPlugin(env.getTool(), DecompilePlugin.class);
				ConvertCharAction action = new ConvertCharAction(plugin);
				name = action.getEquateName(scalar, program);
				characterLiteral = action.getMenuDisplay(scalar, program);
				// Native C escapes quotes and backslashes even though menu labels do not.
				if (value == 39) {
					characterLiteral = "L'\\''";
				}
				else if (value == 92) {
					characterLiteral = "L'\\\\'";
				}
				assertNotNull(name);
				if (value == 0x414141ff) {
					assertTrue("Actual canonical mixed-byte name must start with a quote", name.startsWith("\""));
					assertTrue("Actual canonical raw-byte suffix must remain covered", name.endsWith("FFh"));
				}
			}
			int transaction = program.startTransaction("Apply actual listing equate");
			try {
				function.setReturnType(IntegerDataType.dataType, SourceType.USER_DEFINED);
				Equate equate = program.getEquateTable().createEquate(name, value);
				equate.addReference(instruction.getAddress(), 1);
				assertEquals(name, equate.getDisplayName());
				assertEquals(name, program.getEquateTable().getEquate(name).getName());
				assertEquals(1, equate.getReferences(instruction.getAddress()).size());
			}
			finally {
				program.endTransaction(transaction, true);
			}
			assertTrue(decompiler.openProgram(program));
			DecompileResults result = decompiler.decompileFunction(function, 30, TaskMonitor.DUMMY);
			assertTrue(result.getErrorMessage(), result.decompileCompleted());
			assertNotNull(result.getHighFunction());
			assertNotNull(result.getDecompiledFunction());
			assertCurrentNative();
			ArrayList<EquateSymbol> symbols = new ArrayList<>();
			result.getHighFunction().getLocalSymbolMap().getSymbols().forEachRemaining(symbol -> {
				if (symbol instanceof EquateSymbol equate && equate.getValue() == value) {
					symbols.add(equate);
				}
			});
			assertEquals("Actual native equate symbol must be returned", 1, symbols.size());
			EquateSymbol actual = symbols.get(0);
			String code = result.getDecompiledFunction().getC();
			System.out.println("ACTUAL_QUOTED_EQUATE requested=" + name + " character=" + character +
				" convert=" + actual.getConvert() + " name=" + actual.getName() + " c=" + code);
			assertEquals(character ? EquateSymbol.FORMAT_CHAR : EquateSymbol.FORMAT_DEFAULT,
				actual.getConvert());
			if (character && value <= 0x7f) {
				assertTrue("Native rendered C must preserve actual character conversion",
					code.contains(characterLiteral));
			}
			else if (!character) {
				assertEquals(name, actual.getName());
				assertTrue("Native rendered C must preserve custom equate", code.contains(name));
			}
		}
		finally {
			decompiler.dispose();
			if (env != null) {
				env.dispose();
			}
			builder.dispose();
		}
	}

	private void assertCurrentNative() throws Exception {
		Field path = DecompileProcessFactory.class.getDeclaredField("exepath");
		path.setAccessible(true); // Read-only current executable inspection.
		File actual = new File((String) path.get(null));
		File expected = new File(Application.getModuleRootDir("Decompiler").getFile(false),
			"build/os/" + Platform.CURRENT_PLATFORM.getDirectoryName() + "/decompile");
		assertEquals(expected.getCanonicalFile(), actual.getCanonicalFile());
		assertTrue(actual.isFile());
		System.out.println("QUOTED_EQUATE_CURRENT_NATIVE path=" + actual);
	}
}
