/*
 * Copyright (C) 2021 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.plug.generate.gradle;


import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;

public abstract class PlugAbstractTask extends DefaultTask {
	@InputDirectory
	public File classesFolderA;

	public File getClassesFolderA() {
		return classesFolderA;
	}

	@InputDirectory
	public File classesFolderB;

	public File getClassesFolderB() {
		return classesFolderB;
	}

	@InputDirectory
	public File classesFolderC;

	public File getClassesFolderC() {
		return classesFolderC;
	}

	void setClassesFolders(Iterable<File> files) {
		int size = 0;
		for (File file : files) {
			switch (++size) {
			case 1:
				// set all fields
				classesFolderA = file;
				classesFolderB = file;
				classesFolderC = file;
				break;
			case 2:
				// set the second field
				classesFolderB = file;
				break;
			case 3:
				// set the last field
				classesFolderC = file;
				break;
			default:
				// nothing
			}
		}
		if (size > 3) {
			throw new IllegalArgumentException("Max class folders is three, this was " + size);
		}
	}

	@Internal
	public List<File> getClassesFolders() {
		List<File> files = new ArrayList<>();
		files.add(classesFolderA);
		if (!classesFolderA.equals(classesFolderB)) {
			files.add(classesFolderB);
		}
		if (!classesFolderA.equals(classesFolderC)) {
			files.add(classesFolderC);
		}
		return files;
	}
}
