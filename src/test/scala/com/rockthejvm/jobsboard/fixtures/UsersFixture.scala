package com.rockthejvm.jobsboard.fixtures

import com.rockthejvm.jobsboard.domain.user.Role.{ADMIN, RECRUITER}
import com.rockthejvm.jobsboard.domain.user.User

trait UsersFixture {
  val ben = User(
    email = "ben@rockthejvm.com",
    hashedPassword = "$2a$10$hB2jCMCtBoxthyzV.Q4xAuKdkAJ7Z9ptPhugKCBM8fF3GnkI26eVq", // rockthejvm
    firstName = Some("Ben"),
    lastName = Some("Mcallister"),
    company = Some("Rock the JVM"),
    role = ADMIN
  )

  val benEmail: String = ben.email

  val heath = User(
    email = "heath@rockthejvm.com",
    hashedPassword = "$2a$10$hKjCeqk3DIWZ1odm/.OiF.dGDoAtVSAPQzN7NmjogCkZ8IjTe7YwO", // heathisthebest
    firstName = Some("Heath"),
    lastName = Some("Emmott-Mcallister"),
    company = Some("Rock the JVM"),
    role = RECRUITER
  )

  val heathEmail = heath.email

  val newUser = User(
    "dawn@rockthejvm.com",
    hashedPassword = "$2a$10$2Bv3xu92rH/AR8px3FMDTOK.Dgg8Rbjecmc4yc1bjKQHFa5.x0deS", // dawnisthebest
    firstName = Some("Dawn"),
    lastName = Some("Emmott-Mcallister"),
    company = Some("Rock the JVM"),
    role = RECRUITER
  )

  val updatedHeath = User(
    "heath@rockthejvm.com",
    hashedPassword = "$2a$10$kG0haxkBaYIhn/QoPA.2oe93qpKJR6gw6.UimIcbpMMLMHNA.vETe", // heathisevenbetter
    firstName = Some("HEATH"),
    lastName = Some("EMMOTT-MCALLISTER"),
    company = Some("Adobe"),
    role = RECRUITER
  )
}
