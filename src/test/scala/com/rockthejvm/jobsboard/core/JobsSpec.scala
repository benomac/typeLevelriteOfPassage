package com.rockthejvm.jobsboard.core

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import com.rockthejvm.jobsboard.algebras.LiveJobs
import com.rockthejvm.jobsboard.domain.Job
import com.rockthejvm.jobsboard.fixtures.JobFixture
import doobie.ExecutionContexts
import doobie.postgres.implicits.*
import doobie.implicits.*
import doobie.util.fragment
import doobie.util.transactor.Transactor
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

class JobsSpec
    extends AsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with DoobieSpec
    with JobFixture {
  override val initScript: String = "sql/jobs.sql"

  "Jobs 'algebra'" - {

    "should return no job if the given uuid does not exist" in {
      transactor.use { xa =>
        val program = for {
          jobs <- LiveJobs[IO](xa)
          retrieved <- jobs.find(NotFoundJobUuid)
        } yield retrieved

        program.asserting(_ shouldBe None)
      }
    }

    "should return a job by id" in {
      transactor.use { xa =>
        val program = for {
          jobs <- LiveJobs[IO](xa)
          retrieved <- jobs.find(AwesomeJobUuid)
        } yield retrieved

        program.asserting(_ shouldBe Some(AwesomeJob))
      }
    }

    "should return all jobs" in {
      transactor.use { xa =>
        val program = for {
          jobs <- LiveJobs[IO](xa)
          retrieved <- jobs.all()
        } yield retrieved

        program.asserting(_ shouldBe List(AwesomeJob))
      }
    }

    "should create a new job" in {
      transactor.use { xa =>
        val program: IO[Option[Job.Job]] = for {
          jobs <- LiveJobs[IO](xa)
          retrieved <- jobs.create("ben@rockthejvm.com", RockTheJvmNewJob)
          maybeJob <- jobs.find(retrieved)
        } yield  maybeJob

        program.asserting(_.map(_.jobInfo) shouldBe Some(RockTheJvmNewJob))
      }
    }

    "should return an updated job if it exists" in {
      transactor.use { xa =>
        val program: IO[Option[Job.Job]] = for {
          jobs <- LiveJobs[IO](xa)
          maybeUpdatedJob <- jobs.update(AwesomeJobUuid, UpdatedAwesomeJob.jobInfo)
        } yield maybeUpdatedJob

        program.asserting(_ shouldBe Some(UpdatedAwesomeJob))
      }
    }

    "should return None when trying to update a job that doesn't exist" in {
      transactor.use { xa =>
        val program: IO[Option[Job.Job]] = for {
          jobs <- LiveJobs[IO](xa)
          maybeUpdatedJob <- jobs.update(NotFoundJobUuid, UpdatedAwesomeJob.jobInfo)
        } yield maybeUpdatedJob

        program.asserting(_ shouldBe None)
      }
    }

    "should delete a job if it exist" in {
      transactor.use { xa =>
        val program = for {
          jobs <- LiveJobs[IO](xa)
          numberOfDeletedJobs <- jobs.delete(AwesomeJobUuid)
          countOfJobs <- sql"""SELECT count(*) from jobs where id = $AwesomeJobUuid""".query[Int].unique.transact(xa)
        } yield (numberOfDeletedJobs, countOfJobs)

        program.asserting{
          case (numberOfDeletedJobs, countOfJobs) =>
            numberOfDeletedJobs shouldBe 1
            countOfJobs shouldBe 0
        }
      }
    }

    "should return 0 updated rows if the job id to delete is not found" in {
      transactor.use { xa =>
        val program = for {
          jobs <- LiveJobs[IO](xa)
          numberOfDeletedJobs <- jobs.delete(NotFoundJobUuid)
        } yield numberOfDeletedJobs

        program.asserting(_ shouldBe 0)
      }
    }

  }
}
