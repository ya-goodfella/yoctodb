/*
 * (C) YANDEX LLC, 2014-2016
 *
 * The Source Code called "YoctoDB" available at
 * https://github.com/yandex/yoctodb is subject to the terms of the
 * Mozilla Public License, v. 2.0 (hereinafter referred to as the "License").
 *
 * A copy of the License is also available at http://mozilla.org/MPL/2.0/.
 */

package com.yandex.yoctodb.util.mutable.impl;

import com.yandex.yoctodb.util.buf.Buffer;
import com.yandex.yoctodb.util.mutable.BitSet;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author svyatoslav
 */
public class LongArrayBitSetTest {
    private static final int SIZE = 1024;

    @Test
    public void inverse() {
        for (int i = 1; i < SIZE; i++) {
            final BitSet bs = LongArrayBitSet.one(i);
            assertFalse(bs.inverse());
            assertTrue(bs.inverse());
            assertEquals(i, bs.cardinality());
        }
    }

    @Test
    public void or() {
        for (int i = 1; i < SIZE; i++) {
            final BitSet bs1 = LongArrayBitSet.one(i);
            final BitSet bs2 = LongArrayBitSet.zero(i);
            assertEquals(i, bs1.cardinality());
            assertEquals(0, bs2.cardinality());
            bs2.or(bs1);
            assertEquals(i, bs2.cardinality());
        }
    }

    @Test
    public void andEmpty() {
        for (int i = 1; i < SIZE; i++) {
            final BitSet bs1 = LongArrayBitSet.one(i);
            final BitSet bs2 = LongArrayBitSet.zero(i);
            assertEquals(i, bs1.cardinality());
            assertEquals(0, bs2.cardinality());
            assertFalse(bs2.and(bs1));
        }
    }

    @Test
    public void andNonEmpty() {
        for (int i = 1; i < SIZE; i++) {
            final BitSet bs1 = LongArrayBitSet.one(i);
            final BitSet bs2 = LongArrayBitSet.one(i);
            assertEquals(i, bs1.cardinality());
            assertEquals(i, bs2.cardinality());
            assertTrue(bs2.and(bs1));
        }
    }

    @Test
    public void xor() {
        final BitSet bs1 = LongArrayBitSet.zero(2);
        final BitSet bs2 = LongArrayBitSet.one(2);

        bs1.set(0);
        assertEquals(2, bs2.cardinality());
        bs2.xor(bs1);
        assertEquals(1, bs2.cardinality());
    }

    @Test
    public void andBuffer() {
        for (int docs = 1; docs < SIZE; docs++) {
            final int arraySize = LongArrayBitSet.arraySize(docs);
            final BitSet bs1 = LongArrayBitSet.one(docs);

            assertEquals(docs, bs1.cardinality());
            byte[] buf1 = new byte[arraySize * 8];
            bs1.and(Buffer.from(buf1), 0, arraySize);
            assertEquals(0, bs1.cardinality());

            final BitSet bs2 = LongArrayBitSet.one(docs);
            assertEquals(docs, bs2.cardinality());
            byte[] buf2 = new byte[arraySize * 8];
            buf2[7] = 1;
            assertTrue(bs2.and(Buffer.from(buf2), 0, arraySize));
            assertEquals(1, bs2.cardinality());
        }
    }

    @Test
    public void empty() {
        for (int i = 1; i < SIZE; i++) {
            assertTrue(LongArrayBitSet.zero(i).isEmpty());
        }
    }

    @Test
    public void simpleAndTest() {
        for (int i = 1; i < SIZE; i++) {
            final BitSet bs1 = LongArrayBitSet.one(i);
            final BitSet bs2 = LongArrayBitSet.one(i);
            assertEquals(i, bs1.cardinality());
            bs1.and(bs2);
            assertEquals(i, bs1.cardinality());
        }
    }

    @Test
    public void clearTest() {
        for (int size = 1; size <= SIZE; size++) {
            final BitSet bitSet = LongArrayBitSet.one(size);
            assertEquals(size, bitSet.cardinality());
            bitSet.clear();
            assertEquals(0, bitSet.cardinality());
        }
    }

    @Test
    public void interleavingIteratorTest() {
        final BitSet s = LongArrayBitSet.zero(SIZE);
        for (int i = 0; i < s.getSize(); i += 2)
            s.set(i);
        for (int i = 0; i < s.getSize(); i += 2) {
            assertEquals(i, s.nextSetBit(i));
        }
    }

    @Test
    public void zerosIteratorTest() {
        final BitSet s = LongArrayBitSet.zero(SIZE);
        assertEquals(-1, s.nextSetBit(0));
    }

    @Test
    public void onesIteratorTest() {
        final BitSet s = LongArrayBitSet.one(SIZE);
        for (int i = 0; i < s.getSize(); i++) {
            assertEquals(i, s.nextSetBit(i));
        }
    }

}
