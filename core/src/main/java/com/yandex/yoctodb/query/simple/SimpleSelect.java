/*
 * (C) YANDEX LLC, 2014
 *
 * The Source Code called "YoctoDB" available at
 * https://bitbucket.org/yandex/yoctodb is subject to the terms of the
 * Mozilla Public License, v. 2.0 (hereinafter - MPL).
 *
 * A copy of the MPL is available at http://mozilla.org/MPL/2.0/.
 */

package com.yandex.yoctodb.query.simple;

import com.yandex.yoctodb.immutable.FilterableIndex;
import com.yandex.yoctodb.query.*;
import com.yandex.yoctodb.util.mutable.BitSet;
import com.yandex.yoctodb.util.mutable.impl.ReadOnlyOneBitSet;
import net.jcip.annotations.NotThreadSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Simple {@link Select} implementation
 *
 * @author incubos
 */
@NotThreadSafe
public final class SimpleSelect implements Select {
    @NotNull
    private final List<AbstractSimpleCondition> conditions =
            new ArrayList<AbstractSimpleCondition>();
    @NotNull
    private final List<Order> sorts = new ArrayList<Order>();
    private int skip = 0;
    private int limit = Integer.MAX_VALUE;

    @NotNull
    @Override
    public Where where(
            @NotNull
            final Condition condition) {
        conditions.add((AbstractSimpleCondition) condition);
        return new SimpleWhereClause(this, conditions);
    }

    @NotNull
    @Override
    public OrderBy orderBy(
            @NotNull
            final Order order) {
        sorts.add(order);
        return new SimpleOrderClause(this, sorts);
    }

    @NotNull
    @Override
    public Select skip(final int skip) {
        this.skip = skip;
        return this;
    }

    @Override
    public int getSkip() {
        return this.skip;
    }

    @NotNull
    @Override
    public Select limit(final int limit) {
        this.limit = limit;
        return this;
    }

    @Override
    public int getLimit() {
        return this.limit;
    }

    @Override
    public boolean hasSorting() {
        return !sorts.isEmpty();
    }

    @Nullable
    @Override
    public BitSet filteredUnlimited(
            @NotNull
            final QueryContext ctx) {
        if (conditions.isEmpty()) {
            return new ReadOnlyOneBitSet(ctx.getDocumentCount());
        } else if (conditions.size() == 1) {
            final AbstractSimpleCondition c = conditions.iterator().next();
            final BitSet result = ctx.getZeroBitSet();
            final FilterableIndex filter = ctx.getFilter(c.getFieldName());
            if (filter != null && c.set(filter, result)) {
                return result;
            } else {
                return null;
            }
        } else {
            // Searching
            final BitSet result = ctx.getOneBitSet();
            final BitSet conditionResult = ctx.getZeroBitSet();
            final Iterator<AbstractSimpleCondition> iter =
                    conditions.iterator();
            while (iter.hasNext()) {
                final AbstractSimpleCondition c = iter.next();
                final FilterableIndex filter = ctx.getFilter(c.getFieldName());
                if (filter == null || !c.set(filter, conditionResult)) {
                    return null;
                }
                if (!result.and(conditionResult)) {
                    return null;
                }
                if (iter.hasNext()) {
                    conditionResult.clear();
                }
            }

            assert !result.isEmpty();

            return result;
        }
    }

    @NotNull
    @Override
    public Iterator<? extends ScoredDocument<?>> sortedUnlimited(
            @NotNull
            final BitSet docs,
            @NotNull
            final QueryContext ctx) {
        assert !docs.isEmpty();

        // Shortcut if there is not sorting
        if (sorts.isEmpty()) {
            return new IdScoredDocumentIterator(ctx, docs);
        } else {
            return new SortingScoredDocumentIterator(ctx, docs, sorts);
        }
    }

    @Override
    public String toString() {
        return "SimpleSelect{" +
               "conditions=" + conditions +
               ", sorts=" + sorts +
               ", skip=" + skip +
               ", limit=" + limit +
               '}';
    }
}
