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

import static org.junit.jupiter.api.Assertions.fail;

import com.diffplug.common.base.Errors;
import com.diffplug.common.base.StandardSystemProperty;
import com.diffplug.common.base.StringPrinter;
import com.diffplug.common.tree.TreeDef;
import com.diffplug.common.tree.TreeStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;

public class GradleIntegrationHarness extends ResourceHarness {
	/**
	 * Each test gets its own temp folder, and we create a gradle
	 * build there and run it.
	 *
	 * Because those test folders don't have a .gitattributes file,
	 * git (on windows) will default to \r\n. So now if you read a
	 * test file from the spotless test resources, and compare it
	 * to a build result, the line endings won't match.
	 *
	 * By sticking this .gitattributes file into the test directory,
	 * we ensure that the default Spotless line endings policy of
	 * GIT_ATTRIBUTES will use \n, so that tests match the test
	 * resources on win and linux.
	 */
	@BeforeEach
	void gitAttributes() throws IOException {
		setFile(".gitattributes").toContent("* text eol=lf");
	}

	protected GradleRunner gradleRunner() throws IOException {
		return GradleRunner.create()
				.withProjectDir(rootFolder())
				.withPluginClasspath();
	}

	/** Dumps the complete file contents of the folder to the console. */
	protected String getContents() throws IOException {
		return getContents(subPath -> !subPath.startsWith(".gradle"));
	}

	protected String getContents(Predicate<String> subpathsToInclude) throws IOException {
		TreeDef<File> treeDef = TreeDef.forFile(Errors.rethrow());
		List<File> files = TreeStream.depthFirst(treeDef, rootFolder())
				.filter(File::isFile)
				.collect(Collectors.toList());

		ListIterator<File> iterator = files.listIterator(files.size());
		int rootLength = rootFolder().getAbsolutePath().length() + 1;
		return StringPrinter.buildString(printer -> Errors.rethrow().run(() -> {
			while (iterator.hasPrevious()) {
				File file = iterator.previous();
				String subPath = file.getAbsolutePath().substring(rootLength);
				if (subpathsToInclude.test(subPath)) {
					printer.println("### " + subPath + " ###");
					printer.println(read(subPath));
				}
			}
		}));
	}

	protected void checkRunsThenUpToDate() throws IOException {
		checkIsUpToDate(false);
		checkIsUpToDate(true);
	}

	protected void applyIsUpToDate(boolean upToDate) throws IOException {
		taskIsUpToDate("spotlessApply", upToDate);
	}

	protected void checkIsUpToDate(boolean upToDate) throws IOException {
		taskIsUpToDate("spotlessCheck", upToDate);
	}

	private static final int FILESYSTEM_RESOLUTION_MS = StandardSystemProperty.LINE_SEPARATOR.value().equals("\r\n") ? 150 : 2000;

	void pauseForFilesystem() {
		Errors.rethrow().run(() -> Thread.sleep(FILESYSTEM_RESOLUTION_MS));
	}

	private void taskIsUpToDate(String task, boolean upToDate) throws IOException {
		pauseForFilesystem();
		BuildResult buildResult = gradleRunner().withArguments(task).build();

		List<String> expected = outcomes(buildResult, upToDate ? TaskOutcome.UP_TO_DATE : TaskOutcome.SUCCESS);
		List<String> notExpected = outcomes(buildResult, upToDate ? TaskOutcome.SUCCESS : TaskOutcome.UP_TO_DATE);
		boolean everythingAsExpected = !expected.isEmpty() && notExpected.isEmpty() && buildResult.getTasks().size() - 1 == expected.size();
		if (!everythingAsExpected) {
			fail("Expected all tasks to be " + (upToDate ? TaskOutcome.UP_TO_DATE : TaskOutcome.SUCCESS) + ", but instead was\n" + buildResultToString(buildResult));
		}
	}

	protected static List<String> outcomes(BuildResult build, TaskOutcome outcome) {
		return build.taskPaths(outcome).stream()
				.filter(s -> !s.equals(":spotlessInternalRegisterDependencies"))
				.collect(Collectors.toList());
	}

	protected static List<BuildTask> outcomes(BuildResult build) {
		return build.getTasks().stream()
				.filter(t -> !t.getPath().equals(":spotlessInternalRegisterDependencies"))
				.collect(Collectors.toList());
	}

	static String buildResultToString(BuildResult result) {
		return StringPrinter.buildString(printer -> {
			for (BuildTask task : result.getTasks()) {
				printer.println(task.getPath() + " " + task.getOutcome());
			}
		});
	}
}