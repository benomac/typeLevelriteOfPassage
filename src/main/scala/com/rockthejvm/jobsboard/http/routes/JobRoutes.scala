package com.rockthejvm.jobsboard.http.routes

import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import cats.effect.*
import cats.implicits.*
import org.typelevel.log4cats.Logger
import org.http4s.*
import org.http4s.dsl.*
import org.http4s.dsl.impl.*
import org.http4s.server.*
import cats.syntax.semigroupk.*
import org.http4s.dsl.Http4sDsl

import scala.collection.mutable
import java.util.UUID
import com.rockthejvm.jobsboard.domain.Job.*
import com.rockthejvm.jobsboard.algebras.*
import com.rockthejvm.jobsboard.http.responses.*
import com.rockthejvm.jobsboard.logging.Syntax.*

class JobRoutes[F[_]: Concurrent: Logger] private (jobs: Jobs[F]) extends Http4sDsl[F] {

  // POST /jobs?offset=x&limit=y { filters } // TODO add query params and filters later
  private val allJobsRoute: HttpRoutes[F] = HttpRoutes.of[F] { case POST -> Root =>
    for {
      jobsList <- jobs.all()
      resp     <- Ok(jobsList)
    } yield resp

  }

  // GET /jobs/uuid
  private val findJobRoute: HttpRoutes[F] = HttpRoutes.of[F] { case GET -> Root / UUIDVar(id) =>
    jobs.find(id).flatMap {
      case Some(job) => Ok(job)
      case None      => NotFound(FailureResponse(s"Job with $id not found"))
    }
  }

  private val createJobRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "create" =>
      for {
        jobInfo <- req.as[JobInfo].logError(e => s"Parsing payload failed: $e")
        jobId   <- jobs.create("ben@email.com", jobInfo)
        resp    <- Created(jobId)
      } yield resp
  }

  // PUT /jobs/uuid { jobInfo }
  private val updateJobRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ PUT -> Root / UUIDVar(id) =>
      for {
        jobInfo     <- req.as[JobInfo]
        maybeNewJob <- jobs.update(id, jobInfo)
        resp <- maybeNewJob match { // change to flatMap to test if that works in the same way
          case Some(job) => Ok()
          case None      => NotFound(FailureResponse(s"Cannot update job $id: id not found"))
        }
      } yield resp
  }

  // DELETE /jobs/uuid
  private val deleteJobRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req@DELETE -> Root / UUIDVar(id) =>
      jobs.find(id).flatMap {
        case Some(job) =>
          for {
            _ <- jobs.delete(id)
            resp <- Ok()
          } yield resp
        case None => NotFound(FailureResponse(s"Cannot delete job $id: id not found"))
      }
  }

  val routes = Router(
    "/jobs" -> (allJobsRoute <+> findJobRoute <+> createJobRoute <+> updateJobRoute <+> deleteJobRoute)
  )
}

object JobRoutes {
  def apply[F[_]: Concurrent: Logger](jobs: Jobs[F]) = new JobRoutes[F](jobs)
}
