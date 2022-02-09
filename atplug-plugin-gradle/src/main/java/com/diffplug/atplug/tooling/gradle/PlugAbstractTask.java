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
package com.diffplug.atplug.tooling.gradle;


import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;

public abstract class PlugAbstractTask extends DefaultTask {
	@InputFiles
	public abstract DirectoryProperty getClassesFolderA();

	@InputFiles
	public abstract DirectoryProperty getClassesFolderB();

	@InputFiles
	public abstract DirectoryProperty getClassesFolderC();

	void setClassesFolders(Iterable<File> files) {
		int size = 0;
		for (File file : files) {
			file.mkdirs();
			switch (++size) {
			case 1:
				// set all fields
				getClassesFolderA().set(file);
				getClassesFolderB().set(file);
				getClassesFolderC().set(file);
				break;
			case 2:
				// set the second field
				getClassesFolderB().set(file);
				break;
			case 3:
				// set the last field
				getClassesFolderC().set(file);
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
		files.add(getClassesFolderA().get().getAsFile());
		if (!getClassesFolderA().equals(getClassesFolderB())) {
			files.add(getClassesFolderB().get().getAsFile());
		}
		if (!getClassesFolderA().equals(getClassesFolderC())) {
			files.add(getClassesFolderC().get().getAsFile());
		}
		return files;
	}
}
