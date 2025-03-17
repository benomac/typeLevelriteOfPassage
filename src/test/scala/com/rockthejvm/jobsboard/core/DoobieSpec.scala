package com.rockthejvm.jobsboard.core

import cats.effect.{IO, Resource}
import doobie.*
import doobie.implicits.*
import doobie.util.*
import doobie.hikari.HikariTransactor
import org.testcontainers.containers.PostgreSQLContainer

trait DoobieSpec { // Similar to itv user trait "PostgresSpec"
  // simulate a database
  // docker containers
  // testContainers


  // to be implemented by whatever test case interacts with the db
  val initScript: String

  val postgres: Resource[IO, PostgreSQLContainer[Nothing]] = {
    val acquire = IO {
      val container: PostgreSQLContainer[Nothing] =
        new PostgreSQLContainer("postgres").withInitScript(initScript)
      container.start()
      container
    }
    val release = (container: PostgreSQLContainer[Nothing]) => IO(container.stop())
    Resource.make(acquire)(release)
  }

  // set up a postgres transactor
  val transactor: Resource[IO, Transactor[IO]] =
    for {
      db <- postgres
      ce <- ExecutionContexts.fixedThreadPool[IO](1)
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        db.getJdbcUrl,
        db.getUsername,
        db.getPassword,
        ce
      )
    } yield xa
}
