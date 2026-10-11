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
package ghidra.program.model.data;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;

import org.junit.Test;

import db.Transaction;
import generic.test.AbstractGenericTest;
import ghidra.program.database.ProgramDB;
import ghidra.program.model.address.Address;
import ghidra.program.model.lang.Language;
import ghidra.program.model.lang.LanguageID;
import ghidra.program.model.listing.Data;
import ghidra.program.util.DefaultLanguageService;
import ghidra.util.task.TaskMonitor;

public class BooleanListingEncodingTest extends AbstractGenericTest {
	@Test
	public void testRenderedFalseCanBePatched() throws Exception {
		check(0, false);
	}

	@Test
	public void testRenderedTrueCanBePatched() throws Exception {
		check(1, false);
	}

	@Test
	public void testNonzeroTrueUsesCanonicalEncoding() throws Exception {
		check(0xff, false);
	}

	@Test
	public void testTypedFalseCanBeEncoded() throws Exception {
		check(0, true);
	}

	@Test
	public void testTypedTrueCanBeEncoded() throws Exception {
		check(1, true);
	}

	@Test
	public void testExistingNumericHexEncodingPreserved() throws Exception {
		try (Fixture f = new Fixture(0)) {
			assertArrayEquals(new byte[] { (byte) 0xff },
				BooleanDataType.dataType.encodeRepresentation("FFh", f.data, f.data, 1));
		}
	}

	@Test
	public void testInvalidRepresentationRejected() throws Exception {
		try (Fixture f = new Fixture(0)) {
			try {
				BooleanDataType.dataType.encodeRepresentation("not-a-boolean", f.data, f.data, 1);
				fail("Invalid representation must be rejected");
			}
			catch (DataTypeEncodeException expected) {
				// Existing public checked exception contract.
			}
		}
	}

	@Test
	public void testLengthMismatchRejected() throws Exception {
		try (Fixture f = new Fixture(0)) {
			try {
				BooleanDataType.dataType.encodeRepresentation("TRUE", f.data, f.data, 2);
				fail("A boolean must still occupy exactly one byte");
			}
			catch (DataTypeEncodeException expected) {
				// Boolean representation uses the same fixed-length contract.
			}
		}
	}

	private void check(int originalByte, boolean typed) throws Exception {
		try (Fixture f = new Fixture(originalByte)) {
			boolean expected = originalByte != 0;
			assertTrue(f.data.getBaseDataType().isEncodable());
			assertEquals(expected, f.data.getValue());
			String representation = f.data.getDefaultValueRepresentation();
			assertEquals(expected ? "TRUE" : "FALSE", representation);
			byte[] encoded = typed
					? BooleanDataType.dataType.encodeValue(f.data.getValue(), f.data, f.data, 1)
					: BooleanDataType.dataType.encodeRepresentation(representation, f.data, f.data, 1);
			assertArrayEquals(new byte[] { (byte) (expected ? 1 : 0) }, encoded);
			try (Transaction tx = f.program.openTransaction("Patch boolean data")) {
				f.program.getMemory().setBytes(f.address, encoded);
			}
			assertEquals(expected, f.data.getValue());
			assertEquals(representation, f.data.getDefaultValueRepresentation());
		}
	}

	private class Fixture implements AutoCloseable {
		final ProgramDB program;
		final Address address;
		final Data data;

		Fixture(int value) throws Exception {
			Language language = DefaultLanguageService.getLanguageService()
				.getLanguage(new LanguageID("Toy:LE:32:default"));
			program = new ProgramDB("Boolean listing encoding", language,
				language.getDefaultCompilerSpec(), this);
			try {
				address = program.getAddressFactory().getDefaultAddressSpace().getAddress(0x1000);
				try (Transaction tx = program.openTransaction("Create boolean data")) {
					program.getMemory().createInitializedBlock("data", address,
						new ByteArrayInputStream(new byte[] { (byte) value }), 1,
						TaskMonitor.DUMMY, false);
					data = program.getListing().createData(address, BooleanDataType.dataType);
				}
			}
			catch (Exception | Error e) {
				program.release(this);
				throw e;
			}
		}

		@Override
		public void close() {
			program.release(this);
		}
	}
}
