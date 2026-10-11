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
package ghidra.app.util;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import db.Transaction;
import generic.test.AbstractGenericTest;
import ghidra.program.database.ProgramDB;
import ghidra.program.model.address.Address;
import ghidra.program.model.lang.Language;
import ghidra.program.model.lang.LanguageID;
import ghidra.program.model.symbol.*;
import ghidra.program.util.DefaultLanguageService;

public class SymbolPathExternalLibraryTest extends AbstractGenericTest {
	private final List<ProgramDB> programs = new ArrayList<>();

	@After
	public void tearDown() {
		for (ProgramDB program : programs) {
			program.release(this);
		}
	}

	@Test
	public void testNestedExternalSymbolExcludesLibrary() throws Exception {
		Symbol symbol = externalSymbol(program(), "demo", "Widget");
		assertEquals("demo::Widget", new SymbolPath(symbol, true).getPath());
	}

	@Test
	public void testDeepExternalSymbolExcludesLibrary() throws Exception {
		Symbol symbol = externalSymbol(program(), "demo", "inner", "Widget");
		assertEquals("demo::inner::Widget", new SymbolPath(symbol, true).getPath());
	}

	@Test
	public void testExcludedPathFindsCorrespondingSymbolInExternalProgram() throws Exception {
		ProgramDB source = program();
		Symbol external = externalSymbol(source, "demo", "Widget");
		ExternalLocation location = source.getExternalManager().getExternalLocation(external);
		assertNotNull(location);
		assertNull(location.getOriginalImportedName());
		ProgramDB destination = program();
		Symbol expected;
		try (Transaction tx = destination.openTransaction("Create external program symbol")) {
			Namespace namespace = destination.getSymbolTable().createNameSpace(null, "demo",
				SourceType.USER_DEFINED);
			Address address = destination.getAddressFactory().getDefaultAddressSpace().getAddress(0x1000);
			destination.getMemory().createUninitializedBlock("code", address, 1, false);
			expected = destination.getSymbolTable().createLabel(address, "Widget", namespace,
				SourceType.USER_DEFINED);
		}
		// This is the actual public lookup pipeline used by GoToHelper for an external symbol
		// without an original imported name.
		assertEquals(List.of(expected), NamespaceUtils.getSymbols(new SymbolPath(external, true),
			destination));
	}

	@Test
	public void testLibraryRetainedWhenExclusionDisabled() throws Exception {
		Symbol symbol = externalSymbol(program(), "demo", "Widget");
		assertEquals("library::demo::Widget", new SymbolPath(symbol, false).getPath());
	}

	@Test
	public void testDirectExternalSymbolExcludesLibraryControl() throws Exception {
		Symbol symbol = externalSymbol(program(), "Widget");
		assertEquals("Widget", new SymbolPath(symbol, true).getPath());
	}

	@Test
	public void testGlobalSymbolUnaffectedControl() throws Exception {
		ProgramDB program = program();
		Symbol symbol;
		try (Transaction tx = program.openTransaction("Create global symbol")) {
			Address address = program.getAddressFactory().getDefaultAddressSpace().getAddress(0x1000);
			symbol = program.getSymbolTable().createLabel(address, "Widget", SourceType.USER_DEFINED);
		}
		assertEquals("Widget", new SymbolPath(symbol, true).getPath());
		assertEquals("Widget", new SymbolPath(symbol, false).getPath());
	}

	private Symbol externalSymbol(ProgramDB program, String... names) throws Exception {
		try (Transaction tx = program.openTransaction("Create actual external namespace")) {
			Namespace namespace = program.getExternalManager().addExternalLibraryName("library",
				SourceType.USER_DEFINED);
			for (int i = 0; i < names.length - 1; i++) {
				namespace = program.getSymbolTable().createNameSpace(namespace, names[i],
					SourceType.USER_DEFINED);
			}
			return program.getExternalManager().addExtLocation(namespace, names[names.length - 1],
				null, SourceType.USER_DEFINED).getSymbol();
		}
	}

	private ProgramDB program() throws Exception {
		Language language = DefaultLanguageService.getLanguageService()
			.getLanguage(new LanguageID("Toy:LE:32:default"));
		ProgramDB program = new ProgramDB("External symbol path", language,
			language.getDefaultCompilerSpec(), this);
		programs.add(program);
		return program;
	}
}
