/*
 * Copyright 2000-2022 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.flow.templatemodel;

import com.vaadin.flow.internal.StateNode;

/**
 * A complex model type (represents either a list or a bean).
 * <p>
 * For internal use only. May be renamed or removed in a future release.
 *
 * @author Vaadin Ltd
 * @since 1.0
 *
 * @param <T>
 *            the proxy type used by this type
 * @deprecated Template model and model types are not supported for lit
 *             template, but you can use {@code @Id} mapping and the component
 *             API or the element API with property synchronization instead.
 *             Polymer template support is deprecated - we recommend you to use
 *             {@code LitTemplate} instead. Read more details from <a href=
 *             "https://vaadin.com/blog/future-of-html-templates-in-vaadin">the
 *             Vaadin blog.</a>
 */
@Deprecated
public interface ComplexModelType<T> extends ModelType {

    @Override
    StateNode applicationToModel(Object applicationValue,
            PropertyFilter filter);

    /**
     * Checks that this type uses the provided proxy type and returns this type
     * as a model type with that proxy type.
     *
     * @param <C>
     *            the proxy type
     * @param proxyType
     *            the proxy type to cast to
     * @return this model type
     */
    <C> ComplexModelType<C> cast(Class<C> proxyType);
}
