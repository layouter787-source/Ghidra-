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
package ghidra.program.model.listing;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import generic.test.AbstractGenericTest;
import ghidra.program.database.ProgramDB;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.*;
import ghidra.program.model.lang.*;
import ghidra.program.model.pcode.Varnode;
import ghidra.program.model.symbol.SourceType;
import ghidra.program.util.DefaultLanguageService;
import ghidra.util.exception.InvalidInputException;

public class VariableStorageResizeTest extends AbstractGenericTest {

	private ProgramDB program;

	@Before
	public void setUp() throws Exception {
		Language language = DefaultLanguageService.getLanguageService()
			.getLanguage(new LanguageID("Toy:BE:64:default"));
		program = new ProgramDB("Test", language, language.getDefaultCompilerSpec(), this);
	}

	@After
	public void tearDown() {
		program.release(this);
	}

	@Test
	public void testGrowStackVariable() throws Exception {
		LocalVariableImpl variable = new LocalVariableImpl("local", new DWordDataType(), -16, program);
		VariableStorage original = variable.getVariableStorage();
		variable.setDataType(new QWordDataType(), SourceType.USER_DEFINED);
		assertEquals(8, variable.getLength());
		assertEquals(8, variable.getVariableStorage().size());
		assertEquals(-16, variable.getStackOffset());
		assertEquals(4, original.size());
		assertEquals(4, original.getVarnodes()[0].getSize());
	}

	@Test
	public void testGrowBigEndianMemoryVariable() throws Exception {
		Address originalAddress = program.getAddressFactory().getDefaultAddressSpace().getAddress(0x1004);
		LocalVariableImpl variable = new LocalVariableImpl("local", 0, new DWordDataType(), originalAddress, program);
		variable.setDataType(new QWordDataType(), SourceType.USER_DEFINED);
		Varnode[] storage = variable.getVariableStorage().getVarnodes();
		assertEquals(1, storage.length);
		assertEquals(originalAddress.subtract(4), storage[0].getAddress());
		assertEquals(8, storage[0].getSize());
	}

	@Test
	public void testGrowLastComponentOfCompoundStorage() throws Exception {
		Address registerAddress = program.getRegister("r0l").getAddress();
		Address stackAddress = program.getAddressFactory().getStackSpace().getAddress(-16);
		VariableStorage original = new VariableStorage(program,
			new Varnode(registerAddress, 4), new Varnode(stackAddress, 4));
		LocalVariableImpl variable = new LocalVariableImpl("local", 0,
			new ArrayDataType(new ByteDataType(), 8, 1), original, program);
		variable.setDataType(new ArrayDataType(new ByteDataType(), 12, 1), SourceType.USER_DEFINED);
		Varnode[] storage = variable.getVariableStorage().getVarnodes();
		assertEquals(2, storage.length);
		assertEquals(registerAddress, storage[0].getAddress());
		assertEquals(4, storage[0].getSize());
		assertEquals(stackAddress, storage[1].getAddress());
		assertEquals(8, storage[1].getSize());
		assertEquals(12, variable.getVariableStorage().size());
		assertEquals(4, original.getVarnodes()[1].getSize());
	}

	@Test
	public void testShrinkStackVariable() throws Exception {
		LocalVariableImpl variable = new LocalVariableImpl("local", new QWordDataType(), -16, program);
		variable.setDataType(new DWordDataType(), SourceType.USER_DEFINED);
		assertEquals(4, variable.getVariableStorage().size());
		assertEquals(-16, variable.getStackOffset());
	}

	@Test
	public void testSameSizePreservesStorage() throws Exception {
		LocalVariableImpl variable = new LocalVariableImpl("local", new DWordDataType(), -16, program);
		VariableStorage original = variable.getVariableStorage();
		variable.setDataType(new FloatDataType(), SourceType.USER_DEFINED);
		assertSame(original, variable.getVariableStorage());
		assertEquals(4, variable.getLength());
	}

	@Test
	public void testGrowthAcrossStackBoundaryRemainsRejected() throws Exception {
		LocalVariableImpl variable = new LocalVariableImpl("local", new DWordDataType(), -4, program);
		VariableStorage original = variable.getVariableStorage();
		try {
			variable.setDataType(new QWordDataType(), SourceType.USER_DEFINED);
			fail("Growth across stack offset zero must be rejected");
		}
		catch (InvalidInputException expected) {
			assertSame(original, variable.getVariableStorage());
			assertEquals(4, variable.getLength());
		}
	}

	@Test
	public void testGrowBigEndianRegisterUsesParentAddress() throws Exception {
		Register lowHalf = program.getRegister("r0l");
		Register parent = program.getRegister("r0");
		LocalVariableImpl variable = new LocalVariableImpl("local", 0,
			new DWordDataType(), lowHalf, program);
		VariableStorage original = variable.getVariableStorage();
		assertEquals(lowHalf.getAddress(), original.getVarnodes()[0].getAddress());
		variable.setDataType(new QWordDataType(), SourceType.USER_DEFINED);
		Varnode[] storage = variable.getVariableStorage().getVarnodes();
		assertEquals(1, storage.length);
		assertEquals(parent.getAddress(), storage[0].getAddress());
		assertEquals(parent.getMinimumByteSize(), storage[0].getSize());
		assertEquals(lowHalf.getAddress(), original.getVarnodes()[0].getAddress());
		assertEquals(4, original.size());
	}

	@Test
	public void testGrowthBeyondRegisterParentRemainsRejected() throws Exception {
		LocalVariableImpl variable = new LocalVariableImpl("local", 0,
			new QWordDataType(), program.getRegister("r0"), program);
		VariableStorage original = variable.getVariableStorage();
		try {
			variable.setDataType(new ArrayDataType(new ByteDataType(), 16, 1),
				SourceType.USER_DEFINED);
			fail("Growth beyond the parent register must be rejected");
		}
		catch (InvalidInputException expected) {
			assertSame(original, variable.getVariableStorage());
			assertEquals(8, variable.getLength());
		}
	}

}
