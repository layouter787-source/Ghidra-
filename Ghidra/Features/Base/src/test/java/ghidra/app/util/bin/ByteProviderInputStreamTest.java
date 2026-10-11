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
package ghidra.app.util.bin;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

public class ByteProviderInputStreamTest {

	private InputStream stream(long start) throws IOException {
		return new ByteProviderInputStream(new ByteArrayProvider(new byte[] { 1, 2, 3 }), start);
	}

	@Test
	public void testZeroLengthReadAtEnd() throws IOException {
		try (InputStream in = stream(3)) {
			assertEquals(0, in.read(new byte[2], 2, 0));
			assertEquals(0, in.read(new byte[0]));
			assertEquals(-1, in.read());
		}
	}

	@Test
	public void testZeroLengthReadFromEmptyProvider() throws IOException {
		try (InputStream in = new ByteProviderInputStream(new ByteArrayProvider(new byte[0]))) {
			assertEquals(0, in.read(new byte[0]));
			assertEquals(-1, in.read());
		}
	}

	@Test
	public void testSkipDoesNotOverflow() throws IOException {
		try (InputStream in = stream(1)) {
			assertEquals(2, in.skip(Long.MAX_VALUE));
			assertEquals(0, in.available());
			assertEquals(-1, in.read());
			assertEquals(0, in.skip(Long.MAX_VALUE));
		}
	}

	@Test
	public void testReadBoundsCheckedAtEnd() throws IOException {
		try (InputStream in = stream(3)) {
			byte[] buffer = new byte[2];
			assertThrows(IndexOutOfBoundsException.class, () -> in.read(buffer, -1, 0));
			assertThrows(IndexOutOfBoundsException.class, () -> in.read(buffer, 3, 0));
			assertThrows(IndexOutOfBoundsException.class, () -> in.read(buffer, 0, -1));
			assertThrows(IndexOutOfBoundsException.class, () -> in.read(buffer, 0, 3));
		}
	}

	@Test
	public void testReadBoundsCheckedBeforeLengthClipping() throws IOException {
		try (InputStream in = stream(2)) {
			assertThrows(IndexOutOfBoundsException.class, () -> in.read(new byte[2], 0, 3));
			assertEquals(3, in.read());
		}
	}

	@Test
	public void testNullBufferCheckedAtEnd() throws IOException {
		try (InputStream in = stream(3)) {
			assertThrows(NullPointerException.class, () -> in.read(null, 0, 0));
		}
	}

	@Test
	public void testPartialReadAndBufferOffset() throws IOException {
		try (InputStream in = stream(1)) {
			byte[] buffer = new byte[] { 9, 9, 9, 9 };
			assertEquals(0, in.read(buffer, 1, 0));
			assertEquals(2, in.read(buffer, 1, 3));
			assertArrayEquals(new byte[] { 9, 2, 3, 9 }, buffer);
			assertEquals(-1, in.read(buffer));
		}
	}

	@Test
	public void testNonPositiveSkipAndOrdinarySkip() throws IOException {
		try (InputStream in = stream(0)) {
			assertEquals(0, in.skip(0));
			assertEquals(0, in.skip(-1));
			assertEquals(1, in.read());
			assertEquals(1, in.skip(1));
			assertEquals(3, in.read());
		}
	}

	@Test
	public void testMarkResetAfterSkip() throws IOException {
		try (InputStream in = stream(0)) {
			assertEquals(1, in.read());
			in.mark(2);
			assertEquals(1, in.skip(1));
			assertEquals(3, in.read());
			in.reset();
			assertEquals(2, in.read());
		}
	}
}
