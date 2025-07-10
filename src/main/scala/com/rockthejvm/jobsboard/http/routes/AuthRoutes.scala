package com.rockthejvm.jobsboard.http.routes

import cats.effect.*
import cats.implicits.*
import org.typelevel.log4cats.Logger
import org.http4s.HttpRoutes
import org.http4s.server.Router

import com.rockthejvm.jobsboard.http.validation.Syntax.*
import com.rockthejvm.jobsboard.core.*


class AuthRoutes[F[_]: Concurrent: Logger] private (auth: Auth[F]) extends HttpValidationDsl[F] {

  // POST /auth/login { LoginInfo } => OK with JWT as Authorization: Bearer {JWT}
  private val loginRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case POST -> Root / "login" =>
      Ok("TODO")
  }

  // POST /auth/users { NewUserInfo } => 201 Created
  private val createUserRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case POST -> Root / "users" =>
      Ok("TODO")
  }

  // PUT /auth/users/password { NewPasswordInfo } { Authorization: Bearer {jwt} } => 200 OK
  private val changePasswordRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case PUT -> Root / "users" / "password" =>
      Ok("TODO")
  }

  // POST /auth/logout { Authorization: Bearer {jwt} } => 200 OK
  private val logoutRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case POST -> Root / "logout" =>
      Ok("TODO")
  }

  val routes: HttpRoutes[F] = Router(
    "/auth" -> (loginRoute <+>  createUserRoute <+> changePasswordRoute <+> logoutRoute )
  )
}


object AuthRoutes {
  def apply[F[_]: Concurrent: Logger](auth: Auth[F]) =
    new AuthRoutes[F](auth)
}