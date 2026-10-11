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
package utilities.util;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;
import java.util.jar.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import generic.jar.ResourceFile;

public class ResourceFileReadTest {
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void testReadsCompleteDeflatedResource() throws Exception {
		byte[] bytes = payload();
		ResourceFile resource = createResource(bytes);
		assertArrayEquals(bytes, FileUtilities.getBytesFromFile(resource));
	}

	@Test
	public void testReadsDeflatedResourceRange() throws Exception {
		byte[] bytes = payload();
		ResourceFile resource = createResource(bytes);
		assertArrayEquals(Arrays.copyOfRange(bytes, 4096, 53248),
			FileUtilities.getBytesFromFile(resource, 4096, 49152));
	}

	@Test
	public void testSmallDeflatedRangePreservesOffset() throws Exception {
		byte[] bytes = payload();
		ResourceFile resource = createResource(bytes);
		assertArrayEquals(Arrays.copyOfRange(bytes, 4096, 4160),
			FileUtilities.getBytesFromFile(resource, 4096, 64));
	}

	@Test
	public void testRealFileCompleteReadPreservesBytes() throws Exception {
		byte[] bytes = payload();
		File file = temporaryFolder.newFile();
		Files.write(file.toPath(), bytes);
		assertArrayEquals(bytes, FileUtilities.getBytesFromFile(file));
	}

	@Test
	public void testDeflatedResourcePrematureEofStillFails() throws Exception {
		ResourceFile resource = createResource(new byte[] { 1, 2, 3 });
		try {
			FileUtilities.getBytesFromFile(resource, 0, 4);
			fail("Short resource must still fail the requested exact-length read");
		}
		catch (IOException expected) {
			assertEquals("Did not read expected number of bytes! Expected 4, but read 3",
				expected.getMessage());
		}
	}

	private byte[] payload() {
		byte[] bytes = new byte[65536];
		new Random(4271961).nextBytes(bytes);
		return bytes;
	}

	private ResourceFile createResource(byte[] bytes) throws Exception {
		File jar = temporaryFolder.newFile("resource-" + temporaryFolder.getRoot().list().length + ".jar");
		try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar))) {
			JarEntry entry = new JarEntry("payload.bin");
			entry.setMethod(JarEntry.DEFLATED);
			out.putNextEntry(entry);
			out.write(bytes);
			out.closeEntry();
		}
		return new ResourceFile(ResourceFile.openJarResourceFile(jar, null), "payload.bin");
	}
}
