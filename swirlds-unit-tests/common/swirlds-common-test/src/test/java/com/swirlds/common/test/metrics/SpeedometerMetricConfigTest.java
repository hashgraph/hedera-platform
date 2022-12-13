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
package com.swirlds.common.test.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.metrics.FloatFormats;
import com.swirlds.common.metrics.SpeedometerMetric;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpeedometerMetricConfigTest {

    private static final String DEFAULT_FORMAT = FloatFormats.FORMAT_11_3;
    private static final double DEFAULT_HALFLIFE = SettingsCommon.halfLife;

    private static final String CATEGORY = "CaTeGoRy";
    private static final String NAME = "NaMe";
    private static final String DESCRIPTION = "DeScRiPtIoN";
    private static final String FORMAT = "FoRmAt";
    private static final String UNIT = "UnIt";

    private static final double EPSILON = 1e-6;

    @Test
    void testConstructor() {
        // when
        final SpeedometerMetric.Config config = new SpeedometerMetric.Config(CATEGORY, NAME);

        // then
        assertThat(config.getCategory()).isEqualTo(CATEGORY);
        assertThat(config.getName()).isEqualTo(NAME);
        assertThat(config.getDescription()).isEqualTo(NAME);
        assertThat(config.getUnit()).isEmpty();
        assertThat(config.getFormat()).isEqualTo(DEFAULT_FORMAT);
        assertThat(config.getHalfLife()).isEqualTo(DEFAULT_HALFLIFE, within(EPSILON));
    }

    @Test
    @DisplayName("Constructor should throw IAE when passing illegal parameters")
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new SpeedometerMetric.Config(null, NAME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SpeedometerMetric.Config("", NAME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SpeedometerMetric.Config(" \t\n", NAME))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new SpeedometerMetric.Config(CATEGORY, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SpeedometerMetric.Config(CATEGORY, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SpeedometerMetric.Config(CATEGORY, " \t\n"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSetters() {
        // given
        final SpeedometerMetric.Config config = new SpeedometerMetric.Config(CATEGORY, NAME);

        // when
        final SpeedometerMetric.Config result =
                config.withDescription(DESCRIPTION)
                        .withUnit(UNIT)
                        .withFormat(FORMAT)
                        .withHalfLife(Math.PI);

        // then
        assertThat(config.getCategory()).isEqualTo(CATEGORY);
        assertThat(config.getName()).isEqualTo(NAME);
        assertThat(config.getDescription()).isEqualTo(NAME);
        assertThat(config.getUnit()).isEmpty();
        assertThat(config.getFormat()).isEqualTo(DEFAULT_FORMAT);
        assertThat(config.getHalfLife()).isEqualTo(DEFAULT_HALFLIFE, within(EPSILON));

        assertThat(result.getCategory()).isEqualTo(CATEGORY);
        assertThat(result.getName()).isEqualTo(NAME);
        assertThat(result.getDescription()).isEqualTo(DESCRIPTION);
        assertThat(result.getUnit()).isEqualTo(UNIT);
        assertThat(result.getFormat()).isEqualTo(FORMAT);
        assertThat(result.getHalfLife()).isEqualTo(Math.PI, within(EPSILON));
    }

    @Test
    void testSettersWithIllegalParameters() {
        // given
        final SpeedometerMetric.Config config = new SpeedometerMetric.Config(CATEGORY, NAME);
        final String longDescription = DESCRIPTION.repeat(50);

        // then
        assertThatThrownBy(() -> config.withDescription(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.withDescription(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.withDescription(" \t\n"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.withDescription(longDescription))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> config.withUnit(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> config.withFormat(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.withFormat(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.withFormat(" \t\n"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testToString() {
        // given
        final SpeedometerMetric.Config config =
                new SpeedometerMetric.Config(CATEGORY, NAME)
                        .withDescription(DESCRIPTION)
                        .withUnit(UNIT)
                        .withFormat(FORMAT)
                        .withHalfLife(Math.PI);

        // then
        assertThat(config.toString()).contains(CATEGORY, NAME, DESCRIPTION, UNIT, FORMAT, "3.1415");
    }
}
