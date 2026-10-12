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
package ghidra.app.util.cparser;

import static org.junit.Assert.*;

import org.junit.Test;

import ghidra.app.util.cparser.C.CParser;
import ghidra.app.util.cparser.C.ParseException;
import ghidra.program.model.data.*;
import ghidra.test.AbstractGhidraHeadlessIntegrationTest;

public class VoidTypedefParameterTest extends AbstractGhidraHeadlessIntegrationTest {
	@Test
	public void testVoidTypedefHasNoArguments() throws Exception {
		check("typedef void Void;", "void foo(Void);", 0);
	}

	@Test
	public void testNestedVoidTypedefHasNoArguments() throws Exception {
		check("typedef void Void; typedef Void Empty;", "void foo(Empty);", 0);
	}

	@Test
	public void testVoidTypedefFunctionPointerHasNoArguments() throws Exception {
		CParser parser = new CParser();
		try {
			parser.parse("typedef void Void;");
			DataType parsed = parser.parse("typedef void (*Callback)(Void);");
			assertTrue(parsed instanceof TypeDef);
			DataType base = ((TypeDef) parsed).getBaseDataType();
			assertTrue(base instanceof Pointer);
			DataType pointed = ((Pointer) base).getDataType();
			assertTrue(pointed instanceof FunctionDefinition);
			assertEquals(0, ((FunctionDefinition) pointed).getArguments().length);
			assertTrue(VoidDataType.isVoidDataType(
				((FunctionDefinition) pointed).getReturnType()));
		}
		finally {
			parser.getDataTypeManager().close();
		}
	}

	@Test
	public void testDirectVoidControl() throws Exception {
		check("", "void foo(void);", 0);
	}

	@Test
	public void testVoidPointerControl() throws Exception {
		check("typedef void Void;", "void foo(Void *);", 1);
	}

	@Test
	public void testIntegerTypedefControl() throws Exception {
		check("typedef int Count;", "void foo(Count);", 1);
	}

	@Test
	public void testMultipleVoidParametersRemainRejected() throws Exception {
		CParser parser = new CParser();
		try {
			parser.parse("typedef void Void;");
			try {
				parser.parse("void foo(Void, int);");
				fail("A void typedef cannot be a parameter alongside another parameter");
			}
			catch (ParseException expected) {
				// The no-arguments rule applies only to a single void parameter.
			}
		}
		finally {
			parser.getDataTypeManager().close();
		}
	}

	private void check(String typedef, String prototype, int arguments) throws Exception {
		CParser parser = new CParser();
		try {
			if (!typedef.isEmpty()) {
				parser.parse(typedef);
			}
			DataType parsed = parser.parse(prototype);
			assertTrue(parsed instanceof FunctionDefinition);
			FunctionDefinition function = (FunctionDefinition) parsed;
			assertEquals(arguments, function.getArguments().length);
			assertTrue(VoidDataType.isVoidDataType(function.getReturnType()));
			if (prototype.contains("*")) {
				assertTrue(function.getArguments()[0].getDataType() instanceof Pointer);
			}
		}
		finally {
			parser.getDataTypeManager().close();
		}
	}
}
