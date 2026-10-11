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
package ghidra.file.formats.gzip;

import static org.junit.Assert.*;
import static ghidra.formats.gfilesystem.fileinfo.FileAttributeType.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import generic.test.AbstractGenericTest;
import ghidra.app.util.bin.ByteArrayProvider;
import ghidra.app.util.bin.ByteProvider;
import ghidra.formats.gfilesystem.*;
import ghidra.formats.gfilesystem.fileinfo.FileAttributes;
import ghidra.util.task.TaskMonitor;

public class GZipFileSystemFactoryTest extends AbstractGenericTest {
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void testConcatenatedMembersProduceCompletePayload() throws Exception {
		assertPayload(concatenate(member("first"), member("second")), "firstsecond");
	}

	@Test
	public void testEmptyFirstMemberRetainsFollowingPayload() throws Exception {
		assertPayload(concatenate(member(""), member("second")), "second");
	}

	@Test
	public void testSingleMemberPayload() throws Exception {
		assertPayload(member("single"), "single");
	}

	@Test
	public void testSingleEmptyMemberPayload() throws Exception {
		assertPayload(member(""), "");
	}

	@Test
	public void testConcatenatedEmptyMembersPayload() throws Exception {
		assertPayload(concatenate(member(""), member("")), "");
	}

	@Test
	public void testMetadataStillComesFromFirstMember() throws Exception {
		byte[] compressed = concatenate(namedMember("first", "first.bin", "first comment"),
			namedMember("second", "second.bin", "second comment"));
		FileSystemService service = newService();
		try (GZipFileSystem fs = create(service, compressed)) {
			assertEquals("first.bin", fs.getPayloadFile().getName());
			FileAttributes attributes = fs.getFileAttributes(fs.getPayloadFile(), TaskMonitor.DUMMY);
			assertEquals("first comment", attributes.get(COMMENT_ATTR, String.class, null));
			assertEquals((long) compressed.length,
				(long) attributes.get(COMPRESSED_SIZE_ATTR, Long.class, null));
		}
		finally {
			service.clear();
			waitForCacheMaintenance(service);
		}
	}

	private void assertPayload(byte[] compressed, String expected) throws Exception {
		FileSystemService service = newService();
		try (GZipFileSystem fs = create(service, compressed);
				ByteProvider payload = fs.getByteProvider(fs.getPayloadFile(), TaskMonitor.DUMMY)) {
			byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
			assertEquals(expectedBytes.length, payload.length());
			try (InputStream in = payload.getInputStream(0)) {
				assertArrayEquals(expectedBytes, in.readAllBytes());
			}
			assertEquals("sample", fs.getPayloadFile().getName());
		}
		finally {
			service.clear();
			waitForCacheMaintenance(service);
		}
	}

	private FileSystemService newService() throws IOException {
		FileSystemService service = new FileSystemService(temporaryFolder.newFolder());
		waitForCacheMaintenance(service);
		return service;
	}

	private void waitForCacheMaintenance(FileSystemService service) {
		FileCache cache = (FileCache) getInstanceField("fileCache", service);
		// Observe the actual owned service's maintenance worker using existing test helpers.
		waitForCondition(() -> !(Boolean) invokeInstanceMethod("isCleaning", cache));
	}

	private GZipFileSystem create(FileSystemService service, byte[] compressed) throws Exception {
		String md5 = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(compressed));
		FSRL source = FSRLRoot.makeRoot("file").withPath("/sample.gz").withMD5(md5);
		ByteProvider provider = new ByteArrayProvider(compressed, source);
		return new GZipFileSystemFactory().create(FSRLRoot.nestedFS(source, "gzip"), provider,
			service, TaskMonitor.DUMMY);
	}

	private byte[] member(String payload) throws IOException {
		ByteArrayOutputStream compressed = new ByteArrayOutputStream();
		try (GZIPOutputStream out = new GZIPOutputStream(compressed)) {
			out.write(payload.getBytes(StandardCharsets.UTF_8));
		}
		return compressed.toByteArray();
	}

	private byte[] namedMember(String payload, String name, String comment) throws IOException {
		ByteArrayOutputStream compressed = new ByteArrayOutputStream();
		GzipParameters parameters = new GzipParameters();
		parameters.setFileName(name);
		parameters.setComment(comment);
		try (GzipCompressorOutputStream out = new GzipCompressorOutputStream(compressed, parameters)) {
			out.write(payload.getBytes(StandardCharsets.UTF_8));
		}
		return compressed.toByteArray();
	}

	private byte[] concatenate(byte[] first, byte[] second) throws IOException {
		ByteArrayOutputStream joined = new ByteArrayOutputStream();
		joined.write(first);
		joined.write(second);
		return joined.toByteArray();
	}
}
