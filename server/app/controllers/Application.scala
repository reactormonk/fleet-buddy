package controllers

import com.mohiva.play.silhouette.api.LoginEvent
import com.mohiva.play.silhouette.api.actions._
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticatorService
import com.mohiva.play.silhouette.impl.providers.CommonSocialProfile
import javax.inject.Inject
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import com.mohiva.play.silhouette.impl.providers.oauth2.state.{ CookieStateProvider, CookieStateSettings, DummyStateProvider }
import com.mohiva.play.silhouette.persistence.memory.daos._
import com.mohiva.play.silhouette.impl.providers.oauth2._
import com.mohiva.play.silhouette.api.{ Environment, EventBus, Silhouette, SilhouetteProvider }
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.providers.oauth2._
import play.api._
import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient
import play.api.mvc._
import com.mohiva.play.silhouette.impl.util._
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.api.{ Identity, LoginInfo }
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.util._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import play.api.Configuration
import play.api.libs.concurrent.Execution.Implicits._
import com.mohiva.play.silhouette.api.Env
import models.User
import play.api.i18n.MessagesApi
import com.mohiva.play.silhouette.api.services.IdentityService
import scala.concurrent.Future
import scalaz._, Scalaz._

import eveapi._

trait DefaultEnv extends Env {
  type I = User
  type A = SessionAuthenticator
}

object UserService extends IdentityService[User] {
  var store = Map[String, User]()

  def save(user: User): Future[User] = {
    store = store + (user.id -> user)
    Future.successful(user)
  }

  def save(profile: EveOnlineSocialInfo): Future[User] = {
    val user = User(profile.characterId, profile.characterName, profile.loginInfo)
    store = store + (profile.characterId -> user)
    Future.successful(user)
  }

  def retrieve(loginInfo: LoginInfo): Future[Option[User]] = {
    Future.successful(store.get(loginInfo.providerKey))
  }
}

class Application @Inject()(configuration: Configuration, messagesApi: MessagesApi, client: WSClient) extends Controller {
  // This is probably NOT how you should use guice.
  val fingerprintGenerator: FingerprintGenerator = new DefaultFingerprintGenerator(false)
  val idGenerator: IDGenerator = new SecureRandomIDGenerator()
  val clock: Clock = Clock()
  val userService: IdentityService[User] = UserService
  val config = configuration.underlying.as[SessionAuthenticatorSettings]("silhouette.authenticator")
  val authenticatorService: AuthenticatorService[SessionAuthenticator] =
    new SessionAuthenticatorService(config, fingerprintGenerator, clock)
  val eventBus: EventBus = EventBus()
  val env: Environment[DefaultEnv] = Environment[DefaultEnv](
    userService,
    authenticatorService,
    Seq(),
    eventBus
  )
  val secErrorHandler = new DefaultSecuredErrorHandler(messagesApi)
  val secRequestHandler: SecuredRequestHandler = new DefaultSecuredRequestHandler(secErrorHandler)
  val securedAction: SecuredAction = new DefaultSecuredAction(secRequestHandler)
  val unsecErrorHandler = new DefaultUnsecuredErrorHandler(messagesApi)
  val unsecRequestHandler: UnsecuredRequestHandler = new DefaultUnsecuredRequestHandler(unsecErrorHandler)
  val unsecuredAction: UnsecuredAction = new DefaultUnsecuredAction(unsecRequestHandler)
  val uaRequestHandler: UserAwareRequestHandler = new DefaultUserAwareRequestHandler()
  val userAwareAction: UserAwareAction = new DefaultUserAwareAction(uaRequestHandler)
  val silhouette = new SilhouetteProvider[DefaultEnv](env, securedAction, unsecuredAction, userAwareAction)

  val dao = new OAuth2InfoDAO()
  val oAuth2StateProvider: OAuth2StateProvider = {
    val settings = configuration.underlying.as[CookieStateSettings]("silhouette.oauth2StateProvider")
    new CookieStateProvider(settings, idGenerator, clock)
  }

  val oAuth2Provider = new EveOnlineProvider(
    new PlayHTTPLayer(client),
    oAuth2StateProvider,
    configuration.underlying.as[OAuth2Settings]("silhouette.eveonline")) {
  }

  def index = silhouette.SecuredAction.async { implicit request =>
    Future.successful(Ok(s"Sup ${request.identity}"))
  }

  def login = Action.async { implicit request =>
    oAuth2Provider.authenticate().flatMap {
      case Left(raw) => Future.successful(raw) // Failure
      case Right(info) => {
        for {
          profile <- oAuth2Provider.retrieveProfile(info)
          user <- UserService.save(profile)
          authInfo <- dao.save(profile.loginInfo, info)
          authenticator <- silhouette.env.authenticatorService.create(profile.loginInfo)
          value <- silhouette.env.authenticatorService.init(authenticator)
          result <- silhouette.env.authenticatorService.embed(value, Redirect(routes.Application.index()))
        } yield {
          silhouette.env.eventBus.publish(LoginEvent(user, request))
          result
        }
      }
    }
  }
}
