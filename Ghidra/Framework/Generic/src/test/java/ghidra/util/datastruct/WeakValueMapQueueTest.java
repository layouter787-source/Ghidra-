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
import java.util.List;

import org.junit.Test;

/** Deterministic queue delivery tests; these do not depend on spontaneous garbage collection. */
public class WeakValueMapQueueTest {

	private List<AbstractWeakValueMap<String, Object>> maps() {
		return List.of(new WeakValueHashMap<>(), new WeakValueTreeMap<>());
	}

	@Test
	public void testDelayedCollectionPreservesReplacement() {
		for (AbstractWeakValueMap<String, Object> map : maps()) {
			Object oldValue = new Object();
			Object replacement = new Object();
			map.put("key", oldValue);
			Reference<Object> oldReference = map.getRefMap().get("key");
			oldReference.clear();
			assertNull(map.get("key"));
			// Same lookup-miss/repopulate order as DWARF StringTable.getStringAtOffset.
			map.put("key", replacement);
			assertTrue(oldReference.enqueue());
			Reference.reachabilityFence(oldValue);
			assertSame(replacement, map.get("key"));
			assertEquals(1, map.size());
			Reference.reachabilityFence(replacement);
		}
	}

	@Test
	public void testSeveralCollectedGenerationsPreserveCurrentValue() {
		for (AbstractWeakValueMap<String, Object> map : maps()) {
			Object first = new Object();
			Object second = new Object();
			Object current = new Object();
			map.put("key", first);
			Reference<Object> firstReference = map.getRefMap().get("key");
			firstReference.clear();
			map.put("key", second);
			Reference<Object> secondReference = map.getRefMap().get("key");
			secondReference.clear();
			map.put("key", current);
			assertTrue(firstReference.enqueue());
			assertTrue(secondReference.enqueue());
			Reference.reachabilityFence(first);
			Reference.reachabilityFence(second);
			assertSame(current, map.get("key"));
			assertEquals(1, map.size());
			Reference.reachabilityFence(current);
		}
	}

	@Test
	public void testCurrentCollectedReferenceIsRemoved() {
		for (AbstractWeakValueMap<String, Object> map : maps()) {
			Object value = new Object();
			map.put("key", value);
			Reference<Object> reference = map.getRefMap().get("key");
			reference.clear();
			assertTrue(reference.enqueue());
			Reference.reachabilityFence(value);
			assertNull(map.get("key"));
			assertEquals(0, map.size());
		}
	}

	@Test
	public void testCollectionPreservesUnrelatedLiveMapping() {
		for (AbstractWeakValueMap<String, Object> map : maps()) {
			Object removed = new Object();
			Object retained = new Object();
			map.put("removed", removed);
			map.put("retained", retained);
			Reference<Object> reference = map.getRefMap().get("removed");
			reference.clear();
			assertTrue(reference.enqueue());
			Reference.reachabilityFence(removed);
			assertSame(retained, map.get("retained"));
			assertFalse(map.containsKey("removed"));
			assertEquals(1, map.size());
			Reference.reachabilityFence(retained);
		}
	}
}
