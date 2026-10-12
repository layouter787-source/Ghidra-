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
package ghidra.app.util.exporter;

import static org.junit.Assert.*;
import org.junit.Test;
import ghidra.app.util.opinion.IntelHexRecord;
import ghidra.app.util.opinion.IntelHexRecordReader;
import ghidra.app.util.opinion.IntelHexRecordWriter;
import ghidra.program.database.ProgramBuilder;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.Program;
import ghidra.test.AbstractGhidraHeadlessIntegrationTest;
import ghidra.util.task.TaskMonitor;
import java.nio.file.*;
import java.util.*;

/** Actual public Intel HEX export must retain selected address and byte pairs. */
public class IntelHexExportGapTest extends AbstractGhidraHeadlessIntegrationTest {
    private static class Fixture implements AutoCloseable {
        final ProgramBuilder builder;
        final Program program;
        Fixture() throws Exception {
            builder = new ProgramBuilder("IntelHexGap", ProgramBuilder._TOY, null);
            program = builder.getProgram();
        }
        void block(String name, long address, byte[] bytes) throws Exception {
            String location=Long.toHexString(address);
            builder.createMemory(name, location, bytes.length);
            builder.setBytes(location, bytes);
        }
        AddressSet selection(long first, long last) {
            return new AddressSet(builder.addr(first), builder.addr(last));
        }
        public void close() { builder.dispose(); }
    }
    private static byte[] sequence(int count, int start) {
        byte[] bytes=new byte[count];
        for (int i=0;i<count;i++) { bytes[i]=(byte)(start+i); }
        return bytes;
    }
    private Map<Long,Byte> selectedInput(Fixture fixture, AddressSetView selection) throws Exception {
        assertEquals(32,fixture.program.getAddressFactory().getDefaultAddressSpace().getSize());
        assertEquals(1,fixture.program.getAddressFactory().getDefaultAddressSpace().getAddressableUnitSize());
        AddressSetView selected=selection==null ? fixture.program.getMemory() : selection;
        Map<Long,Byte> input=new TreeMap<>();
        AddressIterator addresses=selected.getAddresses(true);
        while (addresses.hasNext()) {
            Address address=addresses.next();
            assertTrue(fixture.program.getMemory().getBlock(address).isInitialized());
            input.put(address.getOffset(),fixture.program.getMemory().getByte(address));
        }
        return input;
    }
    private Map<Long,Byte> exportAndRead(String name, Fixture fixture, AddressSetView selection,
            boolean drop, int expectedInputBytes) throws Exception {
        Map<Long,Byte> input=selectedInput(fixture,selection);
        assertEquals(expectedInputBytes,input.size());
        Path file=Files.createTempFile("intelhex-address-gap-", ".hex");
        try {
            assertTrue(new IntelHexExporter(16,drop).export(file.toFile(),fixture.program,selection,TaskMonitor.DUMMY));
            List<String> lines=Files.readAllLines(file);
            Map<Long,Byte> actual=new TreeMap<>();
            long upper=0;
            int dataRecords=0,extendedRecords=0,eofRecords=0;
            for (int lineIndex=0;lineIndex<lines.size();lineIndex++) {
                IntelHexRecord record=IntelHexRecordReader.readRecord(lines.get(lineIndex));
                assertTrue("Actual record checksum must be valid",record.isReportedChecksumCorrect());
                switch (record.getRecordType()) {
                    case IntelHexRecord.EXTENDED_LINEAR_ADDRESS_RECORD_TYPE:
                        assertEquals(0,eofRecords);
                        assertEquals(2,record.getRecordLength());
                        byte[] segment=record.getData();
                        upper=(((segment[0]&255L)<<8)|(segment[1]&255L))<<16;
                        extendedRecords++;break;
                    case IntelHexRecord.DATA_RECORD_TYPE:
                        assertEquals(0,eofRecords);
                        byte[] data=record.getData();
                        assertTrue(data.length>0 && data.length<=16);
                        for (int i=0;i<data.length;i++) {
                            long address=upper+record.getLoadOffset()+i;
                            assertNull("Actual exported addresses must not overlap",actual.put(address,data[i]));
                        }
                        dataRecords++;break;
                    case IntelHexRecord.END_OF_FILE_RECORD_TYPE:
                        eofRecords++;assertEquals(lines.size()-1,lineIndex);break;
                    default:fail("Unexpected actual record type: "+record.getRecordType());
                }
            }
            assertEquals(1,eofRecords);
            System.out.println("INTEL_HEX_OBSERVATION case="+name+" input="+format(input)+
                " actual="+format(actual)+" dataRecords="+dataRecords+" extendedRecords="+extendedRecords+
                " eofRecords="+eofRecords+" drop="+drop);
            return actual;
        }
        finally { Files.deleteIfExists(file); }
    }
    private String format(Map<Long,Byte> data) {
        List<String> pairs=new ArrayList<>();
        data.forEach((address,value)->pairs.add(String.format("%08x:%02x",address,value&255)));
        return String.join(",",pairs);
    }
    @Test public void testSparseBlocksPreserveActualAddresses() throws Exception {
        try (Fixture fixture=new Fixture()) {
            fixture.block("first",0x1000,sequence(4,1));fixture.block("second",0x1020,sequence(3,0xa1));
            Map<Long,Byte> expected=selectedInput(fixture,null);
            assertEquals("Sparse initialized block export must preserve actual address-byte map",expected,
                exportAndRead("sparse-blocks",fixture,null,false,7));
        }
    }
    @Test public void testSelectionHolesPreserveActualAddresses() throws Exception {
        try (Fixture fixture=new Fixture()) {
            fixture.block("contiguous",0x1000,sequence(16,1));
            AddressSet selected=fixture.selection(0x1000,0x1003);selected.add(fixture.selection(0x1008,0x100b));
            Map<Long,Byte> expected=selectedInput(fixture,selected);
            assertEquals("Sparse selection export must preserve actual address-byte map",expected,
                exportAndRead("selection-holes",fixture,selected,false,8));
        }
    }
    @Test public void testContiguousShortRecordPreserved() throws Exception {
        try (Fixture fixture=new Fixture()) {
            fixture.block("contiguous",0x1000,sequence(7,1));
            assertEquals(selectedInput(fixture,null),exportAndRead("contiguous",fixture,null,false,7));
        }
    }
    @Test public void testAdjacentBlocksRemainContiguous() throws Exception {
        try (Fixture fixture=new Fixture()) {
            fixture.block("first",0x1000,sequence(4,1));fixture.block("second",0x1004,sequence(3,5));
            assertEquals(selectedInput(fixture,null),exportAndRead("adjacent-blocks",fixture,null,false,7));
        }
    }
    @Test public void testFullRecordBeforeGapPreserved() throws Exception {
        try (Fixture fixture=new Fixture()) {
            fixture.block("first",0x1000,sequence(16,1));fixture.block("second",0x1020,sequence(3,0xa1));
            assertEquals(selectedInput(fixture,null),exportAndRead("full-before-gap",fixture,null,false,19));
        }
    }
    @Test public void testSegmentChangePreservesActualAddresses() throws Exception {
        try (Fixture fixture=new Fixture()) {
            fixture.block("first",0x1000,sequence(4,1));fixture.block("second",0x10020,sequence(3,0xa1));
            assertEquals(selectedInput(fixture,null),exportAndRead("segment-change",fixture,null,false,7));
        }
    }
    @Test public void testDeliberateTrailingDropPreserved() throws Exception {
        try (Fixture fixture=new Fixture()) {
            fixture.block("contiguous",0x1000,sequence(19,1));
            Map<Long,Byte> expected=selectedInput(fixture,fixture.selection(0x1000,0x100f));
            assertEquals(expected,exportAndRead("trailing-drop",fixture,null,true,19));
        }
    }
    @Test public void testEmptySelectionProducesOnlyEof() throws Exception {
        try (Fixture fixture=new Fixture()) {
            fixture.block("unselected",0x1000,sequence(7,1));
            assertEquals(Map.of(),exportAndRead("empty-selection",fixture,new AddressSet(),false,0));
        }
    }

