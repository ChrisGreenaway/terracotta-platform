/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terracotta.management.registry.collect;

import org.terracotta.context.ContextManager;
import org.terracotta.context.TreeNode;
import org.terracotta.context.extended.OperationStatisticDescriptor;
import org.terracotta.context.extended.ValueStatisticDescriptor;
import org.terracotta.context.query.Matcher;
import org.terracotta.context.query.Matchers;
import org.terracotta.management.model.capabilities.descriptors.StatisticDescriptor;
import org.terracotta.management.model.stats.MemoryUnit;
import org.terracotta.management.model.stats.NumberUnit;
import org.terracotta.management.model.stats.Statistic;
import org.terracotta.management.model.stats.primitive.Average;
import org.terracotta.management.model.stats.primitive.Counter;
import org.terracotta.management.model.stats.primitive.Duration;
import org.terracotta.management.model.stats.primitive.Rate;
import org.terracotta.management.model.stats.primitive.Ratio;
import org.terracotta.management.model.stats.primitive.Size;
import org.terracotta.statistics.OperationStatistic;
import org.terracotta.statistics.ValueStatistic;
import org.terracotta.statistics.extended.StatisticType;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static org.terracotta.context.query.Matchers.attributes;
import static org.terracotta.context.query.Matchers.context;
import static org.terracotta.context.query.Matchers.hasAttribute;
import static org.terracotta.context.query.Matchers.identifier;
import static org.terracotta.context.query.Matchers.subclassOf;
import static org.terracotta.context.query.QueryBuilder.queryBuilder;

/**
 * @author Mathieu Carbou
 */
public class StatisticRegistry {

  private final Object contextObject;
  private final Map<String, ValueStatistic<? extends Number>> statistics = new HashMap<String, ValueStatistic<? extends Number>>();
  private final Map<String, StatisticType> statisticTypes = new HashMap<String, StatisticType>();

  public StatisticRegistry(Object contextObject) {
    this.contextObject = contextObject; // allows null, in this case it will be a no-op registry
  }

  public Collection<StatisticDescriptor> getDescriptors() {
    Set<StatisticDescriptor> descriptors = new HashSet<StatisticDescriptor>();
    for (Map.Entry<String, ValueStatistic<? extends Number>> entry : statistics.entrySet()) {
      String fullStatName = entry.getKey();
      StatisticType type = statisticTypes.get(fullStatName);
      descriptors.add(new StatisticDescriptor(fullStatName, type.name()));
    }
    return descriptors;
  }

  public Statistic<?, ?> queryStatistic(String fullStatisticName) {
    ValueStatistic<? extends Number> statistic = statistics.get(fullStatisticName);
    if (statistic == null) {
      throw new IllegalArgumentException("No registered statistic named '" + fullStatisticName + "'");
    }
    StatisticType type = statisticTypes.get(fullStatisticName);
    switch (type) {
      case COUNTER: return new Counter(statistic.value().longValue(), NumberUnit.COUNT);
      case RATE: return new Rate(statistic.value().doubleValue(), TimeUnit.SECONDS);
      case RATIO: return new Ratio(statistic.value().doubleValue(), NumberUnit.RATIO);
      case SIZE: return new Size(statistic.value().longValue(), MemoryUnit.B);
      case LATENCY_MIN: return new Duration(statistic.value().longValue(), TimeUnit.MILLISECONDS);
      case LATENCY_MAX: return new Duration(statistic.value().longValue(), TimeUnit.MILLISECONDS);
      case LATENCY_AVG: return new Average(statistic.value().doubleValue(), TimeUnit.MILLISECONDS);
      default: throw new UnsupportedOperationException(type.name());
    }
  }

  public Map<String, Statistic<?, ?>> queryStatistics() {
    Map<String, Statistic<?, ?>> stats = new TreeMap<String, Statistic<?, ?>>();
    for (String fullStatName : statistics.keySet()) {
      stats.put(fullStatName, queryStatistic(fullStatName));
    }
    return stats;
  }

  /**
   * Directly register a SIZE stat with its accessor
   */
  public <N extends Number> void registerSize(String fullStatName, ValueStatistic<N> accessor) {
    registerStatistic(fullStatName, StatisticType.SIZE, accessor);
  }

  /**
   * Register one or several SIZE passthrough stats. Stat prefix is taken from the discriminator if any.
   */
  public void registerSize(String statNameSuffix, ValueStatisticDescriptor descriptor) {
    registerStatistic(statNameSuffix, StatisticType.SIZE, descriptor);
  }

