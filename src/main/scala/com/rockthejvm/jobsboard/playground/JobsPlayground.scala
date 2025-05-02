package com.rockthejvm.jobsboard.playground

import cats.effect.*
import com.rockthejvm.jobsboard.core.LiveJobs
import doobie.*
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.*
import com.rockthejvm.jobsboard.domain.job.*
import com.rockthejvm.jobsboard.core.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.io.StdIn

object JobsPlayground extends IOApp.Simple {

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  val postgresResource: Resource[IO, HikariTransactor[IO]] =
    for {
      ec <- ExecutionContexts.fixedThreadPool(32)
      xa <- HikariTransactor.newHikariTransactor[IO](
        driverClassName = "org.postgresql.Driver",
        url = "jdbc:postgresql://localhost:5432/board",
        user = "docker",
        pass = "docker",
        ec
      )
    } yield xa

  val jobInfo = JobInfo.minimal(
    company = "Rock the JVM",
    title = "Scala Developer",
    description = "Join the best Scala team in the world!",
    externalUrl = "https://rockthejvm.com",
    remote = true,
    location = "remote"
  )

  override def run: IO[Unit] = postgresResource.use { xa =>
    for {
      jobs      <- LiveJobs[IO](xa)
      _         <- IO(println("Ready next ...")) *> IO(StdIn.readLine)
      id        <- jobs.create("ben@jobs.com", jobInfo)
      _         <- IO(println("Next ...")) *> IO(StdIn.readLine)
      list      <- jobs.all()
      _         <- IO(println(s"All jobs $list. Next ...")) *> IO(StdIn.readLine)
      _         <- jobs.update(id, jobInfo.copy(title = "Scala Developer (Senior)"))
      newJob    <- jobs.find(id)
      _         <- IO(println(s"Updated job $newJob. Next ...")) *> IO(StdIn.readLine)
      _         <- jobs.delete(id)
      listAfter <- jobs.all()
      _         <- IO(println(s"Jobs after delete $listAfter. Next ...")) *> IO(StdIn.readLine)
    } yield ()
  }
}
