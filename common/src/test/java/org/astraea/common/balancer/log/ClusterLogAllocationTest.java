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
package org.astraea.common.balancer.log;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.astraea.common.Utils;
import org.astraea.common.admin.Admin;
import org.astraea.common.admin.ClusterInfo;
import org.astraea.common.admin.NodeInfo;
import org.astraea.common.admin.Replica;
import org.astraea.common.admin.TopicPartition;
import org.astraea.it.RequireBrokerCluster;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClusterLogAllocationTest extends RequireBrokerCluster {

  private Set<TopicPartition> generateRandomTopicPartition() {
    return IntStream.range(0, 30)
        .mapToObj(i -> "topic-" + i)
        .map(topic -> TopicPartition.of(topic, ThreadLocalRandom.current().nextInt(0, 100)))
        .limit(30)
        .collect(Collectors.toUnmodifiableSet());
  }

  private Set<Replica> generateFakeReplica(TopicPartition tp, int replicas) {
    return IntStream.range(0, replicas)
        .mapToObj(
            rIndex ->
                Replica.builder()
                    .topic(tp.topic())
                    .partition(tp.partition())
                    .nodeInfo(NodeInfo.of(rIndex, "hostname" + rIndex, 9092))
                    .lag(0)
                    .size(ThreadLocalRandom.current().nextInt(0, 30000000))
                    .isLeader(rIndex == 0)
                    .inSync(true)
                    .isFuture(false)
                    .isOffline(false)
                    .isPreferredLeader(rIndex == 0)
                    .path("/tmp/dir0")
                    .build())
        .collect(Collectors.toUnmodifiableSet());
  }

  private List<Replica> generateRandomReplicaList(
      Set<TopicPartition> topicPartitions, short replicas) {
    return topicPartitions.stream()
        .flatMap(tp -> generateFakeReplica(tp, replicas).stream())
        .collect(Collectors.toUnmodifiableList());
  }

  private Replica update(Replica baseReplica, Map<String, Object> override) {
    return Replica.builder()
        .topic((String) override.getOrDefault("topic", baseReplica.topic()))
        .partition((int) override.getOrDefault("partition", baseReplica.partition()))
        .nodeInfo(
            NodeInfo.of((int) override.getOrDefault("broker", baseReplica.nodeInfo().id()), "", -1))
        .lag((long) override.getOrDefault("lag", baseReplica.lag()))
        .size((long) override.getOrDefault("size", baseReplica.size()))
        .isLeader((boolean) override.getOrDefault("leader", baseReplica.isLeader()))
        .inSync((boolean) override.getOrDefault("synced", baseReplica.inSync()))
        .isFuture((boolean) override.getOrDefault("future", baseReplica.isFuture()))
        .isOffline((boolean) override.getOrDefault("offline", baseReplica.isOffline()))
        .isPreferredLeader(
            (boolean) override.getOrDefault("preferred", baseReplica.isPreferredLeader()))
        .path((String) override.getOrDefault("dir", "/tmp/default/dir"))
        .build();
  }

  @ParameterizedTest
  @DisplayName("Create CLA from ClusterInfo")
  @ValueSource(shorts = {1, 2, 3})
  void testOfClusterInfo(short replicas) {
    // arrange
    try (var admin = Admin.of(bootstrapServers())) {
      var topic0 = Utils.randomString();
      var topic1 = Utils.randomString();
      var topic2 = Utils.randomString();
      var topics = Set.of(topic0, topic1, topic2);
      var partitions = ThreadLocalRandom.current().nextInt(10, 30);
      topics.forEach(
          topic ->
              admin
                  .creator()
                  .topic(topic)
                  .numberOfPartitions(partitions)
                  .numberOfReplicas(replicas)
                  .run()
                  .toCompletableFuture()
                  .join());
      Utils.sleep(Duration.ofSeconds(1));

      // act
      final var cla =
          ClusterLogAllocation.of(admin.clusterInfo(topics).toCompletableFuture().join());

      // assert
      final var expectedPartitions =
          topics.stream()
              .flatMap(
                  topic ->
                      IntStream.range(0, partitions).mapToObj(p -> TopicPartition.of(topic, p)))
              .collect(Collectors.toUnmodifiableSet());
      Assertions.assertEquals(expectedPartitions, cla.topicPartitions());
      Assertions.assertEquals(topics.size() * partitions * replicas, cla.replicas().size());
      expectedPartitions.forEach(tp -> Assertions.assertEquals(replicas, cla.replicas(tp).size()));
    }
  }

  @ParameterizedTest
  @DisplayName("Create CLA from replica list")
  @ValueSource(shorts = {1, 2, 3, 4, 5, 30})
  void testOfReplicaList(short replicas) {
    // arrange
    final var randomTopicPartitions = generateRandomTopicPartition();
    final var randomReplicas = generateRandomReplicaList(randomTopicPartitions, replicas);

    // act
    final var cla = ClusterLogAllocation.of(ClusterInfo.of(randomReplicas));

    // assert
    Assertions.assertEquals(randomReplicas.size(), cla.replicas().size());
    Assertions.assertEquals(List.copyOf(randomReplicas), cla.replicas());
    Assertions.assertEquals(randomTopicPartitions, cla.topicPartitions());
  }

  @Test
  void testOfBadReplicaList() {
    // arrange
    var topic = Utils.randomString();
    var partition = 30;
    var nodeInfo = NodeInfo.of(0, "", -1);
    Replica base =
        Replica.builder()
            .topic(topic)
            .partition(partition)
            .nodeInfo(nodeInfo)
            .lag(0)
            .size(0)
            .isLeader(true)
            .inSync(false)
            .isFuture(false)
            .isOffline(false)
            .isPreferredLeader(true)
            .path("/tmp/default/dir")
            .build();
    Replica leader0 =
        Replica.builder(base).nodeInfo(NodeInfo.of(3, "", -1)).isPreferredLeader(true).build();
    Replica leader1 =
        Replica.builder(base).nodeInfo(NodeInfo.of(4, "", -1)).isPreferredLeader(true).build();
    Replica follower2 =
        Replica.builder(base).nodeInfo(NodeInfo.of(4, "", -1)).isPreferredLeader(false).build();
    Replica isFuture = Replica.builder(base).isFuture(true).build();
    Replica notFuture = Replica.builder(base).isFuture(false).build();

    // act, assert
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> ClusterLogAllocation.of(ClusterInfo.of(List.of(isFuture, notFuture))),
        "No ongoing rebalance");
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> ClusterLogAllocation.of(ClusterInfo.of(List.of(leader0, leader1))),
        "duplicate preferred leader in list");
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> ClusterLogAllocation.of(ClusterInfo.of(List.of(follower2))),
        "no preferred leader");
  }

  @Test
  @DisplayName("Migrate some replicas without directory specified")
  void testMigrateReplicaNoDir() {
    // arrange
    final var randomTopicPartitions = generateRandomTopicPartition();
    final var randomReplicas = generateRandomReplicaList(randomTopicPartitions, (short) 3);
    final var allocation = ClusterLogAllocation.of(ClusterInfo.of(randomReplicas));
    final var target = randomReplicas.stream().findAny().orElseThrow();

    // act, assert
    Assertions.assertThrows(
        NullPointerException.class,
        () -> allocation.migrateReplica(target.topicPartitionReplica(), 9999, null));
  }

  @ParameterizedTest
  @DisplayName("Migrate some replicas")
  @ValueSource(shorts = {1, 2, 3, 4, 5, 30})
  void testMigrateReplica(short replicas) {
    // arrange
    final var randomTopicPartitions = generateRandomTopicPartition();
    final var randomReplicas = generateRandomReplicaList(randomTopicPartitions, replicas);
    final var allocation = ClusterLogAllocation.of(ClusterInfo.of(randomReplicas));
    final var target = randomReplicas.stream().findAny().orElseThrow();

    // act
    final var cla = allocation.migrateReplica(target.topicPartitionReplica(), 9999, "/the/dir");

    // assert
    Assertions.assertEquals(
        replicas, cla.replicas(target.topicPartition()).size(), "No replica factor shrinkage");
    Assertions.assertTrue(
        cla.replicas(target.topicPartition()).stream()
            .anyMatch(replica -> replica.nodeInfo().id() == 9999),
        "The replica is here");
    Assertions.assertTrue(
        cla.replicas(target.topicPartition()).stream()
            .noneMatch(replica -> replica.nodeInfo().id() == target.nodeInfo().id()),
        "The original replica is gone");
    Assertions.assertEquals(
        "/the/dir",
        cla.replicas(target.topicPartition()).stream()
            .filter(replica -> replica.nodeInfo().id() == 9999)
            .findFirst()
            .orElseThrow()
            .path());
  }

  @ParameterizedTest
  @DisplayName("Become leader")
  @ValueSource(shorts = {1, 2, 3, 4, 5, 30})
  void testBecomeLeader(short replicas) {
    // arrange
    final var randomTopicPartitions = generateRandomTopicPartition();
    final var randomReplicas = generateRandomReplicaList(randomTopicPartitions, replicas);
    final var allocation = ClusterLogAllocation.of(ClusterInfo.of(randomReplicas));
    final var target = randomReplicas.stream().findAny().orElseThrow();
    final var theTopicPartition = target.topicPartition();
    final var originalReplicaList = allocation.replicas(theTopicPartition);
    final var originalLeader =
        originalReplicaList.stream().filter(Replica::isPreferredLeader).findFirst().orElseThrow();

    // act
    final var cla = allocation.becomeLeader(target.topicPartitionReplica());

    // assert
    Assertions.assertEquals(
        replicas, cla.replicas(theTopicPartition).size(), "No replica factor shrinkage");
    if (target.isLeader()) {
      // let leader become a leader, nothing changed
      Assertions.assertEquals(
          originalReplicaList,
          allocation.replicas(theTopicPartition),
          "Nothing changed since target is already the leader");
    } else {
      Assertions.assertTrue(
          cla.replicas(theTopicPartition).stream()
              .filter(r -> r.nodeInfo().equals(target.nodeInfo()))
              .findFirst()
              .orElseThrow()
              .isPreferredLeader(),
          "target become the new preferred leader");
      Assertions.assertFalse(
          cla.replicas(theTopicPartition).stream()
              .filter(r -> r.nodeInfo().equals(originalLeader.nodeInfo()))
              .findFirst()
              .orElseThrow()
              .isPreferredLeader(),
          "original leader lost its identity");
      Assertions.assertEquals(
          target.size(),
          cla.replicas(theTopicPartition).stream()
              .filter(r -> r.nodeInfo().equals(target.nodeInfo()))
              .findFirst()
              .orElseThrow()
              .size(),
          "Only the preferred leader field get updated, no change to other fields");
      Assertions.assertEquals(
          originalLeader.size(),
          cla.replicas(theTopicPartition).stream()
              .filter(r -> r.nodeInfo().equals(originalLeader.nodeInfo()))
              .findFirst()
              .orElseThrow()
              .size(),
          "Only the preferred leader field get updated, no change to other fields");
    }
  }

  @ParameterizedTest
  @DisplayName("placements")
  @ValueSource(shorts = {1, 2, 3, 4, 5, 30})
  void testReplicas(short replicas) {
    // arrange
    final var randomTopicPartitions = generateRandomTopicPartition();
    final var randomReplicas = generateRandomReplicaList(randomTopicPartitions, replicas);

    // act
    final var allocation = ClusterLogAllocation.of(ClusterInfo.of(randomReplicas));

    // assert
    Assertions.assertEquals(List.copyOf(randomReplicas), allocation.replicas());
    for (var tp : randomTopicPartitions) {
      final var expected =
          randomReplicas.stream()
              .filter(r -> r.topicPartition().equals(tp))
              .collect(Collectors.toList());
      Assertions.assertEquals(expected, allocation.replicas(tp));
    }
  }

  @ParameterizedTest
  @DisplayName("placements")
  @ValueSource(shorts = {1, 2, 3, 4, 5, 30})
  void testTopicPartitions(short replicas) {
    // arrange
    final var randomTopicPartitions = generateRandomTopicPartition();
    final var randomReplicas = generateRandomReplicaList(randomTopicPartitions, replicas);

    // act
    final var allocation = ClusterLogAllocation.of(ClusterInfo.of(randomReplicas));

    // assert
    Assertions.assertEquals(randomTopicPartitions, allocation.topicPartitions());
  }

  @Test
  void testPlacementMatch() {
    var topic = Utils.randomString();
    var partition = 30;
    var nodeInfo = NodeInfo.of(0, "", -1);

    Replica base =
        Replica.builder()
            .topic(topic)
            .partition(partition)
            .nodeInfo(nodeInfo)
            .lag(0)
            .size(0)
            .isLeader(false)
            .inSync(false)
            .isFuture(false)
            .isOffline(false)
            .isPreferredLeader(false)
            .path("/tmp/default/dir")
            .build();

    {
      // self equal
      var leader0 = update(base, Map.of("leader", true, "preferred", true));
      var follower1 = update(base, Map.of("broker", 1));
      var follower2 = update(base, Map.of("broker", 2));
      Assertions.assertTrue(
          ClusterInfo.placementMatch(
              Set.of(leader0, follower1, follower2), Set.of(leader0, follower1, follower2)),
          "Self equal");
    }

    {
      // unrelated field does nothing
      var leader0 = update(base, Map.of("leader", true, "preferred", true));
      var follower1 = update(base, Map.of("broker", 1));
      var follower2 = update(base, Map.of("broker", 2));
      var awkwardFollower2 = update(follower2, Map.of("size", 123456789L));
      Assertions.assertTrue(
          ClusterInfo.placementMatch(
              Set.of(leader0, follower1, follower2), Set.of(leader0, follower1, awkwardFollower2)),
          "Size field is unrelated to placement");
    }

    {
      // preferred leader changed
      var leaderA0 = update(base, Map.of("leader", true, "preferred", true));
      var followerA1 = update(base, Map.of("broker", 1));
      var followerA2 = update(base, Map.of("broker", 2));
      var followerB0 = update(leaderA0, Map.of("preferred", false));
      var leaderB1 = update(followerA1, Map.of("preferred", true));
      var followerB2 = update(followerA2, Map.of());
      Assertions.assertFalse(
          ClusterInfo.placementMatch(
              Set.of(leaderA0, followerA1, followerA2), Set.of(leaderB1, followerB0, followerB2)),
          "Size field is unrelated to placement");
    }

    {
      // data dir changed
      var leader0 = update(base, Map.of("leader", true, "preferred", true));
      var follower1 = update(base, Map.of("broker", 1));
      var follower2 = update(base, Map.of("broker", 2));
      var alteredFollower2 = update(follower2, Map.of("dir", "/tmp/somewhere"));
      Assertions.assertFalse(
          ClusterInfo.placementMatch(
              Set.of(leader0, follower1, follower2), Set.of(leader0, follower1, alteredFollower2)),
          "data dir changed");
    }

    {
      // replica migrated
      var leader0 = update(base, Map.of("leader", true, "preferred", true));
      var follower1 = update(base, Map.of("broker", 1));
      var follower2 = update(base, Map.of("broker", 2));
      var alteredFollower2 = update(follower2, Map.of("broker", 3));
      Assertions.assertFalse(
          ClusterInfo.placementMatch(
              Set.of(leader0, follower1, follower2), Set.of(leader0, follower1, alteredFollower2)),
          "migrate data dir");
    }

    {
      // null dir in target set mean always bad
      var leader0 = update(base, Map.of("leader", true, "preferred", true));
      var follower1 = update(base, Map.of("broker", 1));
      var nullMap = new HashMap<String, Object>();
      nullMap.put("dir", null);
      var sourceFollower1 = update(follower1, Map.of("dir", "/target"));
      var targetFollower1 = update(follower1, nullMap);
      Assertions.assertFalse(
          ClusterInfo.placementMatch(
              Set.of(leader0, sourceFollower1), Set.of(leader0, targetFollower1)));
    }
  }

  @Test
  void testFindNonFulfilledAllocation() {
    var topic = Utils.randomString();
    var partition = 30;
    var nodeInfo = NodeInfo.of(0, "", -1);

    Replica baseLeader =
        Replica.builder()
            .topic(topic)
            .partition(partition)
            .nodeInfo(nodeInfo)
            .lag(0)
            .size(0)
            .isLeader(true)
            .inSync(true)
            .isFuture(false)
            .isOffline(false)
            .isPreferredLeader(true)
            .path("/tmp/default/dir")
            .build();

    {
      // self equal
      var leader0 = update(baseLeader, Map.of());
      var follower1 = update(baseLeader, Map.of("broker", 1, "leader", false, "preferred", false));
      var follower2 = update(baseLeader, Map.of("broker", 2, "leader", false, "preferred", false));
      Assertions.assertEquals(
          Set.of(),
          ClusterInfo.findNonFulfilledAllocation(
              ClusterInfo.of(List.of(leader0, follower1, follower2)),
              ClusterInfo.of(List.of(leader0, follower1, follower2))));
    }

    {
      // one alteration
      var leader0 = update(baseLeader, Map.of());
      var follower1 = update(baseLeader, Map.of("broker", 1, "leader", false, "preferred", false));
      var follower2 = update(baseLeader, Map.of("broker", 2, "leader", false, "preferred", false));
      var alteredFollower2 = update(follower2, Map.of("broker", 3));
      Assertions.assertEquals(
          Set.of(alteredFollower2.topicPartition()),
          ClusterInfo.findNonFulfilledAllocation(
              ClusterInfo.of(List.of(leader0, follower1, follower2)),
              ClusterInfo.of(List.of(leader0, follower1, alteredFollower2))));
    }

    {
      // two alteration
      var leader0 = update(baseLeader, Map.of());
      var leader1 = update(baseLeader, Map.of("topic", "BBB"));
      var leader2 = update(baseLeader, Map.of("topic", "CCC"));
      var alteredLeader1 = update(baseLeader, Map.of("topic", "BBB", "broker", 4));
      var alteredLeader2 = update(baseLeader, Map.of("topic", "CCC", "broker", 5));
      Assertions.assertEquals(
          Set.of(alteredLeader1.topicPartition(), alteredLeader2.topicPartition()),
          ClusterInfo.findNonFulfilledAllocation(
              ClusterInfo.of(List.of(leader0, leader1, leader2)),
              ClusterInfo.of(List.of(leader0, alteredLeader1, alteredLeader2))));
    }
  }

  @Test
  @DisplayName("the source CLA should be a subset of target CLA")
  void testFindNonFulfilledAllocationException() {
    var topic = Utils.randomString();
    var partition = 30;
    var nodeInfo = NodeInfo.of(0, "", -1);

    Replica baseLeader =
        Replica.builder()
            .topic(topic)
            .partition(partition)
            .nodeInfo(nodeInfo)
            .lag(0)
            .size(0)
            .isLeader(true)
            .inSync(false)
            .isFuture(false)
            .isOffline(false)
            .isPreferredLeader(true)
            .path("/tmp/default/dir")
            .build();

    {
      var leader0 = update(baseLeader, Map.of());
      var follower1 = update(baseLeader, Map.of("broker", 1, "leader", false, "preferred", false));
      var follower2 = update(baseLeader, Map.of("broker", 2, "leader", false, "preferred", false));
      var other = update(baseLeader, Map.of("topic", "AnotherTopic"));
      Assertions.assertThrows(
          IllegalArgumentException.class,
          () ->
              ClusterInfo.findNonFulfilledAllocation(
                  ClusterInfo.of(List.of(leader0, follower1, follower2)),
                  ClusterInfo.of(List.of(leader0, follower1, follower2, other))));
    }
  }

  @Test
  void testMovingReplicas() {
    var topic = Utils.randomString();
    var partition = 30;
    var nodeInfo = NodeInfo.of(0, "", -1);
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            ClusterLogAllocation.of(
                ClusterInfo.of(
                    List.of(
                        Replica.builder()
                            .topic(topic)
                            .nodeInfo(nodeInfo)
                            .isAdding(true)
                            .isPreferredLeader(true)
                            .build()))));

    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            ClusterLogAllocation.of(
                ClusterInfo.of(
                    List.of(
                        Replica.builder()
                            .topic(topic)
                            .nodeInfo(nodeInfo)
                            .isRemoving(true)
                            .isPreferredLeader(true)
                            .build()))));

    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            ClusterLogAllocation.of(
                ClusterInfo.of(
                    List.of(
                        Replica.builder()
                            .topic(topic)
                            .nodeInfo(nodeInfo)
                            .isFuture(true)
                            .isPreferredLeader(true)
                            .build()))));

    Assertions.assertDoesNotThrow(
        () ->
            ClusterLogAllocation.of(
                ClusterInfo.of(
                    List.of(
                        Replica.builder()
                            .topic(topic)
                            .nodeInfo(nodeInfo)
                            .isRemoving(false)
                            .isAdding(false)
                            .isFuture(false)
                            .isPreferredLeader(true)
                            .build()))));
  }
}
