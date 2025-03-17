trait House {
  def newVal: String // adding this doesn't affect the other trait, shows usefulness of the builder pattern
  def getRepresentation: String
}

class BrickHouse extends House {

  override def getRepresentation: String = "Building a BRICK house"

  override def newVal: String = "???"
}

class WoodHouse extends House {

  override def getRepresentation: String = "Building a WOOD house"

  override def newVal: String = ???
}

trait HouseBuilder {

  def createHouse(): House
}

class BrickBuilder extends HouseBuilder {

  override def createHouse(): House = new BrickHouse
}

class WoodBuilder extends HouseBuilder {

  override def createHouse(): House = new WoodHouse
}

class HouseDirector {
  def constructHouse(builder: HouseBuilder) =
    val house: House = builder.createHouse()
    println(house.getRepresentation)
    house
}

val director: HouseDirector = new HouseDirector

val woodBuilder: HouseBuilder = new WoodBuilder()
val woodHouse: House = director.constructHouse(woodBuilder)

val brickBuilder: HouseBuilder = new BrickBuilder()
val brickHouse: House = director.constructHouse(brickBuilder)