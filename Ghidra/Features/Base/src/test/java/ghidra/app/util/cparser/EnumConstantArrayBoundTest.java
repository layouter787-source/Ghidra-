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
import ghidra.program.model.data.*;
import ghidra.test.AbstractGhidraHeadlessIntegrationTest;

/** Enumerator expressions remain numeric through actual header type imports. */
public class EnumConstantArrayBoundTest extends AbstractGhidraHeadlessIntegrationTest {

	@Test
	public void testAnonymousImplicitEnumBound() throws Exception {
		check("typedef enum { MY_ENUM_0=0, MY_ENUM_MAX } my_enum; " +
			"typedef struct { int a[MY_ENUM_MAX]; } my_struct_t;", "my_enum", "MY_ENUM_MAX", 1, 0);
	}

	@Test
	public void testNamedExplicitEnumBound() throws Exception {
		check("enum Bounds { ZERO=0, BOUND=3 }; " +
			"typedef struct { int a[BOUND]; } my_struct_t;", "Bounds", "BOUND", 3, 0);
	}

	@Test
	public void testLiteralArrayBoundControl() throws Exception {
		check("typedef struct { int a[3]; } my_struct_t;", null, null, 3, 0);
	}

	@Test
	public void testFlexibleArrayControl() throws Exception {
		check("typedef struct { int prefix; int a[]; } my_struct_t;", null, null, 0, 1);
	}

	@Test
	public void testDerivedEnumBound() throws Exception {
		check("enum Bounds { BOUND=3 }; typedef struct { int a[BOUND+1]; } my_struct_t;",
			null, null, 4, 0);
	}

	@Test
	public void testSameEnumAliasBound() throws Exception {
		check("enum Bounds { BASE=2, BOUND=BASE+4 }; " +
			"typedef struct { int a[BOUND]; } my_struct_t;", "Bounds", "BOUND", 6, 0);
	}

	@Test
	public void testPriorEnumAliasBound() throws Exception {
		check("enum First { BASE=2 }; enum Bounds { BOUND=BASE+4 }; " +
			"typedef struct { int a[BOUND]; } my_struct_t;", "Bounds", "BOUND", 6, 0);
	}

	@Test
	public void testFunctionLocalEnumDoesNotShadowGlobalBound() throws Exception {
		check("enum Bounds { BOUND=3 }; void f(void) { enum Local { BOUND=1 }; } " +
			"typedef struct { int a[BOUND]; } my_struct_t;", "Bounds", "BOUND", 3, 0);
	}

	@Test
	public void testNestedBlockRestoresOuterEnumerator() throws Exception {
		check("void f(void) { enum Local { BOUND=3 }; { enum Inner { BOUND=5 }; } " +
			"typedef struct { int a[BOUND]; } my_struct_t; }", "Local", "BOUND", 3, 0);
	}

	@Test
	public void testParseFailureRestoresGlobalEnumerator() throws Exception {
		CParser parser = new CParser();
		try {
			parser.parse("enum Bounds { BOUND=3 };");
			try {
				parser.parse("void f(void) { enum Local { BOUND=1 };");
				fail("An unfinished function body must be rejected");
			}
			catch (ghidra.app.util.cparser.C.ParseException expected) {
				// Reuse the public parser after the actual failed nested scope.
			}
			parser.parse("typedef struct { int a[BOUND]; } my_struct_t;");
			checkParsed(parser, "Bounds", "BOUND", 3, 0);
		}
		finally {
			parser.getDataTypeManager().close();
		}
	}

	@Test
	public void testUnknownIdentifierRetainsExistingZeroBound() throws Exception {
		check("typedef struct { int prefix; int a[UNKNOWN_BOUND]; } my_struct_t;",
			null, null, 0, 1);
	}

	@Test
	public void testParameterIdentifierShadowsEnumeratorControl() throws Exception {
		check("enum Bounds { BOUND=3 }; void f(int BOUND) { " +
			"typedef struct { int prefix; int a[BOUND]; } my_struct_t; }", null, null, 0, 1);
	}

	@Test
	public void testLocalIdentifierShadowsEnumeratorControl() throws Exception {
		check("enum Bounds { BOUND=3 }; void f(void) { int BOUND; " +
			"typedef struct { int prefix; int a[BOUND]; } my_struct_t; }", null, null, 0, 1);
	}

	@Test
	public void testMemberIdentifierDoesNotShadowEnumerator() throws Exception {
		check("enum Bounds { BOUND=3 }; typedef struct { int BOUND; int a[BOUND]; } my_struct_t;",
			"Bounds", "BOUND", 3, 1);
	}

	@Test
	public void testPrototypeEnumeratorDoesNotLeak() throws Exception {
		check("enum Bounds { BOUND=3 }; void f(enum Parameter { BOUND=1 } arg); " +
			"typedef struct { int a[BOUND]; } my_struct_t;", "Bounds", "BOUND", 3, 0);
	}

	@Test
	public void testDefinitionParameterEnumeratorIsVisibleInBody() throws Exception {
		check("void f(enum Parameter { BOUND=1 } arg) { " +
			"typedef struct { int a[BOUND]; } my_struct_t; }", "Parameter", "BOUND", 1, 0);
	}

	@Test
	public void testOldStyleParameterShadowsEnumeratorControl() throws Exception {
		check("enum Bounds { BOUND=3 }; void f(BOUND) int BOUND; { " +
			"typedef struct { int prefix; int a[BOUND]; } my_struct_t; }", null, null, 0, 1);
	}

	private void check(String header, String enumName, String member, int count, int ordinal)
			throws Exception {
		CParser parser = new CParser();
		try {
			parser.parse(header);
			checkParsed(parser, enumName, member, count, ordinal);
		}
		finally {
			parser.getDataTypeManager().close();
		}
	}

	private void checkParsed(CParser parser, String enumName, String member, int count, int ordinal) {
		DataTypeManager actual = parser.getDataTypeManager();
		if (enumName != null) {
			DataType e = actual.getDataType("/" + enumName);
			assertNotNull("Actual enum declaration must have been imported", e);
			if (e instanceof TypeDef) {
				e = ((TypeDef) e).getBaseDataType();
			}
			assertTrue(e instanceof ghidra.program.model.data.Enum);
			assertEquals(count, ((ghidra.program.model.data.Enum) e).getValue(member));
		}
		DataType type = actual.getDataType("/my_struct_t");
		assertNotNull("Actual typedef declaration must have been imported", type);
		DataType base = type instanceof TypeDef ? ((TypeDef) type).getBaseDataType() : type;
		assertTrue(base instanceof Structure);
		Structure struct = (Structure) base;
		assertEquals(ordinal + 1, struct.getNumComponents());
		DataType field = struct.getComponent(ordinal).getDataType();
		assertTrue(field instanceof Array);
		Array array = (Array) field;
		assertEquals("a", struct.getComponent(ordinal).getFieldName());
		assertEquals("int", array.getDataType().getName());
		assertEquals(4, array.getElementLength());
		assertEquals(count, array.getNumElements());
		if (count > 0) {
			assertFalse(array.isZeroLength());
			assertEquals(count * 4, array.getLength());
			assertEquals(ordinal * 4 + count * 4, struct.getLength());
		}
		else {
			assertTrue(array.isZeroLength());
			assertEquals(1, array.getLength());
			assertEquals(0, struct.getComponent(ordinal).getLength());
			assertEquals(4, struct.getLength());
			assertEquals(4, struct.getComponent(ordinal).getOffset());
		}
	}
}
