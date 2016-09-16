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
package org.terracotta.management.service.monitoring;

import org.terracotta.management.sequence.SequenceGenerator;
import org.terracotta.monitoring.IStripeMonitoring;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Mathieu Carbou
 */
class VoltronMonitoringService {

  static final long PLATFORM_CONSUMERID = 0;
  static final String PLATFORM_CATEGORY = "platform-notifications";

  private final Map<Long, DefaultMonitoringProducer> producers = new ConcurrentHashMap<>();
  private final Map<Long, DefaultMonitoringConsumer> consumers = new ConcurrentHashMap<>();
  private final SequenceGenerator sequenceGenerator;

  VoltronMonitoringService(SequenceGenerator sequenceGenerator) {
    this.sequenceGenerator = sequenceGenerator;
  }

  IStripeMonitoring getProducer(long callerConsumerID) {
    return producers.computeIfAbsent(callerConsumerID, id -> id == PLATFORM_CONSUMERID ?
        new PlatformMonitoringProducer(id, consumers, producers, sequenceGenerator) :
        new DefaultMonitoringProducer(id, consumers));
  }

  IMonitoringConsumer getConsumer(long callerConsumerID) {
    return consumers.computeIfAbsent(callerConsumerID, id -> new DefaultMonitoringConsumer(id, consumers, consumerId -> Optional.ofNullable(producers.get(consumerId))));
  }

  void clear() {
    consumers.clear();
    producers.clear();
  }

}
