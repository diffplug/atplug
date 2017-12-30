/*
 * Copyright 2018 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.autosgi;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.diffplug.common.collect.ImmutableList;
import com.diffplug.common.collect.ImmutableSet;
import com.diffplug.core.app.MenuForFiles;
import com.diffplug.filesystems.api.Filder;
import com.diffplug.fs.RightClickFile;
import com.diffplug.testcloset.FastTest;

@Category(FastTest.class)
public class NonOsgiDSTest {
	@Test
	public void testClasspathResources() throws IOException {
		Assert.assertTrue(containsResource("com.diffplug.core", "META-INF/MANIFEST.MF"));
		Assert.assertTrue(containsResource("com.diffplug.core", "OSGI-INF/com.diffplug.core.app.MenuForFiles$OnRightClick.xml"));
	}

	private boolean containsResource(String plugin, String resource) throws IOException {
		Enumeration<URL> values = ClassLoader.getSystemClassLoader().getResources(resource);
		while (values.hasMoreElements()) {
			URL element = values.nextElement();
			if (element.toExternalForm().contains("/" + plugin) && element.toExternalForm().endsWith(resource)) {
				return true;
			}
		}
		return false;
	}

	@Test
	public void testItWorks() {
		Set<Class<?>> implementations = RightClickFile.Descriptor.getFor(ImmutableList.of(new Filder("local|something")))
				.map(d -> d.open().get().getClass()).collect(Collectors.toSet());
		Set<Class<?>> expected = ImmutableSet.of(MenuForFiles.OnRightClick.class);
		Assert.assertEquals(expected, implementations);
	}
}
