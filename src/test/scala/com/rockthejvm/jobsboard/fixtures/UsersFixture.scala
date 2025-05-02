package com.rockthejvm.jobsboard.fixtures

import com.rockthejvm.jobsboard.domain.user.Role.{ADMIN, RECRUITER}
import com.rockthejvm.jobsboard.domain.user.User

trait UsersFixture {
  val ben = User(
    "ben@rockthejvm.com",
    "rockthejvm",
    Some("Ben"),
    Some("Mcallister"),
    Some("Rock the JVM"),
    ADMIN
  )

  val heath = User(
    "heath@rockthejvm.com",
    "heathisthebest",
    Some("Heath"),
    Some("Emmott-Mcallister"),
    Some("Rock the JVM"),
    RECRUITER
  )

  val newUser = User(
    "dawn@rockthejvm.com",
    "dawnisthebest",
    Some("Dawn"),
    Some("Emmott-Mcallister"),
    Some("Rock the JVM"),
    RECRUITER
  )

  val updatedHeath = User(
    "heath@rockthejvm.com",
    "heathisevenbetter",
    Some("HEATH"),
    Some("EMMOTT-MCALLISTER"),
    Some("Adobe"),
    RECRUITER
  )
}
