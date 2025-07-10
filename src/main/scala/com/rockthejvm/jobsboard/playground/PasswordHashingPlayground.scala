package com.rockthejvm.jobsboard.playground

import cats.effect.{IO, IOApp}
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

object PasswordHashingPlayground extends IOApp.Simple {
  override def run: IO[Unit] =
    BCrypt.hashpw[IO]("rockthejvmNewPassword").flatMap(IO.println) *>
      BCrypt.checkpwBool[IO](
        "rockthejvmNewPassword",
        PasswordHash[BCrypt]("$2a$10$ODqkGkJoCoPDIXz0HQ7E.eOEuU/PfhZCYOM.MhQzi.9UVkQkE.4z6")
      ).flatMap(IO.println)
}
