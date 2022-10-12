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
package org.astraea.gui.tab;

import java.util.Map;
import java.util.stream.Collectors;
import org.astraea.common.LinkedHashMap;
import org.astraea.gui.Context;
import org.astraea.gui.button.RadioButtonAble;
import org.astraea.gui.pane.PaneBuilder;
import org.astraea.gui.pane.Tab;

public class ConfigTab {

  private enum Resource implements RadioButtonAble {
    BROKER("broker"),
    TOPIC("topic");

    private final String display;

    Resource(String display) {
      this.display = display;
    }

    @Override
    public String display() {
      return display;
    }
  }

  public static Tab of(Context context) {
    var pane =
        PaneBuilder.of()
            .radioButtons(Resource.values())
            .searchField("config key")
            .buttonAction(
                (input, logger) -> {
                  var resource =
                      input.selectedRadio().map(o -> (Resource) o).orElse(Resource.TOPIC);
                  return context
                      .submit(
                          admin ->
                              resource == Resource.TOPIC
                                  ? admin
                                      .topicNames(true)
                                      .thenCompose(admin::topics)
                                      .thenApply(
                                          topics ->
                                              topics.stream()
                                                  .map(t -> Map.entry(t.name(), t.config())))
                                  : admin
                                      .brokers()
                                      .thenApply(
                                          brokers ->
                                              brokers.stream()
                                                  .map(
                                                      t ->
                                                          Map.entry(
                                                              String.valueOf(t.id()), t.config()))))
                      .thenApply(
                          items ->
                              items
                                  .map(
                                      e -> {
                                        var map = new LinkedHashMap<String, Object>();
                                        map.put(
                                            resource == Resource.TOPIC ? "topic" : "broker id",
                                            e.getKey());
                                        e.getValue().raw().entrySet().stream()
                                            .filter(entry -> input.matchSearch(entry.getKey()))
                                            .sorted(Map.Entry.comparingByKey())
                                            .forEach(
                                                entry -> map.put(entry.getKey(), entry.getValue()));
                                        return map;
                                      })
                                  .collect(Collectors.toList()));
                })
            .build();
    return Tab.of("config", pane);
  }
}