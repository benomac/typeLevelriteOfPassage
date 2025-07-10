package com.rockthejvm.jobsboard.core

import cats.*
import cats.effect.MonadCancelThrow
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.*

import com.rockthejvm.jobsboard.logging.Syntax.*
import com.rockthejvm.jobsboard.domain.job.*
import com.rockthejvm.jobsboard.domain.pagination.*
import org.typelevel.log4cats.Logger

import java.util.UUID

trait Jobs[F[_]] {

  // "algebra"
  // CRUD
  def create(ownerEmail: String, jobInfo: JobInfo): F[UUID]
  def all(): F[List[Job]] // TODO fix thoughts on the all() method
  def all(filter: JobFilter, pagination: Pagination): F[List[Job]]
  def find(id: UUID): F[Option[Job]]
  def update(id: UUID, jobInfo: JobInfo): F[Option[Job]]
  def delete(id: UUID): F[Int]

}
/*
      id: UUID,
      date: Long,
      ownerEmail: String,
      company: String,
      title: String,
      description: String,
      externalUrl: String,
      remote: Boolean,
      location: String,
      salaryLo: Option[Int],
      salaryHi: Option[Int],
      currency: Option[String],
      country: Option[String],
      tags: Option[List[String]],
      image: Option[String],
      seniority: Option[String],
      other: Option[String],
      active: Boolean,
 */

class LiveJobs[F[_]: MonadCancelThrow: Logger] private(xa: Transactor[F]) extends Jobs[F] {

  override def create(ownerEmail: String, jobInfo: JobInfo): F[UUID] =
    sql"""
         INSERT INTO jobs(
            date,
            ownerEmail,
            company,
            title,
            description,
            externalUrl,
            remote,
            location,
            salaryLo,
            salaryHi,
            currency,
            country,
            tags,
            image,
            seniority,
            other,
            active
         ) VALUES (
          ${System.currentTimeMillis()},
          $ownerEmail,
          ${jobInfo.company},
          ${jobInfo.title},
          ${jobInfo.description},
          ${jobInfo.externalUrl},
          ${jobInfo.remote},
          ${jobInfo.location},
          ${jobInfo.salaryLo},
          ${jobInfo.salaryHi},
          ${jobInfo.currency},
          ${jobInfo.country},
          ${jobInfo.tags},
          ${jobInfo.image},
          ${jobInfo.seniority},
          ${jobInfo.other},
          false
         )
       """.update
      .withUniqueGeneratedKeys[UUID]("id")
      .transact(xa)

  override def all(): F[List[Job]] =
    sql"""SELECT
            id,
            date,
            ownerEmail,
            company,
            title,
            description,
            externalUrl,
            remote,
            location,
            salaryLo,
            salaryHi,
            currency,
            country,
            tags,
            image,
            seniority,
            other,
            active
          FROM jobs
       """
      .query[Job]
      .to[List]
      .transact(xa)

  override def all(filter: JobFilter, pagination: Pagination): F[List[Job]] = {
    val selectFragment: Fragment = {
      fr"""SELECT
              id,
              date,
              ownerEmail,
              company,
              title,
              description,
              externalUrl,
              remote,
              location,
              salaryLo,
              salaryHi,
              currency,
              country,
              tags,
              image,
              seniority,
              other,
              active
         """
    }

    val fromFragment: Fragment = {
      fr"FROM jobs"
    }

    val whereFragment: Fragment = {
      Fragments.whereAndOpt(
        filter.companies.toNel.map(companies => Fragments.in(fr"company", companies)), // Option["WHERE company in $companies"]
        filter.locations.toNel.map(locations => Fragments.in(fr"location", locations)),
        filter.countries.toNel.map(countries => Fragments.in(fr"country", countries)),
        filter.seniorities.toNel.map(seniorities => Fragments.in(fr"seniority", seniorities)),
        filter.tags.toNel.map(tags => //intersection between filter.tags and rows tags
          Fragments.or(tags.toList.map(tag => fr"$tag=any(tags)")*)
        ),
        filter.maxSalary.map(salary => fr"salaryHi > $salary"),
        filter.remote.some.map(remote => fr"remote = $remote")
      )
    }

    val paginationFragment: Fragment = {
      fr"ORDER BY id LIMIT ${pagination.limit} OFFSET ${pagination.offset}"
    }

    val statement = selectFragment |+| fromFragment |+| whereFragment |+| paginationFragment

    Logger[F].info(statement.toString) *>
    statement
      .query[Job]
      .to[List]
      .transact(xa)
      .logError(e => s"Failed query: ${e.getMessage}")
  }

