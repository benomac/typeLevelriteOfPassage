package com.rockthejvm.jobsboard.core

import scala.concurrent.duration.DurationInt
import cats.data.OptionT
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import com.rockthejvm.jobsboard.domain.auth.NewPasswordInfo
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import tsec.authentication.{IdentityStore, JWTAuthenticator}
import tsec.mac.jca.HMACSHA256
import com.rockthejvm.jobsboard.domain.security.*
import com.rockthejvm.jobsboard.domain.user
import com.rockthejvm.jobsboard.domain.user.*
import com.rockthejvm.jobsboard.fixtures.*
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

class AuthSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers with UserFixture {

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val mockedUsers: Users[IO] = new Users[IO] {

    override def find(email: String): IO[Option[User]] =
      if (email == benEmail) IO.pure(Some(ben))
      else IO.pure(None)

    override def create(user: User): IO[String] = IO.pure(user.email)

    override def update(user: User): IO[Option[User]] = IO.pure(Some(user))

    override def delete(email: String): IO[Boolean] = IO.pure(true)
  }

  val mockedAuthenticator: Authenticator[IO] = {
    // key for hashing
    val key = HMACSHA256.unsafeGenerateKey
    // Identity store
    val idStore: IdentityStore[IO, String, User] = (email: String) =>
      if (email == benEmail) OptionT.pure(ben)
      else if (email == heathEmail) OptionT.pure(heath)
      else OptionT.none[IO, User]
    // jwt authenticator
    JWTAuthenticator.unbacked.inBearerToken(
      1.day,   // expiry of tokens
      None,    // max idle time
      idStore, // identity store
      key      // hash key
    )
  }

  "Auth 'algebra'" - {

    "login should return none if the user doesn't exist" in {
      val program = for {
        auth       <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        maybeToken <- auth.login("user@rockthejvm.com", "password")
      } yield maybeToken

      program.asserting(_ shouldBe None)
    }

    "login should return none if the user exists but the password is incorrect" in {
      val program = for {
        auth       <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        maybeToken <- auth.login(benEmail, "password")
      } yield maybeToken

      program.asserting(_ shouldBe None)
    }

    "login should return a token if the user exists and the password is correct" in {
      val program = for {
        auth       <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        maybeToken <- auth.login(benEmail, "rockthejvm")
      } yield maybeToken

      program.asserting(_ shouldBe defined)
    }

    "signing up should not create a user with an existing email" in {
      val program = for {
        auth <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        maybeUser <- auth.signUp(
          NewUserInfo(
            benEmail,
            "somePassword",
            Some("ben"),
            Some("mac"),
            Some("company")
          )
        )
      } yield maybeUser

      program.asserting(_ shouldBe None)
    }

    "signing up should create a completely new user" in {
      val program: IO[Option[User]] = for {
        auth <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        maybeUser <- auth.signUp(
          NewUserInfo(
            "bob@rockthejvm.com",
            "somePassword",
            Some("bob"),
            Some("man"),
            Some("bob company")
          )
        )
      } yield maybeUser

      program.asserting {
        case Some(user) =>
          user.email shouldBe "bob@rockthejvm.com" // these are greyed out, but they are being ran in the test.
          user.firstName shouldBe Some("bob")
          user.lastName shouldBe Some("man")
          user.company shouldBe Some("bob company")
          user.role shouldBe Role.RECRUITER
        case _ => fail("Expected a user to be created")
      }
    }

    "change password should return Right(None) if the user doesn't exist" in {
      val program = for {
        auth <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        result <- auth.changePassword(
          "user@rockthejvm.com",
          NewPasswordInfo("oldPassword", "newPassword")
        )
      } yield result

      program.asserting(_ shouldBe Right(None))
    }

    "change password should return Left with an error if the user does exist but the old password in incorrect" in {
      val program = for {
        auth <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        result <- auth.changePassword(
          benEmail,
          NewPasswordInfo("wrongOldPassword", "newPassword")
        )
      } yield result

      program.asserting(_ shouldBe Left("Invalid password"))
    }

    "change password should return correctly change password if all details are correct" in {
      val program = for {
        auth <- LiveAuth[IO](mockedUsers, mockedAuthenticator)
        result <- auth.changePassword(
          benEmail,
          NewPasswordInfo(oldPassword = "rockthejvm", newPassword = "rockthejvmNewPassword")
        )
        isNicePassword: Boolean <- result match
          case Right(Some(user)) => BCrypt.checkpwBool[IO](
            "rockthejvmNewPassword",
            PasswordHash[BCrypt]("$2a$10$aBgVZNOt.byb3Sj19eaTuuDWWKoT/Ebbo9zrzDqLgKx4Snon.lP8i")
          )
          case _ => IO.pure(false)
      } yield isNicePassword

      program.asserting(_ shouldBe true)
    }

  }

}
