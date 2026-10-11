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
package ghidra.file.formats.ext4;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.junit.Test;

import ghidra.app.util.bin.ByteArrayProvider;
import ghidra.app.util.bin.ByteProvider;

public class Ext4UnwrittenExtentTest {
	@Test
	public void testUnwrittenExtentLength() throws Exception {
		assertEquals(1, extent(0x8001).getExtentBlockCount());
	}

	@Test
	public void testUnwrittenBlocksReadAsZero() throws Exception {
		assertArrayEquals(new byte[1024], read(1024, 1024, entry(0, 0x8001, 1)));
	}

	@Test
	public void testWrittenExtentAfterUnwritten() throws Exception {
		assertArrayEquals(zeroThenWritten(),
			read(2048, 1024, entry(0, 0x8001, 1), entry(1, 1, 2)));
	}

	@Test
	public void testPartialUnwrittenExtent() throws Exception {
		assertArrayEquals(new byte[3], read(3, 1024, entry(0, 0x8001, 1)));
	}

	@Test
	public void testInitializedMaximumLength() throws Exception {
		assertEquals(32768, extent(0x8000).getExtentBlockCount());
		assertArrayEquals(writtenBlock(), read(1024, 1024, entry(0, 0x8000, 1)));
	}

	@Test
	public void testOrdinaryWrittenAndSparseHole() throws Exception {
		assertArrayEquals(zeroThenWritten(),
			read(2048, 1024, entry(1, 1, 2)));
	}

	private static byte[] writtenBlock() {
		byte[] bytes = new byte[1024];
		Arrays.fill(bytes, (byte) 9);
		return bytes;
	}

	private static byte[] zeroThenWritten() {
		byte[] bytes = new byte[2048];
		Arrays.fill(bytes, 1024, 2048, (byte) 9);
		return bytes;
	}

	private static Ext4Extent extent(int length) throws Exception {
		try (ByteArrayProvider bp = new ByteArrayProvider(entry(0, length, 1))) {
			return new Ext4Extent(bp);
		}
	}

	private static byte[] entry(int logical, int length, int physical) {
		return ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(logical)
			.putShort((short) length).putShort((short) 0).putInt(physical).array();
	}

	private static byte[] read(int length, int blockSize, byte[]... entries) throws Exception {
		// Genuine inode i_block extent header and leaf records, as consumed by
		// Ext4FileSystem.getInodeByteProvider. Nonzero backing blocks prove zero
		// results come from unwritten semantics rather than an empty fixture.
		ByteBuffer inode = ByteBuffer.allocate(60).order(ByteOrder.LITTLE_ENDIAN);
		inode.putShort((short) 0xf30a).putShort((short) entries.length);
		inode.putShort((short) 4).putShort((short) 0).putInt(0);
		for (byte[] entry : entries) {
			inode.put(entry);
		}
		byte[] backing = new byte[4096];
		Arrays.fill(backing, (byte) 9);
		try (ByteArrayProvider volume = new ByteArrayProvider(backing);
			ByteProvider file = Ext4ExtentsHelper.getByteProvider(inode.array(), volume,
				length, blockSize, null)) {
			assertEquals(length, file.length());
			return file.readBytes(0, length);
		}
	}
}
