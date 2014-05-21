//package console.handler
//
//import activator.analytics.data._
//import akka.actor.ActorRef
//import activator.analytics.data.TimeRangeType
//import scala.concurrent.duration._
//import console.{ PagingInformation, ScopeModifiers }
//import java.util.concurrent.TimeUnit
//import activator.analytics.repository.ActorStatsSorted
//import activator.analytics.rest.http.SortingHelpers.{ Ascending, SortDirection }
//import console.AnalyticsRepository
//
//object ActorsHandlerSpec {
//  def actorsHandler(repo: AnalyticsRepository)(body: (ActorRef, ActorStatsSorted) => Unit): ActorsHandlerBase = new ActorsHandlerBase {
//    final val defaultLimit: Int = 100
//    val repository: AnalyticsRepository = repo
//
//    def useActorStats(sender: ActorRef, stats: ActorStatsSorted): Unit = body(sender, stats)
//  }
//}
//
//class ActorsHandlerSpec extends ActorsSpec("ActorsHandlerSpec") with ActorHandlerSpecification {
//  isolated
//  import ActorHandlerSpec._
//  import ActorsHandlerSpec._
//
//  "Actors Handler" should {
//    "Find data" in {
//      var resultSender: ActorRef = null
//      var resultStats: ActorStatsSorted = null
//
//      val h = actorsHandler(repository) { (sender, stats) =>
//        resultSender = sender
//        resultStats = stats
//      }
//
//      forall(stats) { (as: ActorStats) =>
//        h.onModuleInformation(ActorRef.noSender, ActorsHandler.ActorsModuleInfo(as.scope,
//          modifiers = ScopeModifiers(),
//          time = as.timeRange,
//          pagingInformation = None,
//          sortOn = ActorStatsSorts.ActorName,
//          sortDirection = Ascending,
//          dataFrom = None,
//          traceId = None))
//
//        resultStats.total must equalTo(3) // Note: Should derive this number instead of hard-coding it.
//        resultSender must equalTo(ActorRef.noSender)
//        forall(resultStats.stats) { (as1: ActorStats) =>
//          as1.timeRange must equalTo(as.timeRange)
//        }
//      }
//    }
//    "Not find data outside of available range" in { // Note: this isn't actually correct, but hey.  No data isn't the same as `zero` data
//      var resultSender: ActorRef = null
//      var resultStats: ActorStatsSorted = null
//
//      val h = actorsHandler(repository) { (sender, stats) =>
//        resultSender = sender
//        resultStats = stats
//      }
//
//      val oneMinute: Int = Duration(1, TimeUnit.MINUTES).toMillis.toInt
//      val maxMinutes = stats.filter(x => x.timeRange.rangeType == TimeRangeType.Minutes).maxBy(_.timeRange.startTime)
//      val outside = maxMinutes.copy(timeRange = TimeRange.rangeFor(maxMinutes.timeRange.startTime + oneMinute, TimeRangeType.Minutes))
//      val emptyStats = ActorStats(outside.timeRange, outside.scope)
//
//      h.onModuleInformation(ActorRef.noSender, ActorsHandler.ActorsModuleInfo(outside.scope,
//        modifiers = ScopeModifiers(),
//        time = outside.timeRange,
//        pagingInformation = None,
//        sortOn = ActorStatsSorts.ActorName,
//        sortDirection = Ascending,
//        dataFrom = None,
//        traceId = None))
//
//      resultStats.total must equalTo(0)
//      resultSender must equalTo(ActorRef.noSender)
//      resultStats.stats must beEmpty
//    }
//  }
//}
