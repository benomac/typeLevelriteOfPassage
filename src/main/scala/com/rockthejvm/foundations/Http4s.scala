package com.rockthejvm.foundations

import cats.*
import cats.implicits.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.circe.*
import cats.effect.{IO, IOApp, Resource}
import org.http4s.{Header, HttpRoutes}
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.{OptionalValidatingQueryParamDecoderMatcher, QueryParamDecoderMatcher}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.typelevel.ci.CIString

import java.util.UUID

object Http4s extends IOApp.Simple {

  // simulate an http server with students and courses

  type Student = String
  case class Instructor(firstName: String, lastName: String)
  case class Course(
      id: String,
      name: String,
      year: Int,
      students: List[Student],
      instructorName: String
  )

  object CourseRepository {
    // a "database"
    // defcd94f-6219-41a3-b94e-5b90d2ece627
    val catsEffectCourse = Course(
      id = "defcd94f-6219-41a3-b94e-5b90d2ece627",
      name = "Rock the JVM Ultimate Scala course",
      year = 2022,
      students = List("Ben", "Dawn"),
      instructorName = "Martin Odersky"
    )

    private val courses: Map[String, Course] = Map(catsEffectCourse.id -> catsEffectCourse)

    // API
    def findCoursesById(courseId: UUID): Option[Course] =
      courses.get(courseId.toString)

    def findCoursesByInstructor(name: String): List[Course] =
      courses.values.filter(_.instructorName == name).toList
  }

  // essential rest endpoints
  // GET localhost:8080/courses?instructor=Martin%20Odersky&year=2022
  // GET localhost:8080/courses/defcd94f-6219-41a3-b94e-5b90d2ece627/students

  object InstructorQueryParamMatcher extends QueryParamDecoderMatcher[String]("instructor")
  object YearQueryParamMatcher       extends OptionalValidatingQueryParamDecoderMatcher[Int]("year")

  def courseRoutes[F[_]: Monad]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl.*

    HttpRoutes.of[F] { /*:? is always used for the first query param +& thereafter*/
      case GET -> Root / "courses" :? InstructorQueryParamMatcher(
            instructor
          ) +& YearQueryParamMatcher(maybeYear) =>
        val courses = CourseRepository.findCoursesByInstructor(instructor)
        maybeYear match {
          case Some(y) =>
            y.fold(
              _ => BadRequest("Parameter year is invalid"),
              year => Ok(courses.filter(_.year == year).asJson)
            )
          case None => Ok(courses.asJson)
        }
      case GET -> Root / "courses" / UUIDVar(courseId) / "students" =>
        CourseRepository.findCoursesById(courseId).map(_.students) match
          case Some(students) =>
            Ok(students.asJson, Header.Raw(CIString("My-custom-header"), "rockthejvm"))
          case None => NotFound(s"No course with $courseId was found")

    }
  }

  def healthEndpoint[F[_]: Monad]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl.*
    HttpRoutes.of[F] {
      case GET -> Root / "health" => Ok("all going great")
    }
  }

  def routerWithPathPrefixes = Router( // prefixes come after Root
    "/api" -> courseRoutes[IO],
    "/private" -> healthEndpoint[IO]
  ).orNotFound

  def allRoutes[F[_]: Monad]: HttpRoutes[F] = courseRoutes[F] <+> healthEndpoint[F]


  override def run: IO[Unit] = EmberServerBuilder
    .default[IO]
    .withHttpApp(routerWithPathPrefixes)
    .build
    .use(_ => IO.println("Server ready!") *> IO.never)
}
