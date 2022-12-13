import com.swirlds.config.api.spi.AbstractConfigurationResolver;
import com.swirlds.config.impl.internal.ConfigurationProviderResolverImpl;

module com.swirlds.config.impl {
    exports com.swirlds.config.impl.sources;
    exports com.swirlds.config.impl.converters;
    exports com.swirlds.config.impl.validators;
    exports com.swirlds.config.impl.validators.annotation;

    requires com.swirlds.config;
    requires com.swirlds.common;
    requires org.apache.logging.log4j;

    provides AbstractConfigurationResolver with
            ConfigurationProviderResolverImpl;
}
