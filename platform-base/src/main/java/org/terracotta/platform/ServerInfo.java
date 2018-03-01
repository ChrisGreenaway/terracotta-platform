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
package org.terracotta.platform;

import com.tc.classloader.CommonComponent;

import static java.util.Objects.requireNonNull;

@CommonComponent
public class ServerInfo {
  private final String name;

  ServerInfo(String name) {
    this.name = requireNonNull(name, "name");
  }

  public String getName() {
    return name;
  }
}
