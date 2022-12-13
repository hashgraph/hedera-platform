import com.swirlds.config.api.spi.AbstractConfigurationResolver;

module com.swirlds.config {
    exports com.swirlds.config.api;
    exports com.swirlds.config.api.spi;
    exports com.swirlds.config.api.converter;
    exports com.swirlds.config.api.source;
    exports com.swirlds.config.api.validation;

    uses AbstractConfigurationResolver;
}
