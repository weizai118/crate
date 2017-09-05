/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.analyze;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.crate.analyze.relations.QueriedRelation;
import io.crate.analyze.symbol.Function;
import io.crate.analyze.symbol.InputColumn;
import io.crate.analyze.symbol.Literal;
import io.crate.analyze.symbol.Symbol;
import io.crate.metadata.ColumnIdent;
import io.crate.metadata.FunctionIdent;
import io.crate.metadata.FunctionInfo;
import io.crate.metadata.GeneratedReference;
import io.crate.metadata.Reference;
import io.crate.metadata.doc.DocTableInfo;
import io.crate.operation.scalar.SubscriptObjectFunction;
import io.crate.planner.projection.builder.InputColumns;
import io.crate.types.DataType;
import io.crate.types.DataTypes;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;


public class InsertFromSubQueryAnalyzedStatement implements AnalyzedStatement {

    private final DocTableInfo targetTable;
    private final QueriedRelation subQueryRelation;

    @Nullable
    private final Map<Reference, Symbol> onDuplicateKeyAssignments;
    private final List<Reference> targetColumns;
    private final List<Symbol> primaryKeySymbols;
    private final List<Symbol> partitionedBySymbols;
    @Nullable
    private final Symbol clusteredBySymbol;

    public InsertFromSubQueryAnalyzedStatement(QueriedRelation subQueryRelation,
                                               DocTableInfo tableInfo,
                                               List<Reference> targetColumns,
                                               @Nullable Map<Reference, Symbol> onDuplicateKeyAssignments) {
        this.targetTable = tableInfo;
        this.subQueryRelation = subQueryRelation;
        this.onDuplicateKeyAssignments = onDuplicateKeyAssignments;
        this.targetColumns = targetColumns;
        Map<ColumnIdent, Integer> columnPositions = toPositionMap(targetColumns);


        int clusteredByIdx = MoreObjects.firstNonNull(columnPositions.get(tableInfo.clusteredBy()), -1);
        if (clusteredByIdx > -1) {
            clusteredBySymbol = new InputColumn(clusteredByIdx,  targetColumns.get(clusteredByIdx).valueType());
        } else {
            clusteredBySymbol = null;
        }
        ImmutableMap<ColumnIdent, GeneratedReference> generatedColumns =
            Maps.uniqueIndex(tableInfo.generatedColumns(), Reference.TO_COLUMN_IDENT::apply);

        if (tableInfo.hasAutoGeneratedPrimaryKey()) {
            this.primaryKeySymbols = Collections.emptyList();
        } else {
            this.primaryKeySymbols = symbolsFromTargetColumnPositionOrGeneratedExpression(
                columnPositions, targetColumns, tableInfo.primaryKey(), generatedColumns);
        }
        this.partitionedBySymbols = symbolsFromTargetColumnPositionOrGeneratedExpression(
            columnPositions, targetColumns, tableInfo.partitionedBy(), generatedColumns);
    }

    private static Map<ColumnIdent, Integer> toPositionMap(List<Reference> targetColumns) {
        if (targetColumns.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<ColumnIdent, Integer> columnPositions = new HashMap<>(targetColumns.size(), 1);
        ListIterator<Reference> it = targetColumns.listIterator();
        while (it.hasNext()) {
            columnPositions.put(it.next().column(), it.previousIndex());
        }
        return columnPositions;
    }

    private List<Symbol> symbolsFromTargetColumnPositionOrGeneratedExpression(Map<ColumnIdent, Integer> targetColumnMap,
                                                                              List<Reference> targetColumns,
                                                                              List<ColumnIdent> columns,
                                                                              Map<ColumnIdent, GeneratedReference> generatedColumns) {
        if (columns.isEmpty()) {
            return Collections.emptyList();
        }

        List<Symbol> symbols = new ArrayList<>(columns.size());
        InputColumns.Context inputContext = null;
        for (ColumnIdent column : columns) {
            ColumnIdent subscriptColumn = null;
            if (!column.isColumn()) {
                subscriptColumn = column;
                column = column.getRoot();
            }
            Integer colPosition = targetColumnMap.get(column);
            if (colPosition != null) {
                Symbol symbol = new InputColumn(colPosition, targetColumns.get(colPosition).valueType());
                if (subscriptColumn != null) {
                    symbol = rewriteNestedInputToSubscript(subscriptColumn, symbol);
                }
                symbols.add(symbol);
            } else {
                GeneratedReference generatedReference = generatedColumns.get(column);
                if (generatedReference == null) {
                    throw new IllegalArgumentException(String.format(Locale.ENGLISH,
                        "Column \"%s\" is required but is missing from the insert statement", column.sqlFqn()));
                }

                if (inputContext == null) {
                    inputContext = new InputColumns.Context(targetColumns);
                }
                Symbol symbol = InputColumns.create(generatedReference.generatedExpression(), inputContext);
                symbols.add(symbol);
            }
        }
        return symbols;
    }

    private Symbol rewriteNestedInputToSubscript(ColumnIdent columnIdent, Symbol inputSymbol) {
        Reference reference = tableInfo().getReference(columnIdent);
        Symbol symbol = inputSymbol;
        Iterator<String> pathIt = columnIdent.path().iterator();
        while (pathIt.hasNext()) {
            // rewrite object access to subscript scalar
            String key = pathIt.next();
            DataType returnType = DataTypes.OBJECT;
            if (!pathIt.hasNext()) {
                returnType = reference.valueType();
            }

            FunctionIdent functionIdent = new FunctionIdent(SubscriptObjectFunction.NAME,
                ImmutableList.<DataType>of(DataTypes.OBJECT, DataTypes.STRING));
            symbol = new Function(new FunctionInfo(functionIdent, returnType),
                Arrays.asList(symbol, Literal.of(key)));
        }
        return symbol;
    }

    public List<Reference> columns() {
        return targetColumns;
    }

    public DocTableInfo tableInfo() {
        return targetTable;
    }

    public QueriedRelation subQueryRelation() {
        return this.subQueryRelation;
    }

    @Override
    public <C, R> R accept(AnalyzedStatementVisitor<C, R> analyzedStatementVisitor, C context) {
        return analyzedStatementVisitor.visitInsertFromSubQueryStatement(this, context);
    }

    @Override
    public boolean isWriteOperation() {
        return true;
    }

    @Nullable
    public Map<Reference, Symbol> onDuplicateKeyAssignments() {
        return onDuplicateKeyAssignments;
    }

    public List<Symbol> primaryKeySymbols() {
        return primaryKeySymbols;
    }

    public List<Symbol> partitionedBySymbols() {
        return partitionedBySymbols;
    }

    @Nullable
    public Symbol clusteredBySymbol() {
        return clusteredBySymbol;
    }
}
