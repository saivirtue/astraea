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
package org.astraea.common.metrics.client.consumer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.astraea.common.metrics.AppInfo;
import org.astraea.common.metrics.BeanObject;
import org.astraea.common.metrics.BeanQuery;
import org.astraea.common.metrics.MBeanClient;
import org.astraea.common.metrics.client.HasNodeMetrics;

public class ConsumerMetrics {

  public static List<AppInfo> appInfo(MBeanClient client) {
    return client
        .queryBeans(
            BeanQuery.builder()
                .domainName("kafka.consumer")
                .property("type", "app-info")
                .property("client-id", "*")
                .build())
        .stream()
        .map(
            obj ->
                new AppInfo() {
                  @Override
                  public String id() {
                    return beanObject().properties().get("client-id");
                  }

                  @Override
                  public String commitId() {
                    return (String) beanObject().attributes().get("commit-id");
                  }

                  @Override
                  public Optional<Long> startTimeMs() {
                    var t = beanObject().attributes().get("start-time-ms");
                    if (t == null) return Optional.empty();
                    return Optional.of((long) t);
                  }

                  @Override
                  public String version() {
                    return (String) beanObject().attributes().get("version");
                  }

                  @Override
                  public BeanObject beanObject() {
                    return obj;
                  }
                })
        .collect(Collectors.toList());
  }

  /**
   * collect HasNodeMetrics from all consumers.
   *
   * @param mBeanClient to query metrics
   * @return key is broker id, and value is associated to broker metrics recorded by all consumers
   */
  public static Collection<HasNodeMetrics> nodes(MBeanClient mBeanClient) {
    Function<String, Integer> brokerId =
        node -> Integer.parseInt(node.substring(node.indexOf("-") + 1));
    return mBeanClient
        .queryBeans(
            BeanQuery.builder()
                .domainName("kafka.consumer")
                .property("type", "consumer-node-metrics")
                .property("node-id", "*")
                .property("client-id", "*")
                .build())
        .stream()
        .map(b -> (HasNodeMetrics) () -> b)
        .collect(Collectors.toUnmodifiableList());
  }

  public static Collection<HasConsumerCoordinatorMetrics> coordinators(MBeanClient mBeanClient) {
    return mBeanClient
        .queryBeans(
            BeanQuery.builder()
                .domainName("kafka.consumer")
                .property("type", "consumer-coordinator-metrics")
                .property("client-id", "*")
                .build())
        .stream()
        .map(b -> (HasConsumerCoordinatorMetrics) () -> b)
        .collect(Collectors.toUnmodifiableList());
  }

  public static Collection<HasConsumerFetchMetrics> fetches(MBeanClient mBeanClient) {
    return mBeanClient
        .queryBeans(
            BeanQuery.builder()
                .domainName("kafka.consumer")
                .property("type", "consumer-fetch-manager-metrics")
                .property("client-id", "*")
                .build())
        .stream()
        .map(b -> (HasConsumerFetchMetrics) () -> b)
        .collect(Collectors.toUnmodifiableList());
  }

  public static Collection<HasConsumerMetrics> of(MBeanClient mBeanClient) {
    return mBeanClient
        .queryBeans(
            BeanQuery.builder()
                .domainName("kafka.consumer")
                .property("type", "consumer-metrics")
                .property("client-id", "*")
                .build())
        .stream()
        .map(b -> (HasConsumerMetrics) () -> b)
        .collect(Collectors.toUnmodifiableList());
  }
}
