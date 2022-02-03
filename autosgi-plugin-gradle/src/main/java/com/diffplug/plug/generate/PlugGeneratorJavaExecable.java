/*
 * Copyright (C) 2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.plug.generate;


import com.diffplug.plug.generate.gradle.JavaExecable;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

/**
 * {@link PlugGenerator#PlugGenerator(List, Set)} in a {@link JavaExecable} form.
 */
public class PlugGeneratorJavaExecable implements JavaExecable {
	// inputs
	List<File> toSearch;
	Set<File> toLinkAgainst;

	public PlugGeneratorJavaExecable(List<File> toSearch, Set<File> toLinkAgainst) {
		this.toSearch = new ArrayList<>(toSearch);
		this.toLinkAgainst = new LinkedHashSet<>(toLinkAgainst);
	}

	// outputs
	SortedMap<String, String> osgiInf;

	public SortedMap<String, String> getOsgiInf() {
		return osgiInf;
	}

	@Override
	public void run() throws Throwable {
		PlugGenerator metadataGen = new PlugGenerator(toSearch, toLinkAgainst);
		osgiInf = metadataGen.osgiInf;
	}
}
