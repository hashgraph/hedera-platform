/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
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
package com.swirlds.config.api.validation;

import java.io.Serializable;

/**
 * A violation that is based on a validation (see {@link ConfigValidator}) or a constraint (see
 * {@link ConfigPropertyConstraint}).
 */
public interface ConfigViolation extends Serializable {

    /**
     * Returns the name of the property that caused the violation
     *
     * @return name of the property
     */
    String getPropertyName();

    /**
     * Returns the message of the violation
     *
     * @return the message of the violation
     */
    String getMessage();

    /**
     * Returns the value of the property that caused the violation
     *
     * @return the value of the property
     */
    String getPropertyValue();

    /**
     * Returns true if the property exists (is defined by a {@link
     * com.swirlds.config.api.source.ConfigSource}), false otherwise
     *
     * @return true if the property exists
     */
    boolean propertyExists();
}
