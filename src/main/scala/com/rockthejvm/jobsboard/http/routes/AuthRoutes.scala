package com.rockthejvm.jobsboard.http.routes

import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import cats.effect.*
import cats.implicits.*
import org.typelevel.log4cats.Logger
import org.http4s.{HttpRoutes, Response, Status}
import org.http4s.server.Router
import tsec.authentication.{SecuredRequestHandler, TSecAuthService, asAuthed}
import com.rockthejvm.jobsboard.http.validation.Syntax.*
import com.rockthejvm.jobsboard.core.*
import com.rockthejvm.jobsboard.domain.auth.{LoginInfo, NewPasswordInfo}
import com.rockthejvm.jobsboard.domain.security.*
import com.rockthejvm.jobsboard.domain.user.{NewUserInfo, User}
import com.rockthejvm.jobsboard.http.responses.FailureResponse

import scala.language.implicitConversions


class AuthRoutes[F[_]: Concurrent: Logger] private (auth: Auth[F]) extends HttpValidationDsl[F] {

  private val authenticator = auth.authenticator
  private val securedHandler: SecuredRequestHandler[F, String, User, JWTToken] = SecuredRequestHandler(authenticator)
  // POST /auth/login { LoginInfo } => OK with JWT as Authorization: Bearer {JWT}
  private val loginRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "login" =>
      req.validate[LoginInfo] { loginInfo =>
      val maybeJWTToken: F[Option[JWTToken]] = for {
          maybeToken <- auth.login(loginInfo.email, loginInfo.password)
          _ <- Logger[F].info(s"User logging in: ${loginInfo.email}")
        } yield maybeToken

        maybeJWTToken.map {
          case Some(token) => authenticator.embed(Response(Status.Ok), token)
          case None => Response(Status.Unauthorized)
        }
      }
  }

  // POST /auth/users { NewUserInfo } => 201 Created or BadRequest
  private val createUserRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "users" =>
      req.validate[NewUserInfo] { newUserInfo =>
        for {
          maybeNewUser <- auth.signUp(newUserInfo)
          response <- maybeNewUser match
            case Some(user) => Created(user.email)
            case None => BadRequest(s"User with email ${newUserInfo.email} already exists.")
        } yield response
      }
  }

  // PUT /auth/users/password { NewPasswordInfo } { Authorization: Bearer {jwt} } => 200 OK
  private val changePasswordRoute: AuthRoute[F] = {
    case req @ PUT -> Root / "users" / "password" asAuthed user =>
      req.request.validate[NewPasswordInfo] { newPasswordInfo =>
        for {
          maybeUserOrError <- auth.changePassword(user.email, newPasswordInfo)
          resp <- maybeUserOrError match
            case Right(Some(_)) => Ok()
            case Right(None) => NotFound(FailureResponse(s"User ${user.email} not found."))
            case Left(_) => Forbidden()
        } yield resp
      }
  }

  // POST /auth/logout { Authorization: Bearer {jwt} } => 200 OK
  private val logoutRoute: AuthRoute[F] = {
    case req @ POST -> Root / "logout" asAuthed _ =>
      val token: JWTToken =  req.authenticator
      for {
        _ <- authenticator.discard(token)
        resp <- Ok()
      } yield resp
  }

  // DELETE /auth/users/<email you want to delete>
  private val deleteUserRoute: AuthRoute[F] = {
    case req @ DELETE -> Root / "users" / email asAuthed user =>
      auth.delete(email).flatMap {
        case true => Ok()
        case false => NotFound()
      }
  }

  val unauthedRoutes = loginRoute <+> createUserRoute
  val authedRoutes = securedHandler.liftService(
    changePasswordRoute.restrictedTo(allRoles) |+|
      logoutRoute.restrictedTo(allRoles) |+|
      deleteUserRoute.restrictedTo(adminOnly)
  )

  val routes: HttpRoutes[F] = Router(
    "/auth" -> (unauthedRoutes <+> authedRoutes)
  )
}


object AuthRoutes {
  def apply[F[_]: Concurrent: Logger](auth: Auth[F]) =
    new AuthRoutes[F](auth)
}