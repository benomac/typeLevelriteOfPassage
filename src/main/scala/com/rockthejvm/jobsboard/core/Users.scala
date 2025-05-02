package com.rockthejvm.jobsboard.core

import cats.*
import cats.effect.MonadCancelThrow
import cats.implicits.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.*
import cats.effect.MonadCancelThrow
import com.rockthejvm.jobsboard.domain.user.{Role, User}
import doobie.util.transactor.Transactor
import org.typelevel.log4cats.Logger

trait Users[F[_]] {
  // CRUD
  def find(email: String): F[Option[User]]
  def create(user: User): F[String]
  def update(user: User): F[Option[User]]
  def delete(email: String): F[Boolean]
}


final class LiveUsers[F[_]: MonadCancelThrow] private (xa: Transactor[F]) extends Users[F]:
  override def find(email: String): F[Option[User]] =
    sql"SELECT * FROM users WHERE email = $email"
      .query[User]
      .option
      .transact(xa)

  override def create(user: User): F[String] =
    sql"""INSERT INTO users (
          email,
          hashedPassword,
          firstName,
          lastName,
          company,
          role
         ) VALUES (
         ${user.email},
         ${user.hashedPassword},
         ${user.firstName},
         ${user.lastName},
         ${user.company},
         ${user.role}
         )
       """
      .update
      .run
      .transact(xa)
      .map(_ => user.email)

  override def update(user: User): F[Option[User]] =
    sql"""
         UPDATE users
           SET
            hashedPassword = ${user.hashedPassword},
            firstName = ${user.firstName},
            lastName = ${user.lastName},
            company = ${user.company},
            role = ${user.role}
           WHERE email = ${user.email}
       """
      .update
      .run
      .transact(xa)
      .flatMap(_ => find(user.email))

  override def delete(email: String): F[Boolean] =
    sql"DELETE FROM users WHERE email = $email"
      .update
      .run
      .transact(xa)
      .map(_ > 0)

object LiveUsers {

  def apply[F[_]: MonadCancelThrow: Logger](xa: Transactor[F]): F[LiveUsers[F]] =
    new LiveUsers[F](xa).pure[F]
}