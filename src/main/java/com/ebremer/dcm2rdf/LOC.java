/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ebremer.dcm2rdf;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;

public class LOC {

    public static class BibFrame {
        public static final String NS = "http://id.loc.gov/ontologies/bibframe/";
        public static final Property FileSize = ResourceFactory.createProperty(NS+"FileSize");
    }
    
    public static class cryptographicHashFunctions {
        public static final String NS = "http://id.loc.gov/vocabulary/preservation/cryptographicHashFunctions/";
        public static final Property sha256 = ResourceFactory.createProperty(NS+"sha256");
    }

}
