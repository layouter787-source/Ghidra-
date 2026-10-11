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
import ghidra.program.model.data.RGB32EncodingSettingsDefinition.RGB32Encoding;
import ghidra.program.model.lang.Language;
import ghidra.program.model.lang.LanguageID;
import ghidra.program.model.listing.Data;
import ghidra.program.util.DefaultLanguageService;
import ghidra.util.task.TaskMonitor;

public class RGB32ListingValueTest extends AbstractGenericTest {
	@Test
	public void testRgbaTypedefLittleEndian() throws Exception {
		check(false, RGB32Encoding.RGBA_8888, 0x12345678, true);
	}

	@Test
	public void testBgraTypedefBigEndian() throws Exception {
		check(true, RGB32Encoding.BGRA_8888, 0x56341278, true);
	}

	@Test
	public void testAbgrTypedefLittleEndian() throws Exception {
		check(false, RGB32Encoding.ABGR_8888, 0x78563412, true);
	}

	@Test
	public void testDefaultArgbControl() throws Exception {
		check(true, RGB32Encoding.ARGB_8888, 0x78123456, false);
	}

	private void check(boolean bigEndian, RGB32Encoding encoding, int pixel, boolean typedef)
			throws Exception {
		Language language = DefaultLanguageService.getLanguageService()
			.getLanguage(new LanguageID("Toy:" + (bigEndian ? "BE" : "LE") + ":32:default"));
		ProgramDB program = new ProgramDB("RGB32 listing", language,
			language.getDefaultCompilerSpec(), this);
		try {
			byte[] bytes = ByteBuffer.allocate(4)
				.order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN)
				.putInt(pixel).array();
			Data data;
			try (Transaction tx = program.openTransaction("Create alternate RGB32 listing")) {
				Address address = program.getAddressFactory().getDefaultAddressSpace().getAddress(0x1000);
				program.getMemory().createInitializedBlock("color", address,
					new ByteArrayInputStream(bytes), bytes.length, TaskMonitor.DUMMY, false);
				DataType type = typedef ? RGB32ColorDataType.createRGB32Typedef(encoding)
						: RGB32ColorDataType.datatype;
				data = program.getListing().createData(address, type);
			}
			assertEquals(4, data.getLength());
			assertArrayEquals(bytes, data.getBytes());
			ColorIcon icon = (ColorIcon) data.getValue();
			assertNotNull(icon);
			assertEquals(0x78123456, icon.getRGBValue());
			String representation = data.getDefaultValueRepresentation();
			assertTrue(representation, representation.startsWith(encoding.name() + " "));
			assertTrue(representation, representation.contains("G:34h"));
		}
		finally {
			program.release(this);
		}
	}
}
