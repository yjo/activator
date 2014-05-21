//package console.handler.rest
//
//import activator.analytics.data._
//import activator.analytics.data.TimeRangeType
//import com.typesafe.trace.uuid.UUID
//import play.api.libs.json.Json
//import org.specs2.mutable.Specification
//import com.typesafe.trace._
//import play.api.libs.json._
//import ActorJsonBuilder.ActorResult
//import akka.actor.ActorRef
//import scala.util.Random
//import org.specs2.matcher.MatchResult
//import scala.concurrent.duration._
//import akka.actor._
//import scala.reflect.ClassTag
//
//object ActorJsonSpec {
//  import console.handler.Generators._
//
//  val now = System.currentTimeMillis
//
//  // we don't go too nuts with the generation here because the below already takes around half a second,
//  // so it will rapidly grow
//
//  val minuteTimeRanges = Seq(TimeRange(now - 30.minutes.toMillis, now, TimeRangeType.Minutes))
//  val hourTimeRanges = Seq(TimeRange(now - 30.hours.toMillis, now, TimeRangeType.Hours))
//  val dayTimeRanges = Seq(TimeRange(now - 30.days.toMillis, now, TimeRangeType.Days))
//
//  val scopes = genActorScopes(Set(ActorPath.fromString("akka://user/a"), ActorPath.fromString("akka://user/b")),
//    Set(),
//    Set("host1", "host2"),
//    Set("dispatcher1", "dispatcher2"),
//    Set("system1", "system2"))
//  val timeRanges = minuteTimeRanges ++ hourTimeRanges ++ dayTimeRanges
//  val stats = {
//    val deviationsGen = genDeviationDetails()
//    genActorStats(scopes, timeRanges) { (i, s, tr) =>
//      ActorStats(tr, s, ActorStatsMetrics(bytesRead = i, bytesWritten = i, deviationDetails = deviationsGen()))
//    }
//  }
//}
//
//class ActorJsonSpec extends Specification {
//  import ActorJsonSpec._
//
//  "ActorJson" should {
//    "generate JSON for an actor" in {
//      forall(stats) { (s: ActorStats) =>
//        val r = ActorJsonBuilder.createActorJson(s)
//
//        // do timeRange manually since we go recursive
//        val timeRangeOpt = (r \ "timerange").asOpt[JsObject]
//        timeRangeOpt must beSome
//        timeRangeOpt foreach { tr =>
//          (tr \ "startTime").asOpt[JsNumber] must beSome
//          (tr \ "endTime").asOpt[JsNumber] must beSome
//          val rangeTypeOpt = (tr \ "rangeType").asOpt[String]
//          rangeTypeOpt must beSome
//        }
//
//        def hasField[T](field: String)(implicit tag: ClassTag[T], reads: Reads[T]) = {
//          (r \ field).asOpt[T] must beSome
//        }
//
//        hasField[JsNumber]("askMessagesCount")
//        hasField[JsNumber]("askRate")
//        hasField[JsNumber]("createdCount")
//        hasField[JsNumber]("deadletterCount")
//        hasField[JsArray]("deadletters")
//        hasField[JsNumber]("deadlockCount")
//        hasField[JsArray]("deadlocks")
//        hasField[JsNumber]("deviationCount")
//        hasField[JsNumber]("errorCount")
//        hasField[JsArray]("errors")
//        hasField[JsNumber]("failedCount")
//        hasField[JsNumber]("latestMessageTimestamp")
//        hasField[JsNumber]("latestTraceEventTimestamp")
//        hasField[JsNumber]("maxMailboxSize")
//        hasField[String]("maxMailboxSizeAddressNode")
//        hasField[String]("maxMailboxSizeAddressPath")
//        hasField[JsNumber]("maxMailboxSizeTimestamp")
//        hasField[JsObject]("maxTimeInMailbox")
//        hasField[String]("maxTimeInMailboxAddressNode")
//        hasField[String]("maxTimeInMailboxAddressPath")
//        hasField[JsNumber]("maxTimeInMailboxTimestamp")
//        hasField[JsNumber]("meanBytesReadRate")
//        hasField[String]("meanBytesReadRateUnit")
//        hasField[JsNumber]("meanBytesWrittenRate")
//        hasField[String]("meanBytesWrittenRateUnit")
//        hasField[JsNumber]("meanMailboxSize")
//        hasField[JsNumber]("meanProcessedMessageRate")
//        hasField[String]("meanProcessedMessageRateUnit")
//        hasField[JsObject]("meanTimeInMailbox")
//        hasField[JsNumber]("processedMessagesCount")
//        hasField[String]("rateUnit")
//        hasField[JsNumber]("receiveRate")
//        hasField[JsNumber]("restartCount")
//        hasField[JsObject]("scope")
//        hasField[JsNumber]("stoppedCount")
//        hasField[JsNumber]("tellMessagesCount")
//        hasField[JsNumber]("tellRate")
//        hasField[JsObject]("timerange")
//        hasField[JsNumber]("totalMessageRate")
//        hasField[JsNumber]("unhandledMessageCount")
//        hasField[JsArray]("unhandledMessages")
//        hasField[JsNumber]("warningCount")
//        hasField[JsArray]("warnings")
//
//        r.fieldSet.size mustEqual (43)
//      }
//    }
//  }
//}
