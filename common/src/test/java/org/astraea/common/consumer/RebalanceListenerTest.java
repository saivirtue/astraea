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
package org.astraea.common.consumer;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.astraea.common.Utils;
import org.astraea.it.RequireBrokerCluster;
import org.junit.jupiter.api.Test;

public class RebalanceListenerTest extends RequireBrokerCluster {

  @Test
  void testConsumerRebalanceListener() {
    var getAssignment = new AtomicInteger(0);
    var topicName = "testRebalanceListener-" + System.currentTimeMillis();
    try (var consumer =
        Consumer.forTopics(Set.of(topicName))
            .bootstrapServers(bootstrapServers())
            .consumerRebalanceListener(ignore -> getAssignment.incrementAndGet())
            .build()) {
      Utils.waitFor(
          () -> {
            consumer.poll(Duration.ofSeconds(1));
            return getAssignment.get() == 1;
          },
          Duration.ofSeconds(10));
    }
  }
}
