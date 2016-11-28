/*
 * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.hal.ballroom.form;

/**
 * Helper class to pass data to methods called by the AbstractFormItem constructor.
 */
public class CreationContext<C> {

    public static final CreationContext<Void> EMPTY_CONTEXT = new CreationContext<>(null);

    private final C data;

    public CreationContext(final C data) {this.data = data;}

    public C data() {
        return data;
    }
}
