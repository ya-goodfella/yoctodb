package com.yandex.yoctodb.v1.mutable.segment;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.yandex.yoctodb.util.OutputStreamWritable;
import com.yandex.yoctodb.util.UnsignedByteArray;
import com.yandex.yoctodb.util.UnsignedByteArrays;
import com.yandex.yoctodb.util.mutable.impl.FoldedByteArrayIndexedList;
import com.yandex.yoctodb.v1.V1DatabaseFormat;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class V1FoldedIndex extends Freezable
        implements IndexSegment {
    @NotNull
    private final byte[] fieldName;
    private SortedMap<Integer, UnsignedByteArray> values = new TreeMap<>();
    private int databaseDocumentsCount = -1;

    public V1FoldedIndex(
            @NotNull
            final String fieldName) {
        this.fieldName = fieldName.getBytes();
    }

    @NotNull
    @Override
    public IndexSegment addDocument(
            final int documentId,
            @NotNull
            final Collection<UnsignedByteArray> values) {
        if (documentId < 0)
            throw new IllegalArgumentException("Negative document ID");
        if (values.size() != 1)
            throw new IllegalArgumentException("A single value expected");

        checkNotFrozen();

        this.values.put(documentId, values.iterator().next());

        return this;
    }

    @Override
    public void setDatabaseDocumentsCount(final int documentsCount) {
        assert documentsCount > 0;

        this.databaseDocumentsCount = documentsCount;
    }

    @NotNull
    @Override
    public OutputStreamWritable buildWritable() {
        checkNotFrozen();

        freeze();

        assert databaseDocumentsCount > 0;

        // Padding

        final List<UnsignedByteArray> padded =
                new ArrayList<>(databaseDocumentsCount);
        int expectedDocument = 0;
        final UnsignedByteArray empty = UnsignedByteArrays.from(new byte[]{});

        for (Map.Entry<Integer, UnsignedByteArray> e : values.entrySet()) {
            while (expectedDocument < e.getKey()) {
                padded.add(empty);
                expectedDocument++;
            }

            padded.add(e.getValue());
            expectedDocument++;
        }

        while (expectedDocument < databaseDocumentsCount) {
            padded.add(empty);
            expectedDocument++;
        }

        // Building the index
        final OutputStreamWritable valueIndex =
                new FoldedByteArrayIndexedList(padded);

        // Free memory
        values = null;

        return new OutputStreamWritable() {
            @Override
            public long getSizeInBytes() {
                return 4L + // Field name
                        fieldName.length +
                        8 + // Values
                        valueIndex.getSizeInBytes();
            }

            @Override
            public void writeTo(
                    @NotNull
                    final OutputStream os) throws IOException {
                os.write(Longs.toByteArray(getSizeInBytes()));

                // Payload segment type
                os.write(
                        Ints.toByteArray(
                                V1DatabaseFormat.SegmentType
                                        .VARIABLE_LENGTH_FOLDED_INDEX
                                        .getCode()));

                // Field name
                os.write(Ints.toByteArray(fieldName.length));
                os.write(fieldName);

                // Values
                os.write(Longs.toByteArray(valueIndex.getSizeInBytes()));
                valueIndex.writeTo(os);
            }
        };
    }
}
