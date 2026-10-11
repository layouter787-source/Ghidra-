/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.util;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import ghidra.util.exception.IOCancelledException;
import ghidra.util.task.TaskMonitorAdapter;
import utilities.util.FileUtilities;

public class MonitoredOutputStreamTest {
	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	private TaskMonitorAdapter canceledMonitor() {
		TaskMonitorAdapter monitor = new TaskMonitorAdapter(true);
		monitor.cancel();
		return monitor;
	}

	@Test
	public void testCanceledSingleByteDoesNotWrite() throws Exception {
		ByteArrayOutputStream destination = new ByteArrayOutputStream();
		MonitoredOutputStream output = new MonitoredOutputStream(destination, canceledMonitor());
		assertThrows(IOCancelledException.class, () -> output.write(42));
		assertEquals(0, destination.size());
	}

	@Test
	public void testCanceledShortBufferDoesNotWrite() throws Exception {
		ByteArrayOutputStream destination = new ByteArrayOutputStream();
		MonitoredOutputStream output = new MonitoredOutputStream(destination, canceledMonitor());
		assertThrows(IOCancelledException.class, () -> output.write(new byte[] { 1, 2, 3 }));
		assertEquals(0, destination.size());
	}

	@Test
	public void testCanceledActualCopyDoesNotWriteSmallFile() throws Exception {
		File file = temporary.newFile("canceled-copy");
		try (OutputStream destination = new FileOutputStream(file)) {
			assertThrows(IOCancelledException.class, () -> FileUtilities.copyStreamToStream(
				new ByteArrayInputStream(new byte[] { 1, 2, 3 }), destination, canceledMonitor()));
		}
		assertEquals(0, Files.size(file.toPath()));
	}

	@Test
	public void testCancellationDuringSourceReadStopsNextWrite() throws Exception {
		TaskMonitorAdapter monitor = new TaskMonitorAdapter(true);
		InputStream source = new ByteArrayInputStream(new byte[] { 1, 2 }) {
			@Override
			public synchronized int read(byte[] buffer, int offset, int length) {
				if (pos > 0) {
					monitor.cancel();
				}
				return super.read(buffer, offset, Math.min(length, 1));
			}
		};
		File file = temporary.newFile("partial-copy");
		try (OutputStream destination = new FileOutputStream(file)) {
			assertThrows(IOCancelledException.class,
				() -> FileUtilities.copyStreamToStream(source, destination, monitor));
		}
		assertArrayEquals(new byte[] { 1 }, Files.readAllBytes(file.toPath()));
	}

	@Test
	public void testSuccessfulSmallCopyPreservesDataAndOutputOwnership() throws Exception {
		File file = temporary.newFile("complete-copy");
		try (OutputStream destination = new FileOutputStream(file)) {
			assertEquals(2, FileUtilities.copyStreamToStream(
				new ByteArrayInputStream(new byte[] { 1, 2 }), destination,
				new TaskMonitorAdapter(true)));
			destination.write(3); // The real output descriptor must remain open for its caller.
		}
		assertArrayEquals(new byte[] { 1, 2, 3 }, Files.readAllBytes(file.toPath()));
	}

	@Test
	public void testProgressStillUpdatesAtBufferBoundary() throws Exception {
		AtomicLong progress = new AtomicLong();
		TaskMonitorAdapter monitor = new TaskMonitorAdapter(true) {
			@Override
			public void setProgress(long value) {
				progress.set(value);
			}
		};
		ByteArrayOutputStream destination = new ByteArrayOutputStream();
		MonitoredOutputStream output = new MonitoredOutputStream(destination, monitor);
		output.write(new byte[32767]);
		assertEquals(0, progress.get());
		output.write(1);
		assertEquals(32768, progress.get());
		assertEquals(32768, destination.size());
	}

	@Test
	public void testCanceledZeroLengthWriteRemainsNoOp() throws Exception {
		ByteArrayOutputStream destination = new ByteArrayOutputStream();
		MonitoredOutputStream output = new MonitoredOutputStream(destination, canceledMonitor());
		output.write(new byte[] { 1 }, 1, 0);
		assertEquals(0, destination.size());
	}

	@Test
	public void testCanceledZeroLengthWriteStillValidatesArguments() throws Exception {
		MonitoredOutputStream output = new MonitoredOutputStream(
			new ByteArrayOutputStream(), canceledMonitor());
		assertThrows(NullPointerException.class, () -> output.write(null, 0, 0));
		assertThrows(IndexOutOfBoundsException.class, () -> output.write(new byte[1], 2, 0));
	}
}
