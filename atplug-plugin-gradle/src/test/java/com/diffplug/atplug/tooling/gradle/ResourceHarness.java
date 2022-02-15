/*
 * Copyright (C) 2016-2022 DiffPlug
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
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.io.TempDir;

public class ResourceHarness {
	/**
	 * On OS X, the temp folder is a symlink,
	 * and some of gradle's stuff breaks symlinks.
	 * By only accessing it through the {@link #rootFolder()}
	 * and {@link #newFile(String)} apis, we can guarantee there
	 * will be no symlink problems.
	 */
	@TempDir
	File folderDontUseDirectly;

	/** Returns the root folder (canonicalized to fix OS X issue) */
	protected File rootFolder() throws IOException {
		return folderDontUseDirectly.getCanonicalFile();
	}

	/** Returns a new child of the root folder. */
	protected File newFile(String subpath) throws IOException {
		return new File(rootFolder(), subpath);
	}

	/** Creates and returns a new child-folder of the root folder. */
	protected File newFolder(String subpath) throws IOException {
		File targetDir = newFile(subpath);
		if (!targetDir.mkdir()) {
			throw new IOException("Failed to create " + targetDir);
		}
		return targetDir;
	}

	protected String read(String path) throws IOException {
		return read(newFile(path).toPath(), StandardCharsets.UTF_8);
	}

	protected String read(Path path, Charset encoding) throws IOException {
		return new String(Files.readAllBytes(path), encoding);
	}

	protected void replace(String path, String toReplace, String replaceWith) throws IOException {
		String before = read(path);
		String after = before.replace(toReplace, replaceWith);
		if (before.equals(after)) {
			throw new IllegalArgumentException("Replace was ineffective! '" + toReplace + "' was not found in " + path);
		}
		setFile(path).toContent(after);
	}

	protected ReadAsserter assertFile(String path) throws IOException {
		return new ReadAsserter(newFile(path));
	}

	protected ReadAsserter assertFile(File file) throws IOException {
		return new ReadAsserter(file);
	}

	public static class ReadAsserter {
		private final String content;

		private ReadAsserter(File file) throws IOException {
			this.content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim().replace("\r\n", "\n");
		}

		public void hasContent(String expected) {
			Assertions.assertEquals(expected, content);
		}

		public void hasContentIgnoreWhitespace(String expected) {
			String actualClean = content.replace("\n", "").replace(" ", "");
			String expectedClean = expected.replace("\n", "").replace(" ", "");
			Assertions.assertEquals(expectedClean, actualClean);
		}
	}

	protected WriteAsserter setFile(String path) throws IOException {
		return new WriteAsserter(newFile(path));
	}

	public static class WriteAsserter {
		private File file;

		private WriteAsserter(File file) {
			file.getParentFile().mkdirs();
			this.file = file;
		}

		public File toLines(String... lines) throws IOException {
			return toContent(String.join("\n", Arrays.asList(lines)));
		}

		public File toContent(String content) throws IOException {
			return toContent(content, StandardCharsets.UTF_8);
		}

		public File toContent(String content, Charset charset) throws IOException {
			Files.write(file.toPath(), content.getBytes(charset));
			return file;
		}

		public File deleted() throws IOException {
			Files.delete(file.toPath());
			return file;
		}
	}
}
