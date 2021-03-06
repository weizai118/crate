/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.analyze.relations;

import io.crate.planner.node.dql.join.JoinType;
import io.crate.sql.tree.QualifiedName;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;

public final class JoinPairs {

    /**
     * Finds a JoinPair within pair where pair.left = lhs and pair.right = rhs
     */
    @Nullable
    public static JoinPair exactFindPair(Collection<JoinPair> pairs, QualifiedName lhs, QualifiedName rhs) {
        for (JoinPair pair : pairs) {
            if (pair.equalsNames(lhs, rhs)) {
                return pair;
            }
        }
        return null;
    }

    /**
     * Finds a JoinPair within pairs where either
     *
     *  - pair.left = lhs and pair.right = rhs
     *  - or pair.left = rhs and pair.right = lhs, in which case a reversed pair is returned (and the original pair in the list is replaced)
     */
    @Nullable
    public static JoinPair fuzzyFindPair(List<JoinPair> pairs, QualifiedName lhs, QualifiedName rhs) {
        JoinPair exactMatch = exactFindPair(pairs, lhs, rhs);
        if (exactMatch == null) {
            ListIterator<JoinPair> it = pairs.listIterator();
            while (it.hasNext()) {
                JoinPair pair = it.next();
                JoinType joinType = pair.joinType();
                if (joinType.supportsInversion() && pair.equalsNames(rhs, lhs)) {
                    JoinPair reversed = pair.reverse();
                    it.set(reversed); // change list entry so that the found entry can be removed from pairs
                    return reversed;
                }
            }
        }
        return exactMatch;
    }

    /**
     * Returns true if relation name is part of an outer join and on the outer side.
     */
    static boolean isOuterRelation(QualifiedName name, List<JoinPair> joinPairs) {
        for (JoinPair joinPair : joinPairs) {
            if (joinPair.isOuterRelation(name)) {
                return true;
            }
        }
        return false;
    }
}
