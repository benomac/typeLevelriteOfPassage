package com.rockthejvm.jobsboard.config

import cats.MonadThrow
import pureconfig.ConfigSource
import pureconfig.ConfigReader
import pureconfig.error.ConfigReaderException
import cats.implicits.*
import scala.reflect.ClassTag

object syntax {
  extension (source: ConfigSource)
    def loadF[F[_], A](using reader: ConfigReader[A], F: MonadThrow[F], tag: ClassTag[A]): F[A] =
      F.pure(source.load[A]).flatMap {
        case Left(errors) => F.raiseError[A](ConfigReaderException(errors))
        case Right(value) => F.pure(value)
      }
}
