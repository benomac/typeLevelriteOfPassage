package com.rockthejvm.jobsboard.http.routes

import cats.Monad
import org.http4s.*
import org.http4s.server.*
import org.http4s.dsl.*
import org.http4s.dsl.impl.*

class HealthRoutes[F[_]: Monad] private extends Http4sDsl[F] {
  private val healthRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root =>
      Ok("all going great!")
    }

  val routes = Router(
    "/health" -> healthRoutes
  )
}

object HealthRoutes {
  def apply[F[_] : Monad] = new HealthRoutes[F]
}