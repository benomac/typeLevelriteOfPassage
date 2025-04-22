package com.rockthejvm.jobsboard.http.routes

import cats.effect.*
import cats.implicits.*
import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.freespec.AsyncFreeSpec
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits.*
import org.scalatest.matchers.should.Matchers
import com.rockthejvm.jobsboard.algebras.Jobs
import com.rockthejvm.jobsboard.algebras.*
import com.rockthejvm.jobsboard.domain.pagination.*
import com.rockthejvm.jobsboard.domain.job.*
import com.rockthejvm.jobsboard.fixtures.*
import org.http4s.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.UUID

class JobRoutesSpec
    extends AsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with Http4sDsl[IO]
    with JobFixture {

  /////////////////////////////////////////////////////////////////////////////////
  // prep
  /////////////////////////////////////////////////////////////////////////////////

  val jobs: Jobs[IO] = new Jobs[IO] {
    override def create(ownerEmail: String, jobInfo: JobInfo): IO[UUID] =
      IO.pure(NewJobUuid)

    override def all(): IO[List[Job]] =
      IO.pure(List(AwesomeJob))

    override def all(filter: JobFilter, pagination: Pagination): IO[List[Job]] =
      if(filter.remote) IO.pure(List())
      else IO.pure(List(AwesomeJob))

    override def find(id: UUID): IO[Option[Job]] =
      if (id == AwesomeJobUuid)
        AwesomeJob.some.pure[IO]
      else
        none.pure[IO]

    override def update(id: UUID, jobInfo: JobInfo): IO[Option[Job]] =
      if (id == AwesomeJobUuid)
        UpdatedAwesomeJob.some.pure[IO]
      else none.pure[IO]

    override def delete(id: UUID): IO[Int] =
      if (id == AwesomeJobUuid)
        1.pure[IO]
      else
        0.pure[IO]

  }

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  // this is what we are testing
  val jobsRoutes: HttpRoutes[IO] = JobRoutes[IO](jobs).routes

  /////////////////////////////////////////////////////////////////////////////////
  // tests
  /////////////////////////////////////////////////////////////////////////////////

  "JobsRoutes" - {

    "should return a job with a given id" in {
      for {
        response <- jobsRoutes.orNotFound.run(
          Request(method = Method.GET, uri = uri"/jobs/843df718-ec6e-4d49-9289-f799c0f40064")
        )
        job <- response.as[Job]
      } yield {
        response.status shouldBe Status.Ok
        job shouldBe AwesomeJob
      }
    }

    "should return all jobs" in {
      for {
        response <- jobsRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/jobs")
            .withEntity(JobFilter()) //empty filter
        )
        job <- response.as[List[Job]]
      } yield {
        response.status shouldBe Status.Ok
        job shouldBe List(AwesomeJob)
      }
    }

    "should return all jobs that satisfy a filter" in {
      for {
        response <- jobsRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/jobs")
            .withEntity(JobFilter(remote = true)) 
        )
        job <- response.as[List[Job]]
      } yield {
        response.status shouldBe Status.Ok
        job shouldBe List()
      }
    }

    "should create a new job" in {
      for {
        response <- jobsRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/jobs/create")
            .withEntity(AwesomeJob.jobInfo)
        )
        uuid <- response.as[UUID]
      } yield {
        response.status shouldBe Status.Created
        uuid shouldBe NewJobUuid
      }
    }

    "should only update a job that exists" in {
      for {
        responseOk <- jobsRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/jobs/843df718-ec6e-4d49-9289-f799c0f40064")
            .withEntity(UpdatedAwesomeJob.jobInfo)
        )
        responseNotFound <- jobsRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/jobs/843df718-ec6e-4d49-9289-000000000000")
            .withEntity(UpdatedAwesomeJob.jobInfo)
        )
      } yield {
        responseOk.status shouldBe Status.Ok
        responseNotFound.status shouldBe Status.NotFound
      }
    }

    "should only delete a job that exists" in {
      for {
        responseOk <- jobsRoutes.orNotFound.run(
          Request(method = Method.DELETE, uri = uri"/jobs/843df718-ec6e-4d49-9289-f799c0f40064")
        )
        responseNotFound <- jobsRoutes.orNotFound.run(
          Request(method = Method.DELETE, uri = uri"/jobs/843df718-ec6e-4d49-9289-000000000000")
        )
      } yield {
        responseOk.status shouldBe Status.Ok
        responseNotFound.status shouldBe Status.NotFound
      }
    }

  }

}
