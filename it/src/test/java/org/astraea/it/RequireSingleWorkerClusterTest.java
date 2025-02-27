/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.astraea.it;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RequireSingleWorkerClusterTest extends RequireSingleWorkerCluster {

  @Test
  void testProperties() {
    assertNotNull(workerUrl());
    assertTrue(workerUrl().getPort() > 0);
    assertNotEquals("0.0.0.0", workerUrl().getHost());
    assertNotEquals("127.0.0.1", workerUrl().getHost());
    assertNotNull(bootstrapServers());
  }

  @Test
  void testConnection() throws Exception {
    var jsonTree = new ObjectMapper().readTree(workerUrl());
    assertNotNull(jsonTree.get("version"));
    assertNotNull(jsonTree.get("kafka_cluster_id"));
  }
}
