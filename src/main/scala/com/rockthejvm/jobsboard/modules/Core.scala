package com.rockthejvm.jobsboard.modules

import cats.effect.*
import cats.implicits.*
import com.rockthejvm.jobsboard.algebras.{Jobs, LiveJobs}
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.*
import com.rockthejvm.jobsboard.algebras.*

final class Core[F[_]] private (val jobs: Jobs[F])

// postgres -> Jobs -> Core -> httpAPI -> app
object Core {

  def postgresResource[F[_] : Async]: Resource[F, HikariTransactor[F]] =
    for {
      ec <- ExecutionContexts.fixedThreadPool(32)
      xa <- HikariTransactor.newHikariTransactor[F](
        driverClassName = "org.postgresql.Driver",
        url = "jdbc:postgresql://localhost:5432/board", //TODO move to config
        user = "docker",
        pass = "docker",
        ec
      )
    } yield xa
  def apply[F[_] : Async] : Resource[F, Core[F]] =
    postgresResource[F]
      .evalMap(postgres => LiveJobs[F](postgres))
      .map(jobs => new Core(jobs))
}