  /**
   * Register one or several SIZE stats based on the observers found and summarize the outcomes. Stat prefix is taken from the discriminator if any.
   */
  public <T extends Enum<T>> void registerSize(String statNameSuffix, OperationStatisticDescriptor<T> descriptor, EnumSet<T> outcomes) {
    registerStatistic(statNameSuffix, StatisticType.SIZE, descriptor, outcomes);
  }

  /**
   * Directly register a COUNTER stat with its accessor
   */
  public <N extends Number> void registerCounter(String fullStatName, ValueStatistic<N> accessor) {
    registerStatistic(fullStatName, StatisticType.COUNTER, accessor);
  }

  /**
   * Register one or several COUNTER passthrough stats. Stat prefix is taken from the discriminator.
   */
  public void registerCounter(String statNameSuffix, ValueStatisticDescriptor descriptor) {
    registerStatistic(statNameSuffix, StatisticType.COUNTER, descriptor);
  }

  /**
   * Register one or several COUNTER stats based on the observers found and summarize the outcomes. Stat prefix is taken from the discriminator if any.
   */
  public <T extends Enum<T>> void registerCounter(String statNameSuffix, OperationStatisticDescriptor<T> descriptor, EnumSet<T> outcomes) {
    registerStatistic(statNameSuffix, StatisticType.COUNTER, descriptor, outcomes);
  }

  private <N extends Number> void registerStatistic(String statNameSuffix, StatisticType type, final ValueStatisticDescriptor descriptor) {
    if (contextObject == null) {
      return;
    }

    Set<TreeNode> result = queryBuilder()
        .descendants()
        .filter(context(attributes(Matchers.<Map<String, Object>>allOf(
            hasAttribute("name", descriptor.getObserverName()),
            hasAttribute("tags", new Matcher<Set<String>>() {
              @Override
              protected boolean matchesSafely(Set<String> object) {
                return object.containsAll(descriptor.getTags());
              }
            })))))
        .filter(context(identifier(subclassOf(ValueStatistic.class))))
        .build().execute(Collections.singleton(ContextManager.nodeFor(contextObject)));

    if (!result.isEmpty()) {
      for (TreeNode node : result) {
        String discriminator = null;

        Map<String, Object> properties = (Map<String, Object>) node.getContext().attributes().get("properties");
        if (properties != null && properties.containsKey("discriminator")) {
          discriminator = properties.get("discriminator").toString();
        }

        String fullStatName = (discriminator == null ? "" : (discriminator + ":")) + statNameSuffix;
        ValueStatistic<N> statistic = (ValueStatistic<N>) node.getContext().attributes().get("this");

        registerStatistic(fullStatName, type, statistic);
      }
    }
  }

  private <T extends Enum<T>> void registerStatistic(String statNameSuffix, StatisticType type, final OperationStatisticDescriptor<T> descriptor, final EnumSet<T> outcomes) {
    if (contextObject == null) {
      return;
    }

    Set<TreeNode> result = queryBuilder()
        .descendants()
        .filter(context(attributes(Matchers.<Map<String, Object>>allOf(
            hasAttribute("type", descriptor.getType()),
            hasAttribute("name", descriptor.getObserverName()),
            hasAttribute("tags", new Matcher<Set<String>>() {
              @Override
              protected boolean matchesSafely(Set<String> object) {
                return object.containsAll(descriptor.getTags());
              }
            })))))
        .filter(context(identifier(subclassOf(OperationStatistic.class))))
        .build().execute(Collections.singleton(ContextManager.nodeFor(contextObject)));

    if (!result.isEmpty()) {
      for (TreeNode node : result) {
        String discriminator = null;

        Map<String, Object> properties = (Map<String, Object>) node.getContext().attributes().get("properties");
        if (properties != null && properties.containsKey("discriminator")) {
          discriminator = properties.get("discriminator").toString();
        }

        String fullStatName = (discriminator == null ? "" : (discriminator + ":")) + statNameSuffix;
        final OperationStatistic<T> statistic = (OperationStatistic<T>) node.getContext().attributes().get("this");

        registerStatistic(fullStatName, type, new ValueStatistic<Number>() {
          @Override
          public Number value() {
            return statistic.sum(outcomes);
          }
        });
      }
    }
  }

  private <N extends Number> void registerStatistic(String fullStatName, StatisticType type, ValueStatistic<N> accessor) {
    if (statistics.put(fullStatName, accessor) != null) {
      throw new IllegalArgumentException("Found duplicate statistic " + fullStatName);
    }
    statisticTypes.put(fullStatName, type);
  }

}
