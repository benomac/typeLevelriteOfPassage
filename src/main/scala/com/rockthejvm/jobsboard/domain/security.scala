package com.rockthejvm.jobsboard.domain

import cats.{Applicative, Monad, MonadThrow, Semigroup}
import cats.implicits.*
import com.rockthejvm.jobsboard.domain.user.*
import org.http4s.{Response, Status}
import tsec.authentication.{AugmentedJWT, JWTAuthenticator, SecuredRequest, TSecAuthService}
import tsec.authorization.{AuthorizationInfo, BasicRBAC}
import tsec.mac.jca.HMACSHA256

object security {
  private type Crypto = HMACSHA256

  type JWTToken = AugmentedJWT[Crypto, String]

  type Authenticator[F[_]] = JWTAuthenticator[F, String, User, Crypto]

  type AuthRoute[F[_]] = PartialFunction[SecuredRequest[F, User, JWTToken], F[Response[F]]]

  type AuthRBAC[F[_]] = BasicRBAC[F, Role, User, JWTToken]

  // RBAC - Role Based Access Control
  // BasicRBAC[F, Role, User, JWTToken

  given authRole[F[_]: Applicative]: AuthorizationInfo[F, Role, User] with {
    override def fetchInfo(u: User): F[Role] = u.role.pure[F]
  }

  def allRoles[F[_]: MonadThrow]: AuthRBAC[F] =
    BasicRBAC.all[F, Role, User, JWTToken]

  def recruiterOnly[F[_]: MonadThrow]: AuthRBAC[F] =
    BasicRBAC(Role.RECRUITER)

  def adminOnly[F[_]: MonadThrow]: AuthRBAC[F] =
    BasicRBAC(Role.ADMIN)

  // authorization
  case class Authorizations[F[_]](rbacRoutes: Map[AuthRBAC[F], List[AuthRoute[F]]])

  object Authorizations {
    given combiner[F[_]]: Semigroup[Authorizations[F]] = Semigroup.instance {
      (authA, authB) =>  Authorizations(authA.rbacRoutes |+| authB.rbacRoutes)
    }
  }

  // AuthRoute -> Authorizations -> TSecAuthService -> HttpRoute

  // 1.AuthRoute -> Authorizations = .restrictedTo extension method
  extension [F[_]](authRoute: AuthRoute[F])
    def restrictedTo(rbac: AuthRBAC[F]): Authorizations[F] =
      Authorizations(Map(rbac -> List(authRoute)))

  // 2. Authorizations -> TSecAuthService = implicit conversion

  given auth2Tsec[F[_]: Monad]: Conversion[Authorizations[F], TSecAuthService[User, JWTToken, F]] =
    authz => {
      // this responds with 401 always
      val unauthorizedService: TSecAuthService[User, JWTToken, F] =
        TSecAuthService[User, JWTToken, F] { _ =>
          Response[F](Status.Unauthorized).pure[F]
        }

      authz.rbacRoutes // Map[RBAC, List[Routes]]
        .toSeq
        .foldLeft(unauthorizedService) { case (acc, (rbac, routes)) =>
          // merge into one
          val bigRoute = routes.reduce(_.orElse(_))
          TSecAuthService.withAuthorizationHandler(rbac)(bigRoute, acc.run)
        }
    }

}
