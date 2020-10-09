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

package com.swirlds.blob.internal.db.migration;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import javax.sql.DataSource;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class Migration implements AutoCloseable {
	private static final String DEFAULT_INSTALLED_BY = "system";

	private static final File[] SEARCH_PATHS = { new File("data/lib"), new File("sdk/data/lib") };
	private static final String JAR_FILE_SUFFIX = ".jar";
	private static final String JAR_FILE_PREFIX = "swirlds";


	private DataSource dataSource;
	private String installedBy;

	public Migration(final DataSource dataSource) {
		if (dataSource == null) {
			throw new IllegalArgumentException("dataSource");
		}

		this.dataSource = dataSource;
		this.installedBy = DEFAULT_INSTALLED_BY;
	}

	public Migration(final DataSource dataSource, final String installedBy) {
		this(dataSource);

		if (installedBy != null && !installedBy.isEmpty()) {
			this.installedBy = installedBy;
		}
	}

	protected abstract File[] getAdditionalJars();

	protected abstract String[] getLocations();

	protected abstract String[] getSchemas();

	protected abstract Map<String, String> getPlaceholders();

	protected Flyway configure() {
		FluentConfiguration config = Flyway.configure(getClassLoader())
				.locations(getLocations())
				.schemas(getSchemas())
				.dataSource(dataSource)
				.installedBy(installedBy);

		final Map<String, String> placeholders = getPlaceholders();

		if (placeholders != null && !placeholders.isEmpty()) {
			config.placeholders(placeholders);
		}

		return config.load();
	}

	protected ClassLoader getClassLoader() {
		final List<URL> urls = new ArrayList<>();
		final File[] additionalJars = getAdditionalJars();

		if (additionalJars != null) {
			urls.addAll(Arrays.stream(additionalJars).map(Migration::fileToUrl).collect(Collectors.toList()));
			urls.removeIf(Objects::isNull);
		}

		for (File searchPath : SEARCH_PATHS) {
			if (searchPath.exists() && searchPath.isDirectory()) {
				final File[] jarFiles = searchPath.listFiles(
						f -> f.isFile() &&
								f.getName().startsWith(JAR_FILE_PREFIX) &&
								f.getName().endsWith(JAR_FILE_SUFFIX));

				if (jarFiles != null) {
					urls.addAll(
							Arrays.stream(jarFiles).map(Migration::fileToUrl).collect(Collectors.toList()));
					urls.removeIf(Objects::isNull);
				}
			}
		}

		return new URLClassLoader(getClass().getSimpleName() + " ClassLoader", urls.toArray(new URL[0]),
				this.getClass().getClassLoader());
	}

	public void migrate() {
		configure().migrate();
	}

	public void clean() {
		configure().clean();
	}

	public void repair() {
		configure().repair();
	}

	public void baseline() {
		configure().baseline();
	}

	public boolean isH2Database() {
		if (dataSource instanceof HikariDataSource) {
			return ((HikariDataSource) dataSource).getJdbcUrl().contains("h2");
		}

		return false;
	}

	public boolean isPostgreSql() {
		if (dataSource instanceof HikariDataSource) {
			return ((HikariDataSource) dataSource).getJdbcUrl().contains("postgresql");
		}

		return false;
	}

	@Override
	public void close() {

	}

	private static URL fileToUrl(final File file) {
		try {
			return (file != null && file.exists()) ? file.toURI().toURL() : null;
		} catch (MalformedURLException ex) {
			return null;
		}
	}
}
