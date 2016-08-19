package eve

import eveapi.utils.Decoders._
import eveapi.errors.{EveApiError, EveApiStatusFailed}

import java.time.Clock
import scalaz.stream.async.mutable.Topic
import scala.concurrent.duration.Duration
import scala.collection.concurrent.TrieMap
import scalaz.stream.{Exchange, Process, Sink, async}
import scalaz.concurrent.Task
import scalaz._
import java.util.concurrent.ScheduledExecutorService

import org.http4s.{ Response, Uri }
import org.http4s.Uri.Authority
import org.http4s.util.CaseInsensitiveString

import models._
import oauth._
import shared._
import eveapi._
import eveapi.oauth._

case class EveServer(server: Uri.RegName)

case class TopicHolder(pollInterval: Duration, oauth: OAuth2, server: EveServer)(implicit s: ScheduledExecutorService) {
  def fleetUri(id: Long, server: EveServer) = Uri(scheme = Some(CaseInsensitiveString("https")), authority = Some(Authority(host=server.server)), path = s"/fleets/$id/")

  private val topics = TrieMap[Long, Topic[EveApiError \/ FleetState]]()

  def apply(user: User, fleetId: Long): Topic[EveApiError \/ FleetState] = {
    topics
      .retain({ case (id, topic) =>
        ! topic.subscribe.isHalt
      })
      .getOrElseUpdate(fleetId, {
        async.topic(
          ApiStream.fleetPollSource(fleetUri(fleetId, server), pollInterval, Execute.OAuthInterpreter)
            .translate[Task](ApiStream.fromApiStream(oauth, user.token))
            , true)
      }
    )
  }
}
