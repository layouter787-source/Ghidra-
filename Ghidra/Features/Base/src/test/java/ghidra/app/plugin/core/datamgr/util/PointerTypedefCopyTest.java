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
package ghidra.app.plugin.core.datamgr.util;

import static org.junit.Assert.*;

import org.junit.Test;

import generic.test.AbstractGenericTest;
import ghidra.program.database.ProgramDB;
import ghidra.program.model.data.*;
import ghidra.program.model.lang.Language;
import ghidra.program.model.lang.LanguageID;
import ghidra.program.util.DefaultLanguageService;

public class PointerTypedefCopyTest extends AbstractGenericTest {
	@Test
	public void testDynamicCopyUsesTargetManager() throws Exception {
		check(false, false, false);
	}

	@Test
	public void testDynamicCloneUsesTargetManager() throws Exception {
		check(true, false, false);
	}

	@Test
	public void testFixedWidthCopyKeepsSettingsInTargetManager() throws Exception {
		check(false, true, false);
	}

	@Test
	public void testNamedTypeUtilityCopiesToTargetManager() throws Exception {
		check(false, false, true);
	}

	@Test
	public void testCloneInSameManagerKeepsIdentity() throws Exception {
		ProgramDB program = program(32);
		try {
			PointerTypedef type = new PointerTypedef(null, CharDataType.dataType, -1,
				program.getDataTypeManager(), 8L);
			assertSame(type, type.clone(program.getDataTypeManager()));
			assertEquals(4, type.getLength());
		}
		finally {
			program.release(this);
		}
	}

	@Test
	public void testCopyInSameManagerCreatesDistinctType() throws Exception {
		ProgramDB program = program(32);
		try {
			PointerTypedef type = new PointerTypedef(null, CharDataType.dataType, -1,
				program.getDataTypeManager(), 8L);
			PointerTypedef copied = type.copy(program.getDataTypeManager());
			assertNotSame(type, copied);
			assertSame(program.getDataTypeManager(), copied.getDataTypeManager());
			assertEquals(type.getName(), copied.getName());
			assertTrue(type.isEquivalent(copied));
			assertEquals(4, copied.getLength());
		}
		finally {
			program.release(this);
		}
	}

	private void check(boolean clone, boolean fixed, boolean utility) throws Exception {
		ProgramDB source = program(32);
		try {
			ProgramDB target = program(64);
			try {
				DataTypeManager sourceManager = source.getDataTypeManager();
				DataTypeManager targetManager = target.getDataTypeManager();
				assertEquals(4, sourceManager.getDataOrganization().getPointerSize());
				assertEquals(8, targetManager.getDataOrganization().getPointerSize());
				// Use the address-space pointer typedef produced for typed debugger registers.
				PointerTypedef type = new PointerTypedef(utility ? "RegisterPointer" : null,
					CharDataType.dataType, fixed ? 4 : -1, sourceManager,
					source.getAddressFactory().getDefaultAddressSpace());
				ComponentOffsetSettingsDefinition.DEF.setValue(type.getDefaultSettings(), 8);
				DataType result = utility ? DataTypeUtils.copyToNamedBaseDataType(type, targetManager)
						: clone ? type.clone(targetManager) : type.copy(targetManager);
				assertNotSame(type, result);
				assertSame(targetManager, result.getDataTypeManager());
				TypeDef copied = (TypeDef) result;
				assertSame(targetManager, copied.getDataType().getDataTypeManager());
				assertEquals(fixed ? 4 : 8, copied.getLength());
				assertEquals(!fixed, copied.hasLanguageDependantLength());
				assertEquals(type.isAutoNamed(), copied.isAutoNamed());
				assertEquals(8, ComponentOffsetSettingsDefinition.DEF.getValue(copied.getDefaultSettings()));
				assertEquals(source.getAddressFactory().getDefaultAddressSpace().getName(),
					AddressSpaceSettingsDefinition.DEF.getValue(copied.getDefaultSettings()));
				assertEquals(4, type.getLength());
				if (utility) {
					assertEquals("RegisterPointer", copied.getName());
				}
				else {
					assertTrue(copied.getName().contains("space("));
					assertTrue(copied.getName().contains("offset(0x8)"));
				}
			}
			finally {
				target.release(this);
			}
		}
		finally {
			source.release(this);
		}
	}

	private ProgramDB program(int bits) throws Exception {
		Language language = DefaultLanguageService.getLanguageService()
			.getLanguage(new LanguageID("Toy:BE:" + bits + ":default"));
		return new ProgramDB("Pointer copy " + bits, language,
			language.getDefaultCompilerSpec(), this);
	}
}
