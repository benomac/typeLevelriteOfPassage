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
import com.rockthejvm.jobsboard.modules.*
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Application extends IOApp.Simple {

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] = ConfigSource.default.loadF[IO, AppConfig].flatMap {
    case AppConfig(postgresConfig, emberConfig) =>
    val appResource = for {
      xa <- Database.makePostgresResource[IO](postgresConfig)
      core    <- Core[IO](xa)
      httpApi <- HttpApi[IO](core)
      server <- EmberServerBuilder
        .default[IO]
        .withHost(emberConfig.host)
        .withPort(emberConfig.port)
        .withHttpApp(httpApi.endpoints.orNotFound)
        .build
    } yield server
    appResource.use(_ => IO.println("Server rock the jvm ready!") *> IO.never)
  }

}
