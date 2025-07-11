package com.rockthejvm.jobsboard.core

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import com.rockthejvm.jobsboard.domain.user.User
import com.rockthejvm.jobsboard.fixtures.*
import doobie.implicits.toSqlInterpolator
import doobie.ExecutionContexts
import doobie.postgres.implicits.*
import doobie.implicits.*
import doobie.util.fragment
import doobie.util.transactor.Transactor
import org.postgresql.util.PSQLException
import org.scalatest.Inside
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class UsersSpec
    extends AsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with Inside
    with DoobieSpec
    with UserFixture {

  override val initScript: String = "sql/users.sql"

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  "Users 'algebra'" - {

    "should retrieve a user by email" in {
      transactor.use { xa =>
        val program = for {
          users     <- LiveUsers[IO](xa)
          retrieved <- users.find("ben@rockthejvm.com")
        } yield retrieved

        program.asserting(_ shouldBe Some(ben))
      }
    }

    "should return None if the email does not exist" in {
      transactor.use { xa =>
        val program = for {
          users     <- LiveUsers[IO](xa)
          retrieved <- users.find("notfound@rockthejvm.com")
        } yield retrieved

        program.asserting(_ shouldBe None)
      }
    }

    "should create a new user" in {
      transactor.use { xa =>
        val program: IO[(String, Option[User])] = for {
          users  <- LiveUsers[IO](xa)
          userId <- users.create(newUser)
          maybeUser <- sql"SELECT * FROM users WHERE email = ${newUser.email}"
            .query[User]
            .option
            .transact(xa)
        } yield (userId, maybeUser)

        program.asserting { case (userId, maybeUser) =>
          (userId, maybeUser) shouldBe (newUser.email, Some(newUser))
        }
      }
    }

    "should fail creating a new user if the email already exists" in {
      transactor.use { xa =>
        val program = for {
          users  <- LiveUsers[IO](xa)
          userId <- users.create(ben).attempt
        } yield userId

        program.asserting { outcome =>
          inside(outcome) {
            case Left(e) => e shouldBe a[PSQLException]
            case _       => fail()
          }
        }
      }
    }

    "should return None when updating a user that does not exist" in {
      transactor.use { xa =>
        val program = for {
          users     <- LiveUsers[IO](xa)
          maybeUser <- users.update(newUser)
        } yield maybeUser

        program.asserting(_ shouldBe None)
      }
    }

    "should update an existing user" in {
      transactor.use { xa =>
        val program = for {
          users     <- LiveUsers[IO](xa)
          maybeUser <- users.update(updatedHeath)
        } yield maybeUser

        program.asserting(_ shouldBe Some(updatedHeath))
      }
    }

    "should delete a user" in {
      transactor.use { xa =>
        val program: IO[(Boolean, Option[User])] = for {
          users  <- LiveUsers[IO](xa)
          result <- users.delete("ben@rockthejvm.com")
          maybeUser <-
            sql"SELECT * FROM users WHERE email = 'ben@rockthejvm.com'"
              .query[User]
              .option
              .transact(xa)
        } yield (result, maybeUser)

        program.asserting { case (result, maybeUser) =>
          (result, maybeUser) shouldBe (true, None)
        }
      }
    }

    "should NOT delete a user that does not exist" in {
      transactor.use { xa =>
        val program = for {
          users  <- LiveUsers[IO](xa)
          result <- users.delete("nobody@rockthejvm.com")
        } yield result

        program.asserting(_ shouldBe false)
      }
    }

  }
}
