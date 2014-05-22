package console.handler

import org.specs2.mutable._
import activator.analytics.data._
import akka.actor.{ ActorRef, ActorPath }
import com.typesafe.trace.uuid.UUID
import activator.analytics.data.TimeRangeType.TimeRangeType
import activator.analytics.data.TimeRangeType
import scala.concurrent.duration._
import scala.Some
import activator.analytics.data.ActorStatsMetrics
import console.ScopeModifiers
import java.util.concurrent.TimeUnit
import console.AnalyticsRepository

object ActorHandlerSpec {
  import Generators._

  val minuteTimeRanges = genTimeRanges(0, 30.minutes.toMillis.toInt, 1.minute.toMillis.toInt, TimeRangeType.Minutes)
  val hourTimeRanges = genTimeRanges(0, 30.hours.toMillis.toInt, 1.hour.toMillis.toInt, TimeRangeType.Hours)
  val dayTimeRanges = genTimeRanges(0, 30.days.toMillis.toInt, 1.day.toMillis.toInt, TimeRangeType.Days)

  val scopes = genActorScopes(Set(ActorPath.fromString("akka://user/a"), ActorPath.fromString("akka://user/b"), ActorPath.fromString("akka://user/c")),
    Set(),
    Set("host1", "host2", "host3"),
    Set("dispatcher1", "dispatcher2", "dispatcher3"),
    Set("system1", "system2", "system3"))
  val timeRanges = minuteTimeRanges ++ hourTimeRanges ++ dayTimeRanges
  val stats = genActorStats(scopes, timeRanges) { (i, s, tr) =>
    ActorStats(tr, s, ActorStatsMetrics(bytesRead = i, bytesWritten = i))
  }

  lazy val repository: AnalyticsRepository = {
    val r = AnalyticsRepository.freshMemoryObjects
    val asr = r.actorStatsRepository
    asr.save(stats)
    r
  }

  def actorHandler(repo: AnalyticsRepository)(body: (ActorRef, ActorStats) => Unit): ActorHandlerBase = new ActorHandlerBase {
    val repository: AnalyticsRepository = repo

    def useActorStats(sender: ActorRef, stats: ActorStats): Unit = body(sender, stats)
  }
}

trait ActorHandlerSpecification { this: SpecificationLike =>
  def beEqualActorStats = (be_==(_: ActorStats)) ^^^ ((_: ActorStats).copy(timeRange = TimeRange(), id = UUID.nilUUID()))
}

class ActorHandlerSpec extends ActorsSpec("ActorHandlerSpec") with ActorHandlerSpecification {
  isolated
  import ActorHandlerSpec._

  "Actor Handler" should {
    "Find data" in {
      var resultSender: ActorRef = null
      var resultStats: ActorStats = null

      val h = actorHandler(repository) { (sender, stats) =>
        resultSender = sender
        resultStats = stats
      }

      forall(stats) { (as: ActorStats) =>
        h.onModuleInformation(ActorRef.noSender, ActorHandler.ActorModuleInfo(as.scope,
          modifiers = ScopeModifiers(),
          time = as.timeRange,
          dataFrom = None,
          traceId = None))

        resultSender must equalTo(ActorRef.noSender)
        resultStats must beEqualActorStats(as)
      }
    }
    "Not find data outside of available range" in { // Note: this isn't actually correct, but hey.  No data isn't the same as `zero` data
      var resultSender: ActorRef = null
      var resultStats: ActorStats = null

      val h = actorHandler(repository) { (sender, stats) =>
        resultSender = sender
        resultStats = stats
      }

      val oneMinute: Int = Duration(1, TimeUnit.MINUTES).toMillis.toInt
      val maxMinutes = stats.filter(x => x.timeRange.rangeType == TimeRangeType.Minutes).maxBy(_.timeRange.startTime)
      val outside = maxMinutes.copy(timeRange = TimeRange.rangeFor(maxMinutes.timeRange.startTime + oneMinute, TimeRangeType.Minutes))
      val emptyStats = ActorStats(outside.timeRange, outside.scope)

      h.onModuleInformation(ActorRef.noSender, ActorHandler.ActorModuleInfo(outside.scope,
        modifiers = ScopeModifiers(),
        time = outside.timeRange,
        dataFrom = None,
        traceId = None))

      resultSender must equalTo(ActorRef.noSender)
      resultStats must beEqualActorStats(emptyStats)
    }
  }
}
