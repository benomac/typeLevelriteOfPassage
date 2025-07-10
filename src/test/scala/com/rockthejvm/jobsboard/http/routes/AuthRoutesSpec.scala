package com.rockthejvm.jobsboard.http.routes

import cats.effect.*
import cats.implicits.*
import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.freespec.AsyncFreeSpec
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits.*
import org.scalatest.matchers.should.Matchers
import com.rockthejvm.jobsboard.core.*
import com.rockthejvm.jobsboard.fixtures.*
import org.http4s.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger



class AuthRoutesSpec extends AsyncFreeSpec
  with AsyncIOSpec
  with Matchers
  with Http4sDsl[IO]
  with UsersFixture {

}
