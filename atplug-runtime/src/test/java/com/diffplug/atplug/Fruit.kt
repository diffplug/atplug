package com.diffplug.atplug

interface Fruit {
	@Metadata fun name(): String

	object Socket : SocketOwner.SingletonById<Fruit>(Fruit::class.java) {
		override fun metadata(plug: Fruit) = mapOf(Pair(KEY_ID, plug.name()))
	}
}

@Plug(Fruit::class)
class Apple : Fruit {
	override fun name() = "Apple"
}

@Plug(Fruit::class)
class Orange : Fruit {
	override fun name() = "Orange"
}
