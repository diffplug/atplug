/*
 * Copyright (C) 2021-2022 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
