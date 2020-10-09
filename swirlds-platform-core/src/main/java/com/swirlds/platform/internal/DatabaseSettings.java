/*
 * (c) 2016-2020 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.platform.internal;

/**
 * A class that holds all settings related to database connectivity
 */
public class DatabaseSettings extends SubSetting {

	public boolean active = true;

	public String host = "localhost";

	public int port = 5432;

	public String schema = "fcfs";

	public String userName = "swirlds";

	public String password = "password";

	public String driverClassName = "org.postgresql.Driver";

	public String jdbcUrlPrefix = "postgresql";

	public DatabaseSettings() {
	}

	public DatabaseSettings(final boolean active, final String host, final int port, final String schema, final String userName,
			final String password, final String driverClassName, final String jdbcUrlPrefix) {
		this.active = active;
		this.host = host;
		this.port = port;
		this.schema = schema;
		this.userName = userName;
		this.password = password;
		this.driverClassName = driverClassName;
		this.jdbcUrlPrefix = jdbcUrlPrefix;
	}

	public boolean isActive() {
		return active;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getSchema() {
		return schema;
	}

	public String getUserName() {
		return userName;
	}

	public String getPassword() {
		return password;
	}

	public String getDriverClassName() {
		return driverClassName;
	}

	public String getJdbcUrlPrefix() {
		return jdbcUrlPrefix;
	}
}
