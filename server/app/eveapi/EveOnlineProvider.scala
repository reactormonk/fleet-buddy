package eveapi

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers._
import play.api.libs.json.JsValue

import scala.concurrent.Future

trait BaseEveOnlineProvider extends OAuth2Provider {

  override type Content = JsValue

  override val id = "eve"

  override protected val urls = Map("api" -> "https://login.eveonline.com/oauth/verify")

  override protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    httpLayer.url(urls("api")).withHeaders("Authorization" -> s"Bearer ${authInfo.accessToken}").get().flatMap { response =>
      val json = response.json
      (json \ "error").asOpt[String] match {
        case Some(errorMsg) =>
          throw new ProfileRetrievalException(EveOnlineProvider.SpecifiedProfileError.format(id, errorMsg))
        case _ => profileParser.parse(json, authInfo)
      }
    }
  }
}

case class EveOnlineSocialInfo(characterId: String, characterName: String, loginInfo: LoginInfo) extends SocialProfile {
  val avatarURL = s"https://image.eveonline.com/Character/${characterId}_512.jpg"
}

class EveOnlineProfileParser extends SocialProfileParser[JsValue, EveOnlineSocialInfo, OAuth2Info] {

    override def parse(json: JsValue, authInfo: OAuth2Info): Future[EveOnlineSocialInfo] = Future.successful {
      val id = (json \ "CharacterID").as[Long].toString
      val name = (json \ "CharacterName").as[String]

      EveOnlineSocialInfo(
        characterId = id,
        characterName = name,
        loginInfo = LoginInfo("eve", id)
      )
    }
}

class EveOnlineProvider(
  protected val httpLayer: HTTPLayer,
  protected val stateProvider: OAuth2StateProvider,
  val settings: OAuth2Settings)
  extends BaseEveOnlineProvider with SocialProfileBuilder {

  type Profile = EveOnlineSocialInfo

  /**
   * The type of this class.
   */
  override type Self = EveOnlineProvider

  /**
   * The profile parser implementation.
   */
  override val profileParser = new EveOnlineProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  override def withSettings(f: (Settings) => Settings) = new EveOnlineProvider(httpLayer, stateProvider, f(settings))
}

object EveOnlineProvider {

  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error message: %s"

}