  override def find(id: UUID): F[Option[Job]] =
    sql"""SELECT
              id,
              date,
              ownerEmail,
              company,
              title,
              description,
              externalUrl,
              remote,
              location,
              salaryLo,
              salaryHi,
              currency,
              country,
              tags,
              image,
              seniority,
              other,
              active
            FROM jobs WHERE id = $id"""
      .query[Job]
      .option
      .transact(xa)

  override def update(id: UUID, jobInfo: JobInfo): F[Option[Job]] =
    sql"""
         UPDATE jobs
         SET
          company = ${jobInfo.company},
          title = ${jobInfo.title},
          description = ${jobInfo.description},
          externalUrl = ${jobInfo.externalUrl},
          remote = ${jobInfo.remote},
          location = ${jobInfo.location},
          salaryLo = ${jobInfo.salaryLo},
          salaryHi = ${jobInfo.salaryHi},
          currency = ${jobInfo.currency},
          country = ${jobInfo.country},
          tags = ${jobInfo.tags},
          image = ${jobInfo.image},
          seniority = ${jobInfo.seniority},
          other = ${jobInfo.other}
        WHERE id = $id
       """.update.run
      .transact(xa)
      .flatMap(_ => find(id)) // return the updated job

  override def delete(id: UUID): F[Int] =
    sql"""
         DELETE FROM jobs
         WHERE id = $id
       """
      .update
      .run
      .transact(xa)
}

object LiveJobs {

  given jobRead: Read[Job] = Read[
    (
        UUID,                 // id
        Long,                 // date
        String,               // ownerEmail
        String,               // company
        String,               // title
        String,               // description
        String,               // externalUrl
        Boolean,              // remote
        String,               // location
        Option[Int],          // salaryLo
        Option[Int],          // salaryHi
        Option[String],       // currency
        Option[String],       // country
        Option[List[String]], // tags
        Option[String],       // image
        Option[String],       // seniority
        Option[String],       // other
        Boolean               // active
    )
  ].map {
    case (
          id: UUID,                              // id,
          date: Long,                            // date,
          ownerEmail: String,                    // ownerEmail,
          company: String,                       // company,
          title: String,                         // title,
          description: String,                   // description,
          externalUrl: String,                   // externalUrl,
          remote: Boolean,                       // remote,
          location: String,                      // location,
          salaryLo: Option[Int] @unchecked,      // salaryLo,
          salaryHi: Option[Int] @unchecked,      // salaryHi,
          currency: Option[String] @unchecked,   // currency,
          country: Option[String] @unchecked,    // country,
          tags: Option[List[String]] @unchecked, // tags,
          image: Option[String] @unchecked,      // image,
          seniority: Option[String] @unchecked,  // seniority,
          other: Option[String] @unchecked,      // other,
          active: Boolean                        // active
        ) =>
      Job(
        id = id,
        date = date,
        ownerEmail = ownerEmail,
        JobInfo(
          company = company,
          title = title,
          description = description,
          externalUrl = externalUrl,
          remote = remote,
          location = location,
          salaryLo = salaryLo,
          salaryHi = salaryHi,
          currency = currency,
          country = country,
          tags = tags,
          image = image,
          seniority = seniority,
          other = other
        ),
        active = active
      )
  }

  def apply[F[_]: MonadCancelThrow: Logger](xa: Transactor[F]): F[LiveJobs[F]] = {
    new LiveJobs[F](xa).pure[F]
  }
}
