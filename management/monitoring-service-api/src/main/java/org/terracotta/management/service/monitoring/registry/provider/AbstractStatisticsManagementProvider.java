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
package org.terracotta.management.service.monitoring.registry.provider;

import com.tc.classloader.CommonComponent;
import org.terracotta.management.model.capabilities.descriptors.Descriptor;
import org.terracotta.management.model.capabilities.descriptors.StatisticDescriptor;
import org.terracotta.management.model.context.Context;
import org.terracotta.management.model.stats.Statistic;
import org.terracotta.management.registry.Named;
import org.terracotta.management.registry.RequiredContext;
import org.terracotta.management.registry.action.ExposedObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RequiredContext({@Named("consumerId")})
@CommonComponent
public abstract class AbstractStatisticsManagementProvider<T extends AliasBinding> extends AliasBindingManagementProvider<T> {

  public AbstractStatisticsManagementProvider(Class<? extends T> type) {
    super(type);
  }

  @Override
  protected void dispose(ExposedObject<T> exposedObject) {
    ((AbstractExposedStatistics<T>) exposedObject).close();
  }

  @Override
  public final Collection<? extends Descriptor> getDescriptors() {
    List<StatisticDescriptor> list = new ArrayList<>((Collection<? extends StatisticDescriptor>) super.getDescriptors());
    Collections.sort(list, STATISTIC_DESCRIPTOR_COMPARATOR);
    return list;
  }

  @Override
  public Map<String, Statistic<?, ?>> collectStatistics(Context context, Collection<String> statisticNames) {
    Map<String, Statistic<?, ?>> statistics = new TreeMap<>();
    AbstractExposedStatistics<T> exposedObject = (AbstractExposedStatistics<T>) findExposedObject(context);
    if (exposedObject != null) {
      if (statisticNames == null || statisticNames.isEmpty()) {
        statistics.putAll(exposedObject.queryStatistics());
      } else {
        for (String statisticName : statisticNames) {
          try {
            statistics.put(statisticName, exposedObject.queryStatistic(statisticName));
          } catch (IllegalArgumentException ignored) {
            // ignore when statisticName does not exist and throws an exception
          }
        }
      }
    }
    return statistics;
  }

  @Override
  protected AbstractExposedStatistics<T> wrap(T managedObject) {
    Context context = Context.empty()
        .with("consumerId", String.valueOf(getMonitoringService().getConsumerId()))
        .with("alias", managedObject.getAlias());
    Object contextObject = getContextObject(managedObject);
    return internalWrap(context, managedObject, contextObject);
  }

  protected Object getContextObject(T managedObject) {
    return managedObject.getValue();
  }

  @Override
  protected final AbstractExposedStatistics<T> internalWrap(Context context, T managedObject) {
    throw new UnsupportedOperationException();
  }

  protected abstract AbstractExposedStatistics<T> internalWrap(Context context, T managedObject, Object contextObject);

}
