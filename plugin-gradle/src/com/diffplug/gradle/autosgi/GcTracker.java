/*
 * Copyright 2016 DiffPlug
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
package com.diffplug.gradle.autosgi;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.diffplug.common.base.Errors;
import com.diffplug.common.collect.Sets;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/** Keeps track of when objects in a given pool are GC'ed. */
@SuppressFBWarnings(value = "DM_GC", justification = "Forcing GC is dubious, but helpful in very-specific situations.")
public class GcTracker {
	private ReferenceQueue<Object> queue = new ReferenceQueue<Object>();
	private Set<Reference<Object>> refs = Sets.newHashSet();

	/** Adds a ref to the queue. */
	public void track(Object referent) {
		refs.add(new WeakReference<Object>(referent, queue));
	}

	/** Returns the number of non-GCed objects which have been added to this pool. */
	public int getRemaining() {
		Reference<? extends Object> ref = queue.poll();
		if (ref != null) {
			refs.remove(ref);
			ref = queue.poll();
		}
		return refs.size();
	}

	/** Runs GC until getRemaining() is empty, or until the time runs out. */
	public void tryGcUntilEmpty(long pauseBetweenGc, long maxElapsed, TimeUnit timeUnit) {
		if (getRemaining() > 0) {
			System.gc();
			Errors.rethrow().run(() -> {
				long end = System.currentTimeMillis() + timeUnit.toMillis(maxElapsed);
				while (getRemaining() > 0 && System.currentTimeMillis() < end) {
					Thread.sleep(timeUnit.toMillis(pauseBetweenGc));
					System.gc();
				}
			});
		}
	}
}
