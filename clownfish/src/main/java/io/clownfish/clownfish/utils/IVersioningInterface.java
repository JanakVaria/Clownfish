/*
 * Copyright 2021 raine.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.clownfish.clownfish.utils;

/**
 *
 * @author raine
 */
public interface IVersioningInterface<T> {
    public String getVersion(long ref, long version);
    public void writeVersion(long ref, long version, byte[] content, long userid);
    public boolean hasDifference(T object);
    public long getCurrentVersionNumber(String name);
}
