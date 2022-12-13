module com.swirlds.fcqueue {
    exports com.swirlds.fcqueue;
    exports com.swirlds.fcqueue.internal to
            com.swirlds.fcqueue.test;

    requires com.swirlds.common;
    requires com.swirlds.logging;
    requires org.apache.logging.log4j;
    requires org.apache.commons.lang3;
}
