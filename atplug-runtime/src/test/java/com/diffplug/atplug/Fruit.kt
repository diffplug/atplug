package com.diffplug.atplug

interface Fruit {
	@Metadata fun name(): String

	class MetadataCreator :
			DeclarativeMetadataCreator<Fruit>(
					Fruit::class.java, { mapOf(Pair(SocketOwner.Id.KEY_ID, it.name())) })

	object Socket : SocketOwner.Id<Fruit>(Fruit::class.java)
}

@Plug(Fruit::class)
class Apple : Fruit {
	override fun name() = "Apple"
}

@Plug(Fruit::class)
class Orange : Fruit {
	override fun name() = "Orange"
}
