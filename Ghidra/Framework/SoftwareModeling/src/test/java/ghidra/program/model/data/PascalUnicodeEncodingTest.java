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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import generic.test.AbstractGTest;
import ghidra.program.model.address.AddressSpace;
import ghidra.program.model.address.GenericAddressSpace;
import ghidra.program.model.mem.ByteMemBufferImpl;

public class PascalUnicodeEncodingTest extends AbstractGTest {

	@Test
	public void testAsciiHeaderCountsUtf16Units() throws Exception {
		checkUnicode("hello", "u\"hello\"");
	}

	@Test
	public void testBmpHeaderCountsUtf16Units() throws Exception {
		checkUnicode("a\u03a9z", "u\"a\\u03a9z\"");
	}

	@Test
	public void testSupplementaryHeaderCountsTwoUtf16Units() throws Exception {
		checkUnicode("a\ud83d\ude00z", "u\"a\\U0001f600z\"");
	}

	@Test
	public void testEmptyUnicodeHeaderRemainsZero() throws Exception {
		checkUnicode("", "u\"\"");
	}

	@Test
	public void testAnsi64kHeaderRemainsByteCount() throws Exception {
		for (boolean bigEndian : new boolean[] { false, true }) {
			PascalStringDataType type = new PascalStringDataType();
			byte[] expected = bigEndian ? new byte[] { 0, 3, 'a', 'b', 'c' }
					: new byte[] { 3, 0, 'a', 'b', 'c' };
			byte[] encoded = type.encodeRepresentation("\"abc\"", buffer(bigEndian, new byte[0]),
				new SettingsBuilder(), -1);
			assertArrayEquals(expected, encoded);
			assertEquals("abc", type.getValue(buffer(bigEndian, encoded), new SettingsBuilder(), encoded.length));
		}
	}

	@Test
	public void testAnsi255HeaderRemainsByteCount() throws Exception {
		PascalString255DataType type = new PascalString255DataType();
		byte[] encoded = type.encodeRepresentation("\"abc\"", buffer(false, new byte[0]),
			new SettingsBuilder(), -1);
		assertArrayEquals(new byte[] { 3, 'a', 'b', 'c' }, encoded);
		assertEquals("abc", type.getValue(buffer(false, encoded), new SettingsBuilder(), encoded.length));
	}

	private void checkUnicode(String value, String representation) throws Exception {
		for (boolean bigEndian : new boolean[] { false, true }) {
			PascalUnicodeDataType type = new PascalUnicodeDataType();
			byte[] characters = value.getBytes(bigEndian ? StandardCharsets.UTF_16BE : StandardCharsets.UTF_16LE);
			// Pascal UTF-16 counts code units; String.length counts a surrogate pair as two.
			byte[] expected = ByteBuffer.allocate(2 + characters.length)
				.order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN)
				.putShort((short) value.length()).put(characters).array();
			byte[] encoded = type.encodeRepresentation(representation, buffer(bigEndian, new byte[0]),
				new SettingsBuilder(), -1);
			assertArrayEquals(expected, encoded);
			ByteMemBufferImpl memory = buffer(bigEndian, encoded);
			assertEquals(encoded.length, type.getLength(memory, -1));
			assertEquals(value, type.getValue(memory, new SettingsBuilder(), encoded.length));
		}
	}

	private ByteMemBufferImpl buffer(boolean bigEndian, byte[] bytes) {
		GenericAddressSpace space = new GenericAddressSpace("test", 32, AddressSpace.TYPE_RAM, 1);
		return new ByteMemBufferImpl(space.getAddress(0), bytes, bigEndian);
	}
}
