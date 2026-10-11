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
package ghidra.pyghidra;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import ghidra.formats.gfilesystem.FSUtilities;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.CancelledListener;

public class PyGhidraTaskMonitorTest {

	@Test
	public void testCancelNotifiesListenerWithCancelledState() {
		PyGhidraTaskMonitor monitor = new PyGhidraTaskMonitor(null, null);
		AtomicInteger calls = new AtomicInteger();
		CancelledListener listener = () -> {
			assertTrue(monitor.isCancelled());
			calls.incrementAndGet();
		};
		monitor.addCancelledListener(listener);
		monitor.cancel();
		monitor.removeCancelledListener(listener);
		assertEquals(1, calls.get());
		assertThrows(CancelledException.class, monitor::checkCancelled);
	}

	@Test
	public void testTimeoutNotifiesCancelledListener() throws InterruptedException {
		PyGhidraTaskMonitor monitor = new PyGhidraTaskMonitor(1, null);
		CountDownLatch cancelled = new CountDownLatch(1);
		CancelledListener listener = cancelled::countDown;
		monitor.addCancelledListener(listener);
		try {
			assertTrue(cancelled.await(5, TimeUnit.SECONDS));
			assertTrue(monitor.isCancelled());
		}
		finally {
			monitor.removeCancelledListener(listener);
			monitor.cancel();
		}
	}

	@Test
	public void testRepeatedCancelAndResetNotifications() {
		PyGhidraTaskMonitor monitor = new PyGhidraTaskMonitor(null, null);
		AtomicInteger calls = new AtomicInteger();
		CancelledListener listener = calls::incrementAndGet;
		monitor.addCancelledListener(listener);
		try {
			monitor.cancel();
			monitor.cancel();
			assertEquals(1, calls.get());
			monitor.clearCancelled();
			assertFalse(monitor.isCancelled());
			assertEquals(1, calls.get());
			monitor.cancel();
			assertEquals(2, calls.get());
		}
		finally {
			monitor.removeCancelledListener(listener);
		}
	}

	@Test
	public void testRemovedListenerIsNotNotified() {
		PyGhidraTaskMonitor monitor = new PyGhidraTaskMonitor(null, null);
		AtomicInteger calls = new AtomicInteger();
		CancelledListener listener = calls::incrementAndGet;
		monitor.addCancelledListener(listener);
		monitor.removeCancelledListener(listener);
		monitor.cancel();
		assertEquals(0, calls.get());
		assertTrue(monitor.isCancelled());
	}

	@Test
	public void testListenerExceptionPreservesCancelledState() {
		PyGhidraTaskMonitor monitor = new PyGhidraTaskMonitor(null, null);
		IllegalStateException failure = new IllegalStateException("listener failure");
		CancelledListener listener = () -> { throw failure; };
		monitor.addCancelledListener(listener);
		try {
			assertSame(failure, assertThrows(IllegalStateException.class, monitor::cancel));
			assertTrue(monitor.isCancelled());
			monitor.cancel();
		}
		finally {
			monitor.removeCancelledListener(listener);
		}
	}

	@Test
	public void testCancelUnblocksActualFilesystemStreamCopy() throws Exception {
		RegistrationMonitor monitor = new RegistrationMonitor();
		CountDownLatch completed = new CountDownLatch(1);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
				Socket writer = new Socket(server.getInetAddress(), server.getLocalPort());
				Socket reader = server.accept()) {
			Thread worker = new Thread(() -> {
				try {
					FSUtilities.streamCopy(reader.getInputStream(), new ByteArrayOutputStream(),
						monitor);
				}
				catch (Throwable error) {
					failure.set(error);
				}
				finally {
					completed.countDown();
				}
			}, "PyGhidra cancellation stream copy");
			worker.setDaemon(true);
			worker.start();
			try {
				assertTrue(monitor.registered.await(5, TimeUnit.SECONDS));
				assertEquals(1, completed.getCount());
				monitor.cancel();
				assertTrue(completed.await(5, TimeUnit.SECONDS));
				assertTrue(reader.isClosed());
				assertTrue(failure.get() instanceof IOException);
			}
			finally {
				reader.close();
				writer.close();
				monitor.cancel();
				worker.join(5000);
				assertFalse("Stream-copy worker must always terminate", worker.isAlive());
			}
		}
	}

	private static class RegistrationMonitor extends PyGhidraTaskMonitor {
		private final CountDownLatch registered = new CountDownLatch(1);

		RegistrationMonitor() {
			super(null, null);
		}

		@Override
		public void addCancelledListener(CancelledListener listener) {
			super.addCancelledListener(listener);
			registered.countDown();
		}
	}
}
