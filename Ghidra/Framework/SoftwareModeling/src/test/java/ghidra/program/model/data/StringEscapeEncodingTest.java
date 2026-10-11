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

import java.nio.CharBuffer;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import generic.test.AbstractGTest;
import ghidra.program.model.address.AddressSpace;
import ghidra.program.model.address.GenericAddressSpace;
import ghidra.program.model.lang.Endian;
import ghidra.program.model.mem.ByteMemBufferImpl;

public class StringEscapeEncodingTest extends AbstractGTest {
	@Test
	public void testConsecutiveByteEscapes() throws Exception {
		checkUnicode("AB", "u\"\\x41\\x42\"");
	}

	@Test
	public void testMixedWideAndByteEscapes() throws Exception {
		checkUnicode("\u03a9A", "u\"\\u03a9\\x41\"");
	}

	@Test
	public void testSupplementaryEscapeThenLiteralAndByteEscape() throws Exception {
		checkUnicode("\ud83d\ude01zA", "u\"\\U0001f601z\\x41\"");
	}

	@Test
	public void testAsciiByteEscapes() throws Exception {
		StringDataType type = new StringDataType();
		byte[] actual = type.encodeRepresentation("\"\\x41\\x42\"", buffer(false, new byte[0]),
			new SettingsBuilder(), -1);
		assertArrayEquals("AB".getBytes(StandardCharsets.US_ASCII), actual);
		assertEquals("AB", type.getValue(buffer(false, actual), new SettingsBuilder(), actual.length));
	}

	@Test
	public void testConsecutiveFourDigitEscapes() throws Exception {
		checkUnicode("\u0001B", "u\"\\u0001\\u0042\"");
	}

	@Test
	public void testEightDigitEscapesRemainValid() throws Exception {
		checkUnicode("AB\ud83d\ude00", "u\"\\U00000041\\U00000042\\U0001f600\"");
	}

	@Test
	public void testOrdinaryRepresentationRoundTrip() throws Exception {
		for (boolean bigEndian : new boolean[] { false, true }) {
			UnicodeDataType type = new UnicodeDataType();
			byte[] original = "hello".getBytes(bigEndian ? StandardCharsets.UTF_16BE : StandardCharsets.UTF_16LE);
			String representation = type.getRepresentation(buffer(bigEndian, original), new SettingsBuilder(), original.length);
			assertArrayEquals(original, type.encodeRepresentation(representation, buffer(bigEndian, original), new SettingsBuilder(), original.length));
		}
	}

	@Test
	public void testResetReusesParser() throws Exception {
		StringRenderParser parser = new StringRenderParser('"', Endian.LITTLE, "UTF-16", false);
		assertArrayEquals("A".getBytes(StandardCharsets.UTF_16LE), bytes(parser.parse(CharBuffer.wrap("u\"\\x41\""))));
		parser.reset();
		assertArrayEquals("B".getBytes(StandardCharsets.UTF_16LE), bytes(parser.parse(CharBuffer.wrap("u\"\\x42\""))));
	}

	@Test
	public void testLiteralSupplementaryUtf16RoundTrip() throws Exception {
		for (boolean bigEndian : new boolean[] { false, true }) {
			UnicodeDataType type = new UnicodeDataType();
			byte[] original = "a\ud83d\ude00z".getBytes(bigEndian ? StandardCharsets.UTF_16BE : StandardCharsets.UTF_16LE);
			SettingsBuilder settings = new SettingsBuilder().set(RenderUnicodeSettingsDefinition.RENDER_ENUM.ALL);
			String representation = type.getRepresentation(buffer(bigEndian, original), settings, original.length);
			assertTrue(representation.contains("a\ud83d\ude00z"));
			assertArrayEquals(original, type.encodeRepresentation(representation, buffer(bigEndian, original), settings, original.length));
		}
	}

