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
package org.astraea.connector;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.kafka.common.config.ConfigDef;
import org.astraea.common.Configuration;
import org.astraea.common.VersionUtils;

public abstract class SourceConnector extends org.apache.kafka.connect.source.SourceConnector {

  protected void init(Configuration configuration) {
    // empty
  }

  protected abstract Class<? extends SourceTask> task();

  protected abstract List<Configuration> takeConfiguration(int maxTasks);

  protected abstract List<Definition> definitions();

  protected void close() {
    // empty
  }

  // -------------------------[final]-------------------------//
  @Override
  public final void start(Map<String, String> props) {
    init(Configuration.of(props));
  }

  @Override
  public final Class<? extends org.apache.kafka.connect.source.SourceTask> taskClass() {
    return task();
  }

  @Override
  public List<Map<String, String>> taskConfigs(int maxTasks) {
    return takeConfiguration(maxTasks).stream()
        .map(Configuration::raw)
        .collect(Collectors.toList());
  }

  @Override
  public final void stop() {
    close();
  }

  @Override
  public final ConfigDef config() {
    var def = new ConfigDef();
    definitions()
        .forEach(
            d ->
                def.define(
                    d.name(),
                    ConfigDef.Type.valueOf(d.type().name()),
                    d.defaultValue(),
                    ConfigDef.Importance.MEDIUM,
                    d.documentation()));
    return def;
  }

  @Override
  public final String version() {
    return VersionUtils.VERSION;
  }
}
