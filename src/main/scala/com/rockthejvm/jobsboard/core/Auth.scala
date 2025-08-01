package com.rockthejvm.jobsboard.core

import cats.data.OptionT
import cats.effect.*
import cats.implicits.*
import org.typelevel.log4cats.Logger
import tsec.authentication.{AugmentedJWT, BackingStore, IdentityStore, JWTAuthenticator}
import com.rockthejvm.jobsboard.domain.security.*
import com.rockthejvm.jobsboard.domain.auth.*
import com.rockthejvm.jobsboard.domain.user.*
import com.rockthejvm.jobsboard.config.SecurityConfig

import tsec.common.SecureRandomId
import tsec.mac.jca.HMACSHA256
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

import scala.concurrent.duration.DurationInt

trait Auth[F[_]] {
  def login(email: String, password: String): F[Option[JWTToken]]

  def signUp(newUserInfo: NewUserInfo): F[Option[User]]

  def changePassword(
      email: String,
      newPasswordInfo: NewPasswordInfo
  ): F[Either[String, Option[User]]]
  // TODO password recovery via email

  def delete(email: String): F[Boolean]

  def authenticator: Authenticator[F]
}

class LiveAuth[F[_]: Async: Logger] private (
    users: Users[F],
    override val authenticator: Authenticator[F]
) extends Auth[F] {
  override def login(email: String, password: String): F[Option[JWTToken]] =
    for {
      maybeUser: Option[User] <- users.find(email)
      maybeValidatedUser: Option[User] <- maybeUser.filterA(user =>
        BCrypt.checkpwBool[F](
          password,
          PasswordHash[BCrypt](user.hashedPassword)
        )
      )
      maybeJWTToken <- maybeValidatedUser.traverse(user => authenticator.create(user.email))

    } yield maybeJWTToken

  override def signUp(newUserInfo: NewUserInfo): F[Option[User]] =
    users.find(newUserInfo.email).flatMap {
      case Some(_) => None.pure[F]
      case None =>
        for {
          hashedPW <- BCrypt.hashpw[F](newUserInfo.password)
          user <- User(
            newUserInfo.email,
            hashedPW,
            newUserInfo.firstName,
            newUserInfo.lastName,
            newUserInfo.company,
            Role.RECRUITER
          ).pure[F]
          _ <- users.create(user)
        } yield Some(user)
    }

  override def changePassword(
      email: String,
      newPasswordInfo: NewPasswordInfo
  ): F[Either[String, Option[User]]] = {
    def updateUser(user: User, newPassword: String): F[Option[User]] =
      for {
        hashedPW: PasswordHash[BCrypt] <- BCrypt.hashpw[F](newPasswordInfo.newPassword)
        updatedUser                    <- users.update(user.copy(hashedPassword = hashedPW))
      } yield updatedUser

    def checkandUpdate(
        user: User,
        oldPassword: String,
        newPassword: String
    ): F[Either[String, Option[User]]] =
      for {
        passCheck <- BCrypt.checkpwBool[F](
          newPasswordInfo.oldPassword,
          PasswordHash[BCrypt](user.hashedPassword)
        )
        updateResult: Either[String, Option[User]] <-
          if (passCheck) updateUser(user, newPasswordInfo.newPassword).map(Right(_))
          else Left("Invalid password").pure[F]
      } yield updateResult

    users.find(email).flatMap {
      case None => Right(None).pure[F]
      case Some(user) =>
        val NewPasswordInfo(oldPassword, newPassword) = newPasswordInfo
        checkandUpdate(user, oldPassword, newPassword)
    }
  }

  override def delete(email: String): F[Boolean] =
    users.delete(email)
}

object LiveAuth {
  def apply[F[_]: Async: Logger](
      users: Users[F]
  )(securityConfig: SecurityConfig): F[LiveAuth[F]] = {

    // 1. Identity store: String => OptionT[F, User]
    val idStore: IdentityStore[F, String, User] = (email: String) =>
      OptionT(users.find(email))

      // 2. backing store for JWT: BackingStore[F, id, JWTToken]
    val tokenStoreF = Ref.of[F, Map[SecureRandomId, JWTToken]](Map.empty).map { ref =>
      new BackingStore[F, SecureRandomId, JWTToken] {

        override def get(id: SecureRandomId): OptionT[F, JWTToken] =
          OptionT(ref.get.map((x: Map[SecureRandomId, JWTToken]) => x.get(id)))
        override def put(elem: JWTToken): F[JWTToken] =
          ref.modify(store => (store + (elem.id -> elem), elem))
        override def update(v: JWTToken): F[JWTToken] =
          put(v)
        override def delete(id: SecureRandomId): F[Unit] =
          ref.modify(store => (store - id, ()))

      }
    }

    // TODO

    // 3. hashing key
    val keyF = HMACSHA256.buildKey[F](securityConfig.secret.getBytes("UTF-8")) // TODO move secret to config

    // 4. authenticator
    for {
      key <- keyF
      tokenStore <- tokenStoreF
      authenticator = JWTAuthenticator.backed.inBearerToken(
        expiryDuration = securityConfig.jwtExpiryDuration, // expiry of tokens
        maxIdle = None, // max idle time
        identityStore = idStore,
        tokenStore = tokenStore, // identity store
        signingKey = key // hash key
      )
    } yield new LiveAuth[F](users, authenticator)

  }
}
