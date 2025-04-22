package com.rockthejvm.jobsboard.domain

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.rockthejvm.jobsboard.config.PaginationConfig
import com.rockthejvm.jobsboard.config.syntax.*
import pureconfig.ConfigSource

object pagination {
  final case class Pagination(limit: Int, offset: Int)

  object Pagination {
    val defaultPageSize = 20

    def apply(maybeLimit: Option[Int], maybeOffset: Option[Int]): Pagination =
      new Pagination(maybeLimit.getOrElse(defaultPageSize), maybeOffset.getOrElse(0))

    def default: Pagination =
      new Pagination(limit = 20, offset = 0)
  }

}
