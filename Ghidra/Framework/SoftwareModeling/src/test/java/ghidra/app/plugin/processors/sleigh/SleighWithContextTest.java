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
package ghidra.app.plugin.processors.sleigh;

import static org.junit.Assert.*;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;

import org.junit.Test;

import generic.jar.ResourceFile;
import generic.test.AbstractGenericTest;
import ghidra.app.util.PseudoInstruction;
import ghidra.pcodeCPort.slgh_compile.SleighCompileLauncher;
import ghidra.program.model.address.Address;
import ghidra.program.model.lang.*;
import ghidra.program.model.mem.ByteMemBufferImpl;

public class SleighWithContextTest extends AbstractGenericTest {
	private static final String DEFINITIONS = """
		define endian=little;
		define space ram type=ram_space size=4 default;
		define space register type=register_space size=4;
		define register offset=0 size=4 [PC ctx];
		define context ctx
		    flag=(0,0)
		    second=(1,1)
		;
		define token opbyte(8) op=(0,7);
		""";

	@Test
	public void testWithActionsWithoutConstructorActions() throws Exception {
		compileAndDecode("""
			with : epsilon [flag=1;] {
			    :A is op=0 {}
			}
			""", 1, 0);
	}

	@Test
	public void testNestedWithActionsWithoutConstructorActions() throws Exception {
		compileAndDecode("""
			with : epsilon [flag=0;] {
			    with : epsilon [flag=1; second=1;] {
			        :A is op=0 {}
			    }
			}
			""", 1, 1);
	}

	@Test
	public void testWithActionsAndConstructorActions() throws Exception {
		compileAndDecode("""
			with : epsilon [flag=1;] {
			    :A is op=0 [second=1;] {}
			}
			""", 1, 1);
	}

	@Test
	public void testConstructorActionsWithoutWithBlock() throws Exception {
		compileAndDecode(":A is op=0 [flag=1;] {}", 1, 0);
	}

	private void compileAndDecode(String constructors, long expectedFlag, long expectedSecond)
			throws Exception {
		File directory = createTempDirectory("sleigh-with-context");
		File source = new File(directory, "test.slaspec");
		File compiled = new File(directory, "test.sla");
		Files.writeString(source.toPath(), DEFINITIONS + constructors);
		assertEquals(0, SleighCompileLauncher.runMain(
			new String[] { source.getAbsolutePath(), compiled.getAbsolutePath() }));
		Files.writeString(new File(directory, "test.pspec").toPath(),
			"<processor_spec><programcounter register=\"PC\"/></processor_spec>");
		Files.writeString(new File(directory, "test.cspec").toPath(), "<compiler_spec/>");
		File languageDefinitions = new File(directory, "test.ldefs");
		Files.writeString(languageDefinitions.toPath(), """
			<language_definitions>
			  <language processor="WithTest" endian="little" size="32" variant="default"
			      version="1.0" slafile="test.sla" processorspec="test.pspec"
			      id="WithTest:LE:32:default">
			    <description>With context regression</description>
			    <compiler name="default" spec="test.cspec" id="default"/>
			  </language>
			</language_definitions>
			""");
		SleighLanguageProvider provider =
			new SleighLanguageProvider(new ResourceFile(languageDefinitions));
		SleighLanguage language = (SleighLanguage) provider.getLanguage(
			new LanguageID("WithTest:LE:32:default"));
		Address address = language.getDefaultSpace().getAddress(0);
		ByteMemBufferImpl memory = new ByteMemBufferImpl(address, new byte[] { 0 }, false);
		ProcessorContext context = new ProcessorContextImpl(language);
		InstructionPrototype prototype = language.parse(memory, context, false);
		PseudoInstruction instruction = new PseudoInstruction(address, prototype, memory, context);
		assertEquals("A", instruction.getMnemonicString());
		assertEquals(1, instruction.getLength());
		SleighParserContext parsed = (SleighParserContext) instruction.getParserContext();
		RegisterValue values = parsed.getContextRegisterValue();
		assertEquals(BigInteger.valueOf(expectedFlag),
			values.getRegisterValue(language.getRegister("flag")).getUnsignedValue());
		assertEquals(BigInteger.valueOf(expectedSecond),
			values.getRegisterValue(language.getRegister("second")).getUnsignedValue());
	}
}