    @Test public void testGapFlushesEarlierPartialButDropsTerminalPartial() throws Exception {
        try (Fixture fixture=new Fixture()) {
            fixture.block("first",0x1000,sequence(4,1));
            fixture.block("second",0x1020,sequence(3,0xa1));
            Map<Long,Byte> expected=selectedInput(fixture,fixture.selection(0x1000,0x1003));
            assertEquals("A gap flushes the earlier partial record; trailing drop applies at finish",
                expected,exportAndRead("gap-trailing-drop",fixture,null,true,7));
        }
    }
    @Test public void testSegmentChangeFlushesEarlierPartialButDropsTerminalPartial() throws Exception {
        try (Fixture fixture=new Fixture()) {
            fixture.block("first",0x1000,sequence(4,1));
            fixture.block("second",0x10020,sequence(3,0xa1));
            Map<Long,Byte> expected=selectedInput(fixture,fixture.selection(0x1000,0x1003));
            assertEquals(expected,exportAndRead("segment-trailing-drop",fixture,null,true,7));
        }
    }
    private List<IntelHexRecord> parsedData(IntelHexRecordWriter writer) {
        List<IntelHexRecord> data=new ArrayList<>();
        for (IntelHexRecord emitted:writer.finish(null)) {
            IntelHexRecord parsed=IntelHexRecordReader.readRecord(emitted.format());
            assertTrue(parsed.isReportedChecksumCorrect());
            if (parsed.getRecordType()==IntelHexRecord.DATA_RECORD_TYPE) { data.add(parsed); }
        }
        return data;
    }
    private void assertData(IntelHexRecord record,int offset,int... values) {
        assertEquals(offset,record.getLoadOffset());
        byte[] expected=new byte[values.length];
        for (int i=0;i<values.length;i++) { expected[i]=(byte)values[i]; }
        assertArrayEquals(expected,record.getData());
    }
    @Test public void testSegmentedGapAndSegmentBoundaryPreserveOffsets() {
        SegmentedAddressSpace space=new SegmentedAddressSpace("ram",0);
        IntelHexRecordWriter writer=new IntelHexRecordWriter(16,false);
        writer.addByte(space.getAddress(0x100,0x10),(byte)1);
        writer.addByte(space.getAddress(0x100,0x11),(byte)2);
        writer.addByte(space.getAddress(0x100,0x20),(byte)3);
        writer.addByte(space.getAddress(0x200,0x10),(byte)4);
        List<IntelHexRecord> records=new ArrayList<>();
        for (IntelHexRecord emitted:writer.finish(null)) {
            IntelHexRecord parsed=IntelHexRecordReader.readRecord(emitted.format());
            assertTrue(parsed.isReportedChecksumCorrect());
            records.add(parsed);
        }
        assertEquals(6,records.size());
        assertEquals(IntelHexRecord.EXTENDED_SEGMENT_ADDRESS_RECORD_TYPE,records.get(0).getRecordType());
        assertData(records.get(0),0,1,0);
        assertData(records.get(1),0x10,1,2);
        assertData(records.get(2),0x20,3);
        assertEquals(IntelHexRecord.EXTENDED_SEGMENT_ADDRESS_RECORD_TYPE,records.get(3).getRecordType());
        assertData(records.get(3),0,2,0);
        assertData(records.get(4),0x10,4);
        assertEquals(IntelHexRecord.END_OF_FILE_RECORD_TYPE,records.get(5).getRecordType());
    }
    @Test public void testDescendingAddressesStartSeparateRecords() {
        GenericAddressSpace space=new GenericAddressSpace("ram",32,AddressSpace.TYPE_RAM,0);
        IntelHexRecordWriter writer=new IntelHexRecordWriter(16,false);
        writer.addByte(space.getAddress(0x1020),(byte)1);
        writer.addByte(space.getAddress(0x1000),(byte)2);
        List<IntelHexRecord> records=parsedData(writer);
        assertEquals(2,records.size());
        assertData(records.get(0),0x1020,1);
        assertData(records.get(1),0x1000,2);
    }
    @Test public void testSpaceMaximumDoesNotWrapBufferedRecord() {
        GenericAddressSpace space=new GenericAddressSpace("ram",8,AddressSpace.TYPE_RAM,0);
        assertNull(space.getMaxAddress().next());
        IntelHexRecordWriter writer=new IntelHexRecordWriter(16,false);
        writer.addByte(space.getAddress(0xfe),(byte)1);
        writer.addByte(space.getAddress(0xff),(byte)2);
        writer.addByte(space.getAddress(0),(byte)3);
        List<IntelHexRecord> records=parsedData(writer);
        assertEquals(2,records.size());
        assertData(records.get(0),0xfe,1,2);
        assertData(records.get(1),0,3);
    }
}
