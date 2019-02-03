package com.yandex.yoctodb.util.mutable.impl;

import com.yandex.yoctodb.util.UnsignedByteArray;
import com.yandex.yoctodb.util.UnsignedByteArrays;
import com.yandex.yoctodb.util.buf.Buffer;
import com.yandex.yoctodb.util.mutable.ByteArrayIndexedList;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.yandex.yoctodb.util.UnsignedByteArrays.from;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class FoldedByteArrayIndexedListTest {

    @Test
    public void checkOutputStream() throws IOException {
        final List<UnsignedByteArray> strings = initString();
        Map<UnsignedByteArray, LinkedList<Integer>> data = initData(strings);
        final FoldedByteArrayIndexedList set =
                new FoldedByteArrayIndexedList(data);
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        set.writeTo(os);

        Buffer buf = Buffer.from(os.toByteArray());
        System.out.println("Full buffer size in bytes: " + buf.remaining());

        // getInt() возвращает (первый Int?) количество элементов,
        // которое мы записали в буфер
        final int elementsCount = buf.getInt();
        assertEquals(elementsCount, strings.size());

        final int offsetsCount = buf.getInt();

        System.out.println("Offsets count is: " + offsetsCount);
        int sizeOfIndexOffsetValue;
        if (offsetsCount <= 127) { // one byte 2^8 - 1 = 127
            sizeOfIndexOffsetValue = 1;
        } else if (offsetsCount <= 65535) {  // to  2^16 - 1 = 65535
            sizeOfIndexOffsetValue = 2;
        } else {
            sizeOfIndexOffsetValue = 4;
        }


        System.out.println("Remaining: " + buf.remaining());
        // indexes of offsets
        final Buffer indexes = buf.slice((elementsCount) * sizeOfIndexOffsetValue);

        System.out.println("Idexes remaining " + indexes.remaining());

        System.out.println("remaining " + indexes.remaining());
        System.out.println("value 0 " + getOffsetIndex(indexes, 0, sizeOfIndexOffsetValue)); // todo в assert!
        System.out.println("value 1 " + getOffsetIndex(indexes, 1, sizeOfIndexOffsetValue));
        System.out.println("value 2 " + getOffsetIndex(indexes, 2, sizeOfIndexOffsetValue));
        System.out.println("remaining " + indexes.remaining());

        long shift = indexes.remaining();

        System.out.println(offsetsCount);

        // then offsets of element value
        System.out.println("After slicing indexes shift is: " + shift);
        System.out.println("After slicing indexes offsets size is: " + (buf.remaining() - shift));

        final Buffer offsets = buf.slice() // получаем здесь копию buf
                .slice(shift, buf.remaining() - shift) // смещаемся до места, с которого начинаются offsets
                .slice((offsetsCount) << 3); // отрезаем столько, сколько offsets занимают!

        System.out.println("Offset remaining " + offsets.remaining());

        shift = shift + offsets.remaining();

        System.out.println("After slicing indexes shift is: " + shift);
        System.out.println("After slicing indexes offsets size is: " + (buf.remaining() - shift));


        // then elements value
        final Buffer elements = buf.slice()
                .slice(shift, (long) (buf.remaining() - shift))
                .slice();

        System.out.println("Elements remaining " + elements.remaining());

        System.out.println("remaining " + offsets.remaining());
        System.out.println("value 0 " + getValueIndex(indexes, offsets, 0, sizeOfIndexOffsetValue));
        System.out.println("value 1 " + getValueIndex(indexes, offsets, 1, sizeOfIndexOffsetValue));
        System.out.println("value 2 " + getValueIndex(indexes, offsets, 2, sizeOfIndexOffsetValue));
        System.out.println("remaining " + offsets.remaining());

        // осталось 7 бит - это NEW + USED!
        Buffer buffer;

        buffer = getValue(indexes, offsets, elements, 0, sizeOfIndexOffsetValue);
        UnsignedByteArray byteArray = UnsignedByteArrays.from(buffer);
        String value = UnsignedByteArrays.toString(byteArray);
        System.out.println(value);

        buffer = getValue(indexes, offsets, elements, 1, sizeOfIndexOffsetValue);
        byteArray = UnsignedByteArrays.from(buffer);
        value = UnsignedByteArrays.toString(byteArray);
        System.out.println(value);

        buffer = getValue(indexes, offsets, elements, 2, sizeOfIndexOffsetValue);
        byteArray = UnsignedByteArrays.from(buffer);
        value = UnsignedByteArrays.toString(byteArray);
        System.out.println(value);

        assertEquals(getValueFromBuffer(getValue(indexes, offsets, elements, 0, sizeOfIndexOffsetValue)), "NEW");
        assertEquals(getValueFromBuffer(getValue(indexes, offsets, elements, 1, sizeOfIndexOffsetValue)), "USED");
        assertEquals(getValueFromBuffer(getValue(indexes, offsets, elements, 2, sizeOfIndexOffsetValue)), "NEW");
        assertEquals(getValueFromBuffer(getValue(indexes, offsets, elements, 3, sizeOfIndexOffsetValue)), "NEW");
    }

    private String getValueFromBuffer(Buffer buffer) {
        UnsignedByteArray byteArray = UnsignedByteArrays.from(buffer);
        return UnsignedByteArrays.toString(byteArray);
    }

    private Buffer getValue(Buffer indexes,
                            Buffer offsets,
                            Buffer elements,
                            int docId,
                            int sizeOfIndexOffsetValue) {
        int offsetIndex = getOffsetIndex(indexes, docId, sizeOfIndexOffsetValue);
        long start = offsets.getLong(offsetIndex << 3);
        long end = offsets.getLong((offsetIndex + 1) << 3); // берем соседа
        return elements.slice(start, end - start);
    }


    private long getValueIndex(Buffer indexes,
                               Buffer offsets,
                               int docId,
                               int sizeOfIndexOffsetValue) {
        // если здесь не использовать slice - не ломается :)
        int offsetIndex = getOffsetIndex(indexes, docId, sizeOfIndexOffsetValue);
        return offsets.getLong(offsetIndex << 3);
    }

    private int getOffsetIndex(Buffer indexes, int docId, int sizeOfIndexOffsetValue) {
        // если здесь не использовать slice - не ломается :)
        switch (sizeOfIndexOffsetValue) {
            case (Byte.BYTES): {
                // write every int to one byte
                return indexes.get(docId);
            }
            case (Short.BYTES): {
                // write every int to two bytes
                return twoBytesToInt(indexes, docId);
            }
            case (Integer.BYTES): {
                // write every int to four bytes
                return indexes.getInt(docId >> 2); // как и раньше
            }
        }
        throw new IllegalArgumentException();
    }

    private int twoBytesToInt(Buffer indexes, int docId) {
        int byteIndex = docId * Short.BYTES;
        byte[] bytes = new byte[]{
                indexes.get(byteIndex),
                indexes.get(byteIndex + 1)
        };
        return (0xff & bytes[0]) << 8 | (0xff & bytes[1]);
    }

    @Test
    public void checkSize() {
        final FoldedByteArrayIndexedList foldedList =
                new FoldedByteArrayIndexedList(initData(initString()));
        assertEquals(43, foldedList.getSizeInBytes());
    }

    @Test
    public void string() {
        final List<UnsignedByteArray> elements = new LinkedList<>();
        final int size = 10;
        for (int i = 0; i < size; i++)
            elements.add(from(i));
        final ByteArrayIndexedList set =
                new FoldedByteArrayIndexedList(initData(elements));
        final String text = set.toString();
        assertTrue(text.contains(Integer.toString(size)));
    }

    private final List<UnsignedByteArray> initString() {
        final List<UnsignedByteArray> elements = new LinkedList<>();
        elements.add(from("NEW"));
        elements.add(from("USED"));
        elements.add(from("NEW"));
        elements.add(from("NEW"));
        return elements;
    }

    private final Map<UnsignedByteArray, LinkedList<Integer>> initData(List<UnsignedByteArray> elements) {

        Map<UnsignedByteArray, LinkedList<Integer>> values = new LinkedHashMap<>();
        for (int i = 0; i < elements.size(); i++) {
            int documentId = i;
            UnsignedByteArray val = elements.get(documentId);
            values.merge(val,
                    new LinkedList<Integer>() {{
                        add(documentId);
                    }},
                    (oldList, newList) -> {
                        oldList.addAll(newList);
                        return oldList;
                    });
        }
        return values;
    }
}
