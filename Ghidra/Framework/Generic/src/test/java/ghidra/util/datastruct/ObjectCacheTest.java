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
package ghidra.util.datastruct;

import static org.junit.Assert.*;

import java.lang.ref.Reference;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import generic.test.AbstractGenericTest;

public class ObjectCacheTest extends AbstractGenericTest {

	private Reference<?> reference(ObjectCache<?> cache, long key) {
		Map<?, ?> entries = (Map<?, ?>) getInstanceField("hashTable", cache);
		return (Reference<?>) entries.get(key);
	}

	private void enqueueCollectedReference(Reference<?> reference) {
		// Replay the collector notification deterministically, without forcing GC.
		reference.clear();
		assertTrue(reference.enqueue());
	}

	@Test
	public void testLateCollectedReferencePreservesReplacement() {
		ObjectCache<Object> cache = new ObjectCache<>(0);
		Object first = new Object();
		cache.put(1, first);
		Reference<?> oldReference = reference(cache, 1);
		Object replacement = new Object();
		cache.put(1, replacement);
		enqueueCollectedReference(oldReference);
		Reference.reachabilityFence(first);

		assertTrue(cache.contains(1));
		assertSame(replacement, cache.get(1));
	}

	@Test
	public void testLateCollectedReferenceDoesNotReloadReplacement() {
		ObjectCache<Object> cache = new ObjectCache<>(0);
		Object first = new Object();
		cache.put(1, first);
		Reference<?> oldReference = reference(cache, 1);
		Object replacement = new Object();
		cache.put(1, replacement);
		enqueueCollectedReference(oldReference);
		Reference.reachabilityFence(first);
		Object other = new Object();
		cache.put(2, other); // This unrelated operation drains the reference queue.
		AtomicInteger loads = new AtomicInteger();

		assertSame(replacement, cache.computeIfAbsent(1, key -> {
			loads.incrementAndGet();
			return new Object();
		}));
		assertEquals(0, loads.get());
		assertSame(other, cache.get(2));
	}

	@Test
	public void testCurrentCollectedReferenceIsRemoved() {
		ObjectCache<Object> cache = new ObjectCache<>(0);
		Object first = new Object();
		cache.put(1, first);
		enqueueCollectedReference(reference(cache, 1));
		Reference.reachabilityFence(first);

		assertFalse(cache.contains(1));
		assertNull(cache.get(1));
		Object reloaded = new Object();
		assertSame(reloaded, cache.computeIfAbsent(1, key -> reloaded));
		assertSame(reloaded, cache.get(1));
	}

	@Test
	public void testReplacementAndExplicitRemoval() {
		ObjectCache<Object> cache = new ObjectCache<>(0);
		Object first = new Object();
		Object replacement = new Object();
		cache.put(1, first);
		assertSame(first, cache.get(1));
		cache.put(1, replacement);
		assertSame(replacement, cache.get(1));
		cache.remove(1);
		assertFalse(cache.contains(1));
		assertNull(cache.get(1));
	}
}
