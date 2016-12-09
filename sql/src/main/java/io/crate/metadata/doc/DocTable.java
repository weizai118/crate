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

package io.crate.metadata.doc;

import io.crate.Constants;
import io.crate.analyze.NumberOfReplicas;
import io.crate.analyze.TableParameterInfo;
import io.crate.metadata.TableIdent;
import io.crate.metadata.table.ColumnPolicy;
import io.crate.metadata.table.Operation;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.settings.Settings;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class DocTable {

    public static DocTable fromIndexMetaData(TableIdent ident, IndexMetaData indexMetaData) throws IOException {
        Settings settings = indexMetaData.getSettings();
        MappingMetaData mapping = indexMetaData.mappingOrDefault(Constants.DEFAULT_MAPPING_TYPE);
        Map<String, Object> typeMapping;
        if (mapping == null) {
            typeMapping = Collections.emptyMap();
        } else {
            typeMapping = mapping.sourceAsMap();
        }
        return new DocTable(
            ident,
            columns,
            generatedColumns,
            indexColumns,
            references,
            analyzers,
            primaryKeys,
            clusteredBy,
            hasAutoGeneratedPrimaryKey,
            indexMetaData.getIndex(),
            indexMetaData.getNumberOfShards(),
            NumberOfReplicas.fromSettings(settings),
            TableParameterInfo.tableParametersFromIndexMetaData(indexMetaData),
            ColumnPolicy.fromTypeMapping(typeMapping),
            Operation.buildFromIndexSettings(settings)
        );
    }
}