/*
 * Copyright (C) 2022 DiffPlug
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
package com.diffplug.atplug;


import java.util.HashMap;
import java.util.Map;

public interface Shape {
	@Metadata
	String name();

	class MetadataCreator extends DeclarativeMetadataCreator<Shape> {
		public MetadataCreator() {
			super(Shape.class, instance -> {
				Map<String, String> map = new HashMap<>();
				map.put(SocketOwner.Id.KEY_ID, instance.name());
				return map;
			});
		}
	}

	SocketOwner.Id<Shape> socket = new SocketOwner.Id<Shape>(Shape.class) {
		@Override
		public Map<String, String> metadata(Shape plug) {
			Map<String, String> map = new HashMap<>();
			map.put(KEY_ID, plug.name());
			return map;
		}
	};

	@Plug(Shape.class)
	class Circle implements Shape {
		@Override
		public String name() {
			return "Circle";
		}
	}

	@Plug(Shape.class)
	class Square implements Shape {
		@Override
		public String name() {
			return "Square";
		}
	}
}
