/** The Swirlds public API module used by platform applications. */
module com.swirlds.platform {

    /* Public Package Exports. This list should remain alphabetized. */
    exports com.swirlds.platform;
    exports com.swirlds.platform.chatter;
    exports com.swirlds.platform.chatter.communication;
    exports com.swirlds.platform.network.communication.handshake;
    exports com.swirlds.platform.chatter.protocol;
    exports com.swirlds.platform.chatter.protocol.input;
    exports com.swirlds.platform.chatter.protocol.messages;
    exports com.swirlds.platform.chatter.protocol.output;
    exports com.swirlds.platform.chatter.protocol.peer;
    exports com.swirlds.platform.chatter.protocol.heartbeat;
    exports com.swirlds.platform.network.connection;
    exports com.swirlds.platform.network.connectivity;
    exports com.swirlds.platform.event.validation;
    exports com.swirlds.platform.eventhandling;
    exports com.swirlds.platform.intake;
    exports com.swirlds.platform.metrics;
    exports com.swirlds.platform.network;
    exports com.swirlds.platform.network.communication;
    exports com.swirlds.platform.network.protocol;
    exports com.swirlds.platform.network.topology;
    exports com.swirlds.platform.network.unidirectional;
    exports com.swirlds.platform.state;
    exports com.swirlds.platform.stats;
    exports com.swirlds.platform.stats.atomic;
    exports com.swirlds.platform.stats.cycle;
    exports com.swirlds.platform.stats.simple;
    exports com.swirlds.platform.state.signed;
    exports com.swirlds.platform.state.address;
    exports com.swirlds.platform.sync;
    exports com.swirlds.platform.system;
    exports com.swirlds.platform.threading;

    /* Targeted Exports to External Libraries */
    exports com.swirlds.platform.event to
            com.swirlds.platform.test,
            com.swirlds.common,
            com.swirlds.common.test,
            com.fasterxml.jackson.core,
            com.fasterxml.jackson.databind;
    exports com.swirlds.platform.internal to
            com.swirlds.platform.test,
            com.fasterxml.jackson.core,
            com.fasterxml.jackson.databind;
    exports com.swirlds.platform.event.creation to
            com.swirlds.platform.test;
    exports com.swirlds.platform.swirldapp to
            com.swirlds.platform.test;
    exports com.swirlds.platform.components to
            com.swirlds.platform.test;
    exports com.swirlds.platform.observers to
            com.swirlds.platform.test;
    exports com.swirlds.platform.consensus to
            com.swirlds.platform.test;
    exports com.swirlds.platform.crypto to
            com.swirlds.platform.test;
    exports com.swirlds.platform.event.linking to
            com.swirlds.common,
            com.swirlds.platform.test;
    exports com.swirlds.platform.event.intake to
            com.swirlds.platform.test;
    exports com.swirlds.platform.reconnect to
            com.swirlds.platform.test;
    exports com.swirlds.platform.state.notifications to
            com.swirlds.platform.test;
    exports com.swirlds.platform.state.iss to
            com.swirlds.platform.test;
    exports com.swirlds.platform.state.iss.internal to
            com.swirlds.platform.test;
    exports com.swirlds.platform.chatter.protocol.processing;
    exports com.swirlds.platform.recovery to
            com.swirlds.platform.test;
    exports com.swirlds.platform.util.router to
            com.swirlds.platform.test;
    exports com.swirlds.platform.dispatch to
            com.swirlds.platform.test;
    exports com.swirlds.platform.dispatch.types to
            com.swirlds.platform.test;
    exports com.swirlds.platform.dispatch.triggers.control to
            com.swirlds.platform.test;
    exports com.swirlds.platform.dispatch.triggers.error to
            com.swirlds.platform.test;
    exports com.swirlds.platform.dispatch.triggers.flow to
            com.swirlds.platform.test;
    exports com.swirlds.platform.dispatch.triggers.transaction to
            com.swirlds.platform.test;
    exports com.swirlds.platform.reconnect.emergency to
            com.swirlds.platform.test;

    /* Swirlds Libraries */
    requires transitive com.swirlds.common;
    requires com.swirlds.common.test;
    requires com.swirlds.test.framework;
    requires com.swirlds.logging;

    /* JDK Libraries */
    requires java.desktop;
    requires java.management;
    requires java.scripting;
    requires java.sql;
    requires jdk.management;
    requires jdk.net;

    /* JavaFX Libraries */
    requires javafx.base;

    /* Apache Commons */
    requires org.apache.commons.lang3;

    /* Networking Libraries */
    requires portmapper;

    /* Logging Libraries */
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;

    /* Cryptographic Libraries */
    requires org.bouncycastle.pkix;
    requires org.bouncycastle.provider;

    /* Database Libraries */
    requires com.swirlds.fchashmap;
    requires com.swirlds.jasperdb;
    requires com.swirlds.virtualmap;
    requires com.swirlds.fcqueue;
}
