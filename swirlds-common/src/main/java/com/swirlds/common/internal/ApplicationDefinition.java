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

package com.swirlds.common.internal;

import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;

import java.io.File;
import java.util.List;

/**
 * Temporary internal only class to facilitate an incremental refactor of the {@code com.swirlds.platform.Browser} class.
 * Will not be providing javadoc on class members due to ephemeral nature of this temporary class.
 */
public class ApplicationDefinition {

	private String swirldName;
	private String[] appParameters;
	private String appJarFileName;
	private String mainClassName;
	private File appJarPath;
	private AddressBook addressBook;

	private byte[] masterKey;
	private byte[] swirldId;

	public ApplicationDefinition(final String swirldName, final String[] appParameters, final String appJarFileName,
			final String mainClassName, final File appJarPath, final List<Address> bookData) {
		this.swirldName = swirldName;
		this.appParameters = appParameters;
		this.appJarFileName = appJarFileName;
		this.mainClassName = mainClassName;
		this.appJarPath = appJarPath;
		this.addressBook = new AddressBook(bookData);
	}

	public String getSwirldName() {
		return swirldName;
	}

	public String[] getAppParameters() {
		return appParameters;
	}

	public String getAppJarFileName() {
		return appJarFileName;
	}

	public String getMainClassName() {
		return mainClassName;
	}

	public String getApplicationName() {
		return mainClassName.substring(0, mainClassName.length() - 4);
	}

	public File getAppJarPath() {
		return appJarPath;
	}

	public AddressBook getAddressBook() {
		return addressBook;
	}

	public byte[] getMasterKey() {
		return masterKey;
	}

	public byte[] getSwirldId() {
		return swirldId;
	}

	public void setMasterKey(final byte[] masterKey) {
		this.masterKey = masterKey;
	}

	public void setSwirldId(final byte[] swirldId) {
		this.swirldId = swirldId;
	}
}
