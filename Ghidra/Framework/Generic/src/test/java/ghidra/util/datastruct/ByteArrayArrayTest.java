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
/*
 * ArrayArrayTest.java
 *
 * Created on February 13, 2002, 3:33 PM
 */

package ghidra.util.datastruct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import generic.test.AbstractGenericTest;
import ghidra.util.map.ObjectStorageAdapter;

/**
 *
 * 
 * @version 
 */
public class ByteArrayArrayTest extends AbstractGenericTest {

    /** Creates new ArrayArrayTest */
    public ByteArrayArrayTest() {
        super();
    }
@Test
    public void testSimpleGetPut() {
        ByteArrayArray baa = new ByteArrayArray();
        assertNull(baa.get(0));
        assertNull(baa.get(100));
        baa.put(0,new byte[] {0,1,2});
        byte[] b = baa.get(0);
        assertEquals(3,b.length);
        assertEquals(0,b[0]);
        assertEquals(1,b[1]);
        assertEquals(2,b[2]);
        
        baa.put(1,new byte[]{});
        b = baa.get(1);
        assertEquals(0,b.length);
        baa.put(2,new byte[]{5});
        b = baa.get(2);
        assertEquals(1,b.length);
        
        baa.remove(1);
        assertNull(baa.get(1));
    }
@Test
    public void testMany() {
        ByteArrayArray baa = new ByteArrayArray();

        for(int i=0;i<1000;i++) {
            byte t = (byte)i;
            baa.put(i,new byte[]{t,(byte)(t+1),(byte)(t+2),(byte)(t+3),(byte)(t+4)});
        }
        for(int i=0;i<1000;i++) {
            byte[] b = baa.get(i);
            assertEquals(5, b.length);
            for(int j=0;j<5;j++) {
                byte t = (byte)(i+j);
                assertEquals("i="+i+"j="+j,t,b[j]);
            }
        }
        
        for(int i=999;i>=0;i--) {
            baa.remove(i);
        }
        assertEquals(4,baa.starts.length);
        assertEquals(10,baa.bytes.length);
        for(int i=0;i<1000;i++) {
            byte t = (byte)i;
            baa.put(i,new byte[]{t,(byte)(t+1),(byte)(t+2),(byte)(t+3),(byte)(t+4)});
        }
        for(int i=0;i<1000;i++) {
            byte[] b = baa.get(i);
            assertEquals(5, b.length);
            for(int j=0;j<5;j++) {
                byte t = (byte)(i+j);
                assertEquals("i="+i+"j="+j,t,b[j]);
            }
        }
 
    }


    @Test
    public void testMissingAtInitialCapacity() {
        ByteArrayArray array = new ByteArrayArray();
        assertNull(array.get(array.starts.length));
        assertNull(array.get(array.starts.length + 1));
        assertThrows(IndexOutOfBoundsException.class, () -> array.get(-1));
        array.put(0, new byte[] { 1, 2 });
        assertArrayEquals(new byte[] { 1, 2 }, array.get(0));
        assertNull(array.get(array.starts.length));
    }

    @Test
    public void testMissingAtCapacityAfterGrowthAndShrink() {
        ByteArrayArray array = new ByteArrayArray();
        array.put(8, new byte[] { 7, 8 });
        int grownCapacity = array.starts.length;
        assertNull(array.get(grownCapacity));
        assertArrayEquals(new byte[] { 7, 8 }, array.get(8));
        array.put(0, new byte[0]);
        array.remove(8);
        assertEquals(ByteArrayArray.MIN_SIZE, array.starts.length);
        assertNull(array.get(array.starts.length));
        assertArrayEquals(new byte[0], array.get(0));
        array.put(4, new byte[] { 9 });
        assertArrayEquals(new byte[] { 9 }, array.get(4));
        assertNull(array.get(array.starts.length));
    }

    @Test
    public void testMissingByteArrayAtCapacityThroughDataTable() {
        DataTable table = new DataTable();
        table.putByteArray(0, 0, new byte[] { 1, 2 });
        assertNull(table.getByteArray(ByteArrayArray.MIN_SIZE, 0));
        assertArrayEquals(new byte[] { 1, 2 }, table.getByteArray(0, 0));
        table.putByteArray(ByteArrayArray.MIN_SIZE, 0, new byte[] { 3 });
        assertArrayEquals(new byte[] { 3 }, table.getByteArray(ByteArrayArray.MIN_SIZE, 0));
    }

    @Test
    public void testMissingByteArrayAtCapacityThroughObjectStorage() {
        DataTable table = new DataTable();
        new ObjectStorageAdapter(table, 0).putBytes(new byte[] { 4, 5 });
        assertNull(new ObjectStorageAdapter(table, ByteArrayArray.MIN_SIZE).getBytes());
        assertArrayEquals(new byte[] { 4, 5 }, new ObjectStorageAdapter(table, 0).getBytes());
        new ObjectStorageAdapter(table, ByteArrayArray.MIN_SIZE).putBytes(new byte[] { 6 });
        assertArrayEquals(new byte[] { 6 },
            new ObjectStorageAdapter(table, ByteArrayArray.MIN_SIZE).getBytes());
    }

}
