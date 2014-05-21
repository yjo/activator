//package console.handler
//
//import activator.analytics.data._
//import activator.analytics.data.TimeRangeType.TimeRangeType
//import akka.actor.{ ActorRef, Props, ActorSystem, Actor, ActorPath }
//import com.typesafe.trace.uuid.UUID
//import com.typesafe.trace.{ TraceEvent, TraceEvents, Batch }
//import console.ClientController
//import console.handler.rest.DeviationsJsonBuilder
//import console.handler.rest.DeviationsJsonBuilder.DeviationsResult
//import org.specs2.mutable._
//import play.api.libs.json.Json
//import scala.concurrent.duration._
//import console.AnalyticsRepository
//
//object DeviationsHandlerSpec {
//  import Generators._
//
//  val minuteTimeRanges = genTimeRanges(0, 30.minutes.toMillis.toInt, 1.minute.toMillis.toInt, TimeRangeType.Minutes)
//  val hourTimeRanges = genTimeRanges(0, 30.hours.toMillis.toInt, 1.hour.toMillis.toInt, TimeRangeType.Hours)
//  val dayTimeRanges = genTimeRanges(0, 30.days.toMillis.toInt, 1.day.toMillis.toInt, TimeRangeType.Days)
//
//  val scopes = genActorScopes(Set(ActorPath.fromString("akka://user/a"), ActorPath.fromString("akka://user/b"), ActorPath.fromString("akka://user/c")),
//    Set(),
//    Set("host1", "host2", "host3"),
//    Set("dispatcher1", "dispatcher2", "dispatcher3"),
//    Set("system1", "system2", "system3"))
//  val timeRanges = minuteTimeRanges ++ hourTimeRanges ++ dayTimeRanges
//  val stats = {
//    val deviationsGen = genDeviationDetails()
//    genActorStats(scopes, timeRanges) { (i, s, tr) =>
//      ActorStats(tr, s, ActorStatsMetrics(bytesRead = i, bytesWritten = i, deviationDetails = deviationsGen()))
//    }
//  }
//
//  val errorStats = {
//    timeRanges.flatMap { tr =>
//      val errorStatsGen = genErrorStats(tr)
//      genMultiple(1, errorStatsGen) ++ genMaxMultiple(4, errorStatsGen)
//    }
//  }
//
//  lazy val repository: AnalyticsRepository = {
//    val r = AnalyticsRepository.freshMemoryObjects
//    val asr = r.actorStatsRepository
//    asr.save(stats)
//    val esr = r.errorStatsRepository
//    esr.save(errorStats)
//    r
//  }
//
//  def deviationsHandler(repo: AnalyticsRepository)(body: (ActorRef, Either[Seq[ErrorStats], Seq[ActorStats]]) => Unit): DeviationsHandlerBase = new DeviationsHandlerBase {
//    val repository: AnalyticsRepository = repo
//
//    def useDeviationsResult(sender: ActorRef, result: Either[Seq[ErrorStats], Seq[ActorStats]]): Unit = body(sender, result)
//  }
//}
//
//class DeviationsHandlerSpec extends ActorsSpec("DeviationsHandlerSpec") with ActorHandlerSpecification {
//  isolated
//  import DeviationsHandlerSpec._
//  import DeviationsJsonBuilder._
//
//  "Deviations Handler" should {
//    "Find data for actors" in {
//      var resultSender: ActorRef = null
//      var resultResult: Either[Seq[ErrorStats], Seq[ActorStats]] = null
//
//      val h = deviationsHandler(repository) { (ar, result) =>
//        resultSender = ar
//        resultResult = result
//      }
//
//      forall(stats) { (as: ActorStats) =>
//        resultSender = null
//        resultResult = null
//
//        h.onModuleInformation(ActorRef.noSender, DeviationsHandler.DeviationsModuleInfo(as.scope,
//          time = as.timeRange,
//          dataFrom = None,
//          chunkRange = None))
//
//        resultSender must equalTo(ActorRef.noSender)
//        resultResult must (beRight { (r: Seq[ActorStats]) =>
//          r must have length (1)
//          r.head must beEqualActorStats(as)
//        })
//      }
//    }
//    "Find data for errors" in {
//      var resultSender: ActorRef = null
//      var resultResult: Either[Seq[ErrorStats], Seq[ActorStats]] = null
//
//      val h = deviationsHandler(repository) { (ar, result) =>
//        resultSender = ar
//        resultResult = result
//      }
//
//      forall(errorStats) { (es: ErrorStats) =>
//        resultSender = null
//        resultResult = null
//
//        h.onModuleInformation(ActorRef.noSender, DeviationsHandler.DeviationsModuleInfo(Scope(),
//          time = es.timeRange,
//          dataFrom = None,
//          chunkRange = None))
//
//        resultSender must equalTo(ActorRef.noSender)
//        resultResult must (beLeft { (r: Seq[ErrorStats]) =>
//          r must have length (1)
//          // r.head must equalTo(es) // At the moment cannot predict what value should be produced
//          // Because counts for error stats are recomputed after filter
//          // operation on time.  Need a better way to test this.
//        })
//      }
//    }
//    "Send found actor data to the JSON builder" in {
//      val deviationsHandler = system.actorOf(DeviationsHandler.props(repository, 10, FakeJsonBuilder.props(testActor)))
//
//      val r = forall(stats) { (as: ActorStats) =>
//
//        deviationsHandler ! DeviationsHandler.DeviationsModuleInfo(as.scope,
//          time = as.timeRange,
//          dataFrom = None,
//          chunkRange = None)
//
//        expectMsgPF() {
//          case x: DeviationsResult =>
//            x.receiver must equalTo(testActor)
//            x.result must (beRight { (r: Seq[ActorStats]) =>
//              r must have length (1)
//              r.head must beEqualActorStats(as)
//            })
//        }
//      }
//
//      system.stop(deviationsHandler)
//      r
//    }
//    "Send found error data to the JSON builder" in {
//      val deviationsHandler = system.actorOf(DeviationsHandler.props(repository, 10, FakeJsonBuilder.props(testActor)))
//
//      val r = forall(errorStats) { (es: ErrorStats) =>
//
//        deviationsHandler ! DeviationsHandler.DeviationsModuleInfo(Scope(),
//          time = es.timeRange,
//          dataFrom = None,
//          chunkRange = None)
//
//        expectMsgPF() {
//          case x: DeviationsResult =>
//            x.receiver must equalTo(testActor)
//            x.result must (beLeft { (r: Seq[ErrorStats]) =>
//              r must have length (1)
//              // r.head must equalTo(es) // At the moment cannot predict what value should be produced
//              // Because counts for error stats are recomputed after filter
//              // operation on time.  Need a better way to test this.
//            })
//        }
//      }
//
//      system.stop(deviationsHandler)
//      r
//    }
//    "Get JSON out for actor data" in {
//      // This is a bare minimum, mostly bogus test.
//      // Need meaningful way to ensure actual JSON conforms to expected value
//      val deviationsHandler = system.actorOf(DeviationsHandler.props(repository, 10))
//
//      val r = forall(stats) { (as: ActorStats) =>
//
//        deviationsHandler ! DeviationsHandler.DeviationsModuleInfo(as.scope,
//          time = as.timeRange,
//          dataFrom = None,
//          chunkRange = None)
//
//        expectMsgPF() {
//          case x: ClientController.Update => x.js must not equalTo (Json.obj())
//        }
//      }
//
//      system.stop(deviationsHandler)
//      r
//    }
//    "Get JSON out for error data" in {
//      // This is a bare minimum, mostly bogus test.
//      // Need meaningful way to ensure actual JSON conforms to expected value
//      val deviationsHandler = system.actorOf(DeviationsHandler.props(repository, 10))
//
//      val r = forall(errorStats) { (es: ErrorStats) =>
//
//        deviationsHandler ! DeviationsHandler.DeviationsModuleInfo(Scope(),
//          time = es.timeRange,
//          dataFrom = None,
//          chunkRange = None)
//
//        expectMsgPF() {
//          case x: ClientController.Update => x.js must not equalTo (Json.obj())
//        }
//      }
//
//      system.stop(deviationsHandler)
//      r
//    }
//  }
//
//}
