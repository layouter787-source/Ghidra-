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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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

public class FileTimeListingValueTest extends AbstractGenericTest {
	@Test
	public void testOneTickLittleEndian() throws Exception {
		check(false, 1, "1601-01-01 00:00:00.0000001 UTC");
	}

	@Test
	public void testOneTickBigEndian() throws Exception {
		check(true, 1, "1601-01-01 00:00:00.0000001 UTC");
	}

	@Test
	public void testLastTickBeforeNextSecond() throws Exception {
		check(false, 9_999_999, "1601-01-01 00:00:00.9999999 UTC");
	}

	@Test
	public void testFirstTickAfterNextSecond() throws Exception {
		check(true, 10_000_001, "1601-01-01 00:00:01.0000001 UTC");
	}

	@Test
	public void testRawTicksValueControl() throws Exception {
		withData(false, 123_456_789, false, data -> {
			assertEquals(Long.valueOf(123_456_789), data.getValue());
		});
	}

	@Test
	public void testUnreadableTimestampControl() throws Exception {
		withData(true, 0, true, data -> {
			assertNull(data.getValue());
			assertEquals("", data.getDefaultValueRepresentation());
		});
	}

	private void check(boolean bigEndian, long ticks, String expected) throws Exception {
		withData(bigEndian, ticks, false, data -> {
			assertEquals(Long.valueOf(ticks), data.getValue());
			assertEquals(expected, data.getDefaultValueRepresentation());
		});
	}

	private interface DataCheck {
		void check(Data data) throws Exception;
	}

	private void withData(boolean bigEndian, long ticks, boolean unreadable, DataCheck check)
			throws Exception {
		Language language = DefaultLanguageService.getLanguageService()
			.getLanguage(new LanguageID("Toy:" + (bigEndian ? "BE" : "LE") + ":32:default"));
		ProgramDB program = new ProgramDB("FILETIME listing", language,
			language.getDefaultCompilerSpec(), this);
		try {
			byte[] bytes = ByteBuffer.allocate(8)
				.order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN)
				.putLong(ticks).array();
			Data data;
			try (Transaction tx = program.openTransaction("Create actual FILETIME listing")) {
				Address address = program.getAddressFactory().getDefaultAddressSpace()
					.getAddress(0x1000);
				if (unreadable) {
					program.getMemory().createUninitializedBlock("timestamp", address, 8, false);
				}
				else {
					program.getMemory().createInitializedBlock("timestamp", address,
						new ByteArrayInputStream(bytes), bytes.length, TaskMonitor.DUMMY, false);
				}
				data = program.getListing().createData(address, new FileTimeDataType());
			}
			assertEquals(8, data.getLength());
			if (!unreadable) {
				assertArrayEquals(bytes, data.getBytes());
			}
			check.check(data);
		}
		finally {
			program.release(this);
		}
	}
}