	@Test
	public void testLiteralSupplementaryUtf8RoundTrip() throws Exception {
		StringUTF8DataType type = new StringUTF8DataType();
		byte[] original = "a\ud83d\ude00z".getBytes(StandardCharsets.UTF_8);
		SettingsBuilder settings = new SettingsBuilder().set(RenderUnicodeSettingsDefinition.RENDER_ENUM.ALL);
		String representation = type.getRepresentation(buffer(false, original), settings, original.length);
		assertTrue(representation.contains("a\ud83d\ude00z"));
		assertArrayEquals(original, type.encodeRepresentation(representation, buffer(false, original), settings, original.length));
	}

	@Test
	public void testEscapedSurrogatePair() throws Exception {
		checkUnicode("a\ud83d\ude00z", "u\"a\\ud83d\\ude00z\"");
	}

	@Test
	public void testLiteralPairAcrossInputChunks() throws Exception {
		StringRenderParser parser = new StringRenderParser('"', Endian.LITTLE, "UTF-16", false);
		ByteBuffer output = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
		parser.parse(output, CharBuffer.wrap("u\"a\ud83d"));
		parser.parse(output, CharBuffer.wrap("\ude00z\""));
		parser.finish(output);
		output.flip();
		assertArrayEquals("a\ud83d\ude00z".getBytes(StandardCharsets.UTF_16LE), bytes(output));
	}

	@Test
	public void testOrphanHighSurrogateBeforeQuoteRejected() throws Exception {
		assertMalformed("u\"\ud83d\"");
	}

	@Test
	public void testHighSurrogateBeforeOrdinaryCharacterRejected() throws Exception {
		assertMalformed("u\"\ud83dA\"");
	}

	@Test
	public void testOrphanLowSurrogateRejected() throws Exception {
		assertMalformed("u\"\ude00\"");
	}

	@Test
	public void testFinishRejectsIncompleteSurrogateWithoutSignatureChange() throws Exception {
		StringRenderParser parser = new StringRenderParser('"', Endian.LITTLE, "UTF-16", false);
		ByteBuffer output = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
		parser.parse(output, CharBuffer.wrap("u\"\ud83d"));
		try {
			parser.finish(output);
			fail("Incomplete literal must be rejected");
		}
		catch (StringRenderParser.StringParseException expected) {
			// The public finish method retains its existing exception contract.
		}
	}

	@Test
	public void testResetDiscardsPendingSurrogate() throws Exception {
		StringRenderParser parser = new StringRenderParser('"', Endian.LITTLE, "UTF-16", false);
		parser.parse(ByteBuffer.allocate(16), CharBuffer.wrap("u\"\ud83d"));
		parser.reset();
		assertArrayEquals("B".getBytes(StandardCharsets.UTF_16LE), bytes(parser.parse(CharBuffer.wrap("u\"B\""))));
	}

	private void assertMalformed(String representation) throws Exception {
		try {
			new UnicodeDataType().encodeRepresentation(representation, buffer(false, new byte[0]), new SettingsBuilder(), -1);
			fail("Malformed surrogate sequence must be rejected");
		}
		catch (DataTypeEncodeException expected) {
			// Actual public datatype encoder reports the malformed representation.
		}
	}

	private void checkUnicode(String value, String representation) throws Exception {
		for (boolean bigEndian : new boolean[] { false, true }) {
			UnicodeDataType type = new UnicodeDataType();
			byte[] expected = value.getBytes(bigEndian ? StandardCharsets.UTF_16BE : StandardCharsets.UTF_16LE);
			byte[] encoded = type.encodeRepresentation(representation, buffer(bigEndian, new byte[0]), new SettingsBuilder(), -1);
			assertArrayEquals(expected, encoded);
			assertEquals(value, type.getValue(buffer(bigEndian, encoded), new SettingsBuilder(), encoded.length));
		}
	}

	private byte[] bytes(ByteBuffer buffer) {
		byte[] result = new byte[buffer.remaining()];
		buffer.get(result);
		return result;
	}

	private ByteMemBufferImpl buffer(boolean bigEndian, byte[] bytes) {
		GenericAddressSpace space = new GenericAddressSpace("test", 32, AddressSpace.TYPE_RAM, 1);
		return new ByteMemBufferImpl(space.getAddress(0), bytes, bigEndian);
	}
}
