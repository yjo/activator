package snap

import sbt.protocol._
import play.api.libs.json._

object Sbt {
  def wrapEvent[T <: Event: Format](event: T): JsObject = {
    JsObject(Seq("type" -> JsString("sbt"),
      "event" -> Json.toJson(event)))
  }

  def synthesizeLogEvent(level: String, message: String): JsObject = {
    wrapEvent(LogEvent(0L, LogMessage(level, message)))
  }
}
