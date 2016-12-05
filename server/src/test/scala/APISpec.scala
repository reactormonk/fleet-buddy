import org.specs2._

import org.http4s._, org.http4s.dsl._, org.http4s.server._, org.http4s.client._

object APISpec extends Specification {
  def is = s2"""
The sample endpoint
  should give you a 200 $sampleEndpoint
"""
  val app = Client.fromHttpService(God.appClient.service)

  def sampleEndpoint = {
  }
}
