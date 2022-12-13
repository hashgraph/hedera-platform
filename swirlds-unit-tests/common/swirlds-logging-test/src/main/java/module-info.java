open module com.swirlds.logging.test {
    exports com.swirlds.logging.test;

    requires com.swirlds.logging;

    /* Logging Libraries */
    requires org.apache.logging.log4j;

    /* Utilities */
    requires org.apache.commons.lang3;

    /* Jackson JSON */
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
}
