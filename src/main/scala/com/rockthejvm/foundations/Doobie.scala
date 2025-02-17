package com.rockthejvm.foundations

import cats.effect.kernel.Resource
import cats.effect.{ExitCode, IO, IOApp, MonadCancelThrow}
import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.fragment
import doobie.util.transactor.Transactor

import scala.concurrent.ExecutionContext

object Doobie extends IOApp.Simple {

  case class Student(id: Int, name: String)

  val xa: Transactor[IO] = Transactor.fromDriverManager[IO] (
    "org.postgresql.Driver", // JDBC connector
    "jdbc:postgresql://localhost:5432/demo", // database URL (can just do "jdbc:postgresql:demo" if using local host)
    "docker", // username
    "docker", // password
  )

  def findAllStudentName: IO[List[String]] = {
    val query: doobie.Query0[String] = sql"select name from students".query[String]
    val action: doobie.ConnectionIO[List[String]] = query.to[List]

    action.transact(xa)
  }

  def saveStudent(id: Int, name: String): IO[Int] = { // return type is IO[Int] as it will return the affected number of rows
    val query: fragment.Fragment = sql"insert into students values ($id, $name)"
    val action: doobie.ConnectionIO[Int] = query.update.run
    action.transact(xa)
  }

  def findStudentsByInitial(letter: String): IO[List[Student]] = {
    val selectFragment = fr"select id, name"
    val selectFromFragment = fr"from students"
    val whereFragment = fr"where left(name, 1) = $letter or left(name, 1) = ${letter.toLowerCase}"

    val query = selectFragment ++ selectFromFragment ++ whereFragment
    val action = query.query[Student].to[List]
    action.transact(xa)
  }

  // organize code
  trait Students[F[_]] { // "repository"
    def findById(id: Int): F[Option[Student]]
    def findAll: F[List[Student]]
    def create(name: String): F[Int]
    def clearDB: F[Int]
  }

  object Students {
    def apply[F[_]: MonadCancelThrow](xa: Transactor[F]): Students[F] = new Students[F] {

      override def findById(id: Int): F[Option[Student]] =
        sql"select id, name from students where id = $id".query[Student].option.transact(xa)


      override def findAll: F[List[Student]] =
        sql"select id, name from students".query[Student].to[List].transact(xa)

      override def create(name: String): F[Int] =
        sql"insert into students(name) values ($name)".update.withUniqueGeneratedKeys[Int]("id").transact(xa)

      override def clearDB: F[Int] =
        sql"TRUNCATE TABLE students RESTART IDENTITY".update.run.transact(xa)
    }

  }

  val postgresResource: Resource[IO, HikariTransactor[IO]] = for {
    ec <- ExecutionContexts.fixedThreadPool[IO](16)
    xa <- HikariTransactor.newHikariTransactor[IO](
      "org.postgresql.Driver", // JDBC connector
      "jdbc:postgresql://localhost:5432/demo", // database URL (can just do "jdbc:postgresql:demo" if using local host)
      "docker", // username
      "docker", // password
      ec
    )
  } yield xa

  val smallProgram = postgresResource.use { xa =>
    val studentsRepo = Students[IO](xa)
    for {
      id <- studentsRepo.create("ben")
//      _ <- studentsRepo.clearDB
      all <- studentsRepo.findAll
      _ <- IO.println(all)
      ben <- studentsRepo.findById(id)
      _ <- IO.println(s"The first student of rock the jvm id $ben")
    } yield ()
  }

  override def run = {
    smallProgram
  }

}
