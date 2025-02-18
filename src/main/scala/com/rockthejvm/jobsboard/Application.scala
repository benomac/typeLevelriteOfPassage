package com.rockthejvm.jobsboard

import cats.*
import cats.implicits.*
import cats.effect.IOApp
import cats.effect.IO
import cats.effect.*
import org.typelevel.log4cats.Logger
import org.http4s.*
import org.http4s.ember.server.EmberServerBuilder
import pureconfig.ConfigSource
import com.rockthejvm.jobsboard.config.*
import com.rockthejvm.jobsboard.config.syntax.*
import com.rockthejvm.jobsboard.modules.HttpApi
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Application extends IOApp.Simple {

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] = ConfigSource.default.loadF[IO, EmberConfig].flatMap { config =>
    EmberServerBuilder
      .default[IO]
      .withHost(config.host)
      .withPort(config.port)
      .withHttpApp(HttpApi[IO].endpoints.orNotFound)
      .build
      .use(_ => IO.println("Server rock the jvm ready!") *> IO.never)
  }

}
