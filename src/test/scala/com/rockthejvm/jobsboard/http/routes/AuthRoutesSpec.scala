package com.rockthejvm.jobsboard.http.routes

import cats.data.OptionT
import cats.effect.*
import cats.implicits.*
import cats.effect.testing.scalatest.AsyncIOSpec
import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import org.scalatest.freespec.AsyncFreeSpec
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits.*
import org.scalatest.matchers.should.Matchers
import org.http4s.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import com.rockthejvm.jobsboard.core.*
import com.rockthejvm.jobsboard.domain.user.*
import com.rockthejvm.jobsboard.domain.auth.*
import com.rockthejvm.jobsboard.domain.auth
import com.rockthejvm.jobsboard.domain.security.*
import com.rockthejvm.jobsboard.domain.auth.NewPasswordInfo
import com.rockthejvm.jobsboard.domain.security.JWTToken
import com.rockthejvm.jobsboard.domain.user.{NewUserInfo, User}
import com.rockthejvm.jobsboard.fixtures.*
import org.http4s.headers.Authorization
import org.typelevel.ci.CIStringSyntax
import tsec.authentication.{IdentityStore, JWTAuthenticator}
import tsec.jws.mac.JWTMac
import tsec.mac.jca.HMACSHA256

import scala.concurrent.duration.DurationInt

class AuthRoutesSpec
    extends AsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with Http4sDsl[IO]
    with UserFixture {

  /////////////////////////////////////////////////////////////////////////////////
  // prep
  /////////////////////////////////////////////////////////////////////////////////

  val mockedAuthenticator: Authenticator[IO] = {
    // key for hashing
    val key = HMACSHA256.unsafeGenerateKey
    // Identity store
    val idStore: IdentityStore[IO, String, User] = (email: String) =>
      if (email == benEmail) OptionT.pure(ben)
      else if (email == heathEmail)
        OptionT.pure(heath) // may need to remove this for the change password unhappy path test
      else OptionT.none[IO, User]
    // jwt authenticator
    JWTAuthenticator.unbacked.inBearerToken(
      1.day, // expiry of tokens
      None, // max idle time
      idStore, // identity store
      key // hash key
    )
  }

  val mockedAuth: Auth[IO] = new Auth[IO] {
    // TODO make sure Ben already exists
    override def login(email: String, password: String): IO[Option[JWTToken]] = ???

    override def signUp(newUserInfo: NewUserInfo): IO[Option[User]] = ???

    override def changePassword(
                                 email: String,
                                 newPasswordInfo: NewPasswordInfo
                               ): IO[Either[String, Option[User]]] = ???
  }

  extension (r: Request[IO])
    def withBearerToken(a: JWTToken): Request[IO] =
      r.putHeaders {
        val jwtString = JWTMac.toEncodedString[IO, HMACSHA256](a.jwt)
        Authorization(Credentials.Token(AuthScheme.Bearer, jwtString))
      }

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  val authRoutes: HttpRoutes[IO] = AuthRoutes[IO](mockedAuth).routes

  /////////////////////////////////////////////////////////////////////////////////
  // tests
  /////////////////////////////////////////////////////////////////////////////////

  "Auth Routes" - {
    "should return a 401 unauthorised if login fails" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/login")
            .withEntity(LoginInfo(benEmail, "wrongPassword"))
        )
      } yield {
        response.status shouldBe Status.Unauthorized
      }
    }

    "should return a 200 - OK and a JWT if login succeeds" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/login")
            .withEntity(LoginInfo(benEmail, benRawPassword))
        )
      } yield {
        response.status shouldBe Status.Ok
        response.headers.get(ci"Authorisation") shouldBe defined
      }
    }

    "should return a 400 - Bad request when creating a new user with an email that already exists" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/users")
            .withEntity(newUserben)
        )
      } yield {
        response.status shouldBe Status.BadRequest
      }
    }

    "should return a 201 - Created when creating a new user, and it succeeds" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/users")
            .withEntity(newUserHeath)
        )
      } yield {
        response.status shouldBe Status.Ok
      }
    }

    "should return a 401 - Unauthorised if logging out without a valid JwT token" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/logout")
        )
      } yield {
        response.status shouldBe Status.Unauthorized
      }
    }

    "should return a 200 - OK if logging out with a valid JwT token" in {
      for {
        jwt <- mockedAuthenticator.create(benEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/logout")
            .withBearerToken(jwt)
        )
      } yield {
        response.status shouldBe Status.Ok
      }
    }

    "should return 404 - not found if the user doesn't exist when trying to change password" in {
      for {
        jwt <- mockedAuthenticator.create(heathEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/password")
            .withBearerToken(jwt)
            .withEntity(NewPasswordInfo("rockthejvm", "newpassword"))
        )
      } yield {
        response.status shouldBe Status.NotFound
      }
    }

    "should return 403 - forbidden when trying to change password with an incorrect old password" in {
      for {
        jwt <- mockedAuthenticator.create(benEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/password")
            .withBearerToken(jwt)
            .withEntity(NewPasswordInfo("rockthejam", "newpassword"))
        )
      } yield {
        response.status shouldBe Status.Forbidden
      }
    }

    "should return 401 - unauthorized when trying to change password without a jwt" in {
      for {
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/password")
            .withEntity(NewPasswordInfo(benRawPassword, "newpassword"))
        )
      } yield {
        response.status shouldBe Status.Unauthorized
      }
    }

    "should return 200 - OK when trying to change password with the correct creds" in {
      for {
        jwt <- mockedAuthenticator.create(benEmail)
        response <- authRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/auth/password")
            .withBearerToken(jwt)
            .withEntity(NewPasswordInfo(benRawPassword, "newpassword"))
        )
      } yield {
        response.status shouldBe Status.Ok
      }
    }

  }

}
