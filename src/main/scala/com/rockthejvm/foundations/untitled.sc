def printAll[A](value: A): Unit =
  println(s"$value: this is the value")
  

printAll("ben")
printAll(123)
trait Animal {
  def voice: Unit
}

case class Cat(name: String, sound: String) extends Animal {

  override def voice: Unit = println(sound)
}


case class Dog(name: String, sound: String) extends Animal {
  override def voice: Unit = println(sound)
}

val viv = Cat("Viv", "meow")
val penny = Dog("penny", "woof")

viv.voice

penny.voice


List(List("ben", "ben"), List("Ben", "Ben")).flatten