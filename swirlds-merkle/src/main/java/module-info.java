/** A map that implements the FastCopyable interface. */
open module com.swirlds.merkle {
    exports com.swirlds.merkle.map;
    exports com.swirlds.merkle.tree;
    exports com.swirlds.merkle.tree.internal to
            com.swirlds.merkle.test;

    requires com.swirlds.common;
    requires com.swirlds.logging;
    requires com.swirlds.platform;
    requires com.swirlds.fcqueue;
    requires com.swirlds.fchashmap;
    requires org.apache.logging.log4j;
    requires org.apache.commons.lang3;
    requires java.sql;
}
