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
package ghidra.file.formats.sparseimage;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

import org.junit.Test;

import ghidra.app.util.bin.ByteArrayProvider;
import ghidra.util.task.TaskMonitor;

public class SparseImageFillTest {
	private static final byte[] PATTERN = { 0x01, 0x23, 0x45, 0x67 };

	@Test
	public void testFillPreservesStoredByteOrder() throws Exception {
		assertArrayEquals(repeat(PATTERN, 12), decode(image(PATTERN, 3, false, false)));
	}

	@Test
	public void testFillWithValidCrc() throws Exception {
		assertArrayEquals(repeat(PATTERN, 20), decode(image(PATTERN, 5, true, false)));
	}

	@Test
	public void testFillAcrossBufferBoundary() throws Exception {
		int blocks = 1024 * 1024 / 4 + 1;
		assertArrayEquals(repeat(PATTERN, blocks * 4),
			decode(image(PATTERN, blocks, false, false)));
	}

	@Test
	public void testRawPayloadUnchanged() throws Exception {
		assertArrayEquals(PATTERN, decode(image(PATTERN, 1, false, true)));
	}

	@Test
	public void testSymmetricFill() throws Exception {
		byte[] pattern = { 0x12, 0x34, 0x34, 0x12 };
		assertArrayEquals(repeat(pattern, 16), decode(image(pattern, 4, true, false)));
	}

	@Test
	public void testZeroFill() throws Exception {
		assertArrayEquals(new byte[8], decode(image(new byte[4], 2, true, false)));
	}

	@Test
	public void testSkippedBlocksWithValidCrc() throws Exception {
		assertArrayEquals(new byte[4], decode(skipImage(1, true)));
	}

	@Test
	public void testSkippedBlocksAtBufferBoundary() throws Exception {
		assertArrayEquals(new byte[1024 * 1024], decode(skipImage(1024 * 1024 / 4, false)));
	}

	@Test
	public void testSkippedBlocksAcrossBufferBoundaryWithCrc() throws Exception {
		int blocks = 1024 * 1024 / 4 + 1;
		assertArrayEquals(new byte[blocks * 4], decode(skipImage(blocks, true)));
	}

	private static byte[] skipImage(int blocks, boolean crc) {
		ByteBuffer image = ByteBuffer.allocate(28 + 12 + (crc ? 16 : 0))
			.order(ByteOrder.LITTLE_ENDIAN);
		image.putInt(0xed26ff3a).putShort((short) 1).putShort((short) 0);
		image.putShort((short) 28).putShort((short) 12).putInt(4);
		image.putInt(blocks).putInt(crc ? 2 : 1).putInt(0);
		image.putShort((short) 0xcac3).putShort((short) 0).putInt(blocks).putInt(12);
		if (crc) {
			CRC32 checksum = new CRC32();
			checksum.update(new byte[blocks * 4]);
			image.putShort((short) 0xcac4).putShort((short) 0);
			image.putInt(0).putInt(16).putInt((int) checksum.getValue());
		}
		return image.array();
	}

	private static byte[] decode(byte[] image) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (ByteArrayProvider provider = new ByteArrayProvider(image)) {
			new SparseImageDecompressor(provider, output).decompress(TaskMonitor.DUMMY);
		}
		return output.toByteArray();
	}

	// AOSP sparse_format.h: 28-byte LE header, 12-byte chunk header, and four
	// stored bytes of fill data. Build the oracle from those bytes, without
	// interpreting the pattern as an integer.
	private static byte[] image(byte[] pattern, int blocks, boolean crc, boolean raw) {
		ByteBuffer image = ByteBuffer.allocate(28 + 12 + 4 + (crc ? 16 : 0))
			.order(ByteOrder.LITTLE_ENDIAN);
		image.putInt(0xed26ff3a).putShort((short) 1).putShort((short) 0);
		image.putShort((short) 28).putShort((short) 12).putInt(4);
		image.putInt(blocks).putInt(crc ? 2 : 1).putInt(0);
		image.putShort((short) (raw ? 0xcac1 : 0xcac2)).putShort((short) 0);
		image.putInt(blocks).putInt(16).put(pattern);
		if (crc) {
			CRC32 checksum = new CRC32();
			checksum.update(repeat(pattern, blocks * 4));
			image.putShort((short) 0xcac4).putShort((short) 0);
			image.putInt(0).putInt(16).putInt((int) checksum.getValue());
		}
		return image.array();
	}

	private static byte[] repeat(byte[] pattern, int size) {
		byte[] bytes = new byte[size];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = pattern[i % pattern.length];
		}
		return bytes;
	}
}
