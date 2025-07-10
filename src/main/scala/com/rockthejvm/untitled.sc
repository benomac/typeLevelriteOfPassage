import scala.collection.immutable.List

val list = "1234"


def slices(n: Int, s: String): List[List[Int]] = {
  def slice(n: Int, s: String, acc: List[List[String]]): List[List[Int]] =
    if (s.isEmpty || s.length < n) acc.map(_.map(_.toInt))
    else {
      val splitList: List[String] = s.split("").toList

      splitList match
        case Nil => acc.map(_.map(_.toInt))
        case ::(head, next) => slice(n, next.mkString, acc :+ splitList.take(n))
    }
  slice(n, s, Nil)
}




println(slices(3, list))