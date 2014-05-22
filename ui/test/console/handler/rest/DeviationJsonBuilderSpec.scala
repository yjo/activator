package console.handler.rest

import com.typesafe.trace.uuid.UUID
import play.api.libs.json.Json
import org.specs2.mutable.Specification
import com.typesafe.trace._
import play.api.libs.json._
import DeviationJsonBuilder.{ InvalidResult, ValidResult, DeviationResult }
import akka.actor.ActorRef
import scala.util.Random
import org.specs2.matcher.MatchResult

object DeviationJsonSpec {
  import console.handler.Generators._

  val numberOfTraces = 200
  val traceId = new UUID()
  val traces = genTraceEvents(randomFlatten(genNMessageTraceAnnotations(numberOfTraces)), traceId)
}

class DeviationJsonSpec extends Specification {
  import DeviationJsonSpec._

  def validEventStructure(in: JsValue): MatchResult[Any] = {
    (in \ "id").asOpt[JsValue] must beSome
    (in \ "trace").asOpt[JsValue] must beSome
    (in \ "sampled").asOpt[JsValue] must beSome
    (in \ "node").asOpt[JsValue] must beSome
    (in \ "actorSystem").asOpt[JsValue] must beSome
    (in \ "host").asOpt[JsValue] must beSome
    (in \ "timestamp").asOpt[JsValue] must beSome
    (in \ "nanoTime").asOpt[JsValue] must beSome
    (in \ "annotation").asOpt[JsValue] must beSome
  }

  def validateChild(in: JsValue): MatchResult[Any] = {
    (in \ "event").asOpt[JsValue] must beSome
    validEventStructure(in \ "event")
    (in \ "children").asOpt[JsValue] must beSome
    validateChildren((in \ "traceTree" \ "children").as[JsArray].value)
  }

  def validateChildren(in: Seq[JsValue]): MatchResult[Any] =
    forall(in)(validateChild)

  "DeviationJson" should {
    "generate JSON for a valid result" in {
      val trace = traces(Random.nextInt(numberOfTraces))
      val event = trace.id
      val result = ValidResult(ActorRef.noSender, event, trace, traces)
      val r = DeviationJsonBuilder.createJson(result)
      (r \ "type").asOpt[String] must beSome("deviation")
      (r \ "data").asOpt[JsValue] must beSome
      val root = (r \ "data").as[JsValue]
      (root \ "traceEvent").asOpt[JsValue] must beSome
      validEventStructure(root \ "traceEvent")
      (root \ "traceTree").asOpt[JsValue] must beSome
      (root \ "traceTree" \ "event").asOpt[JsValue] must beSome
      validEventStructure(root \ "traceTree" \ "event")
      (root \ "traceTree" \ "children").asOpt[JsValue] must beSome
      validateChildren((root \ "traceTree" \ "children").as[JsArray].value)
    }
    "generate JSON for an invalid result" in {
      val trace = traces(Random.nextInt(numberOfTraces))
      val event = trace.id
      val result = InvalidResult(ActorRef.noSender, event)
      val r = DeviationJsonBuilder.createJson(result)
      (r \ "type").asOpt[String] must beSome("deviation")
      (r \ "data").asOpt[JsValue] must beSome(JsNull)
    }
  }
}
