//package console.handler
//
//import com.typesafe.trace.{ TraceEvent, TraceEvents, Batch }
//import akka.actor.{ ActorRef, Props, ActorSystem, Actor }
//import com.typesafe.trace.uuid.UUID
//import console.handler.rest.DeviationJsonBuilder.{ DeviationResult, ValidResult, InvalidResult }
//import console.ClientController
//import play.api.libs.json.Json
//import console.handler.rest.DeviationJsonBuilder
//import console.AnalyticsRepository
//
//object DeviationHandlerSpec {
//  import Generators._
//
//  val traceId = new UUID()
//  val traces = genTraceEvents(randomFlatten(genNMessageTraceAnnotations(20)), traceId)
//
//  lazy val repository: AnalyticsRepository = {
//    val r = AnalyticsRepository.freshMemoryObjects
//    val tr = r.traceRepository
//    tr.store(Batch(Seq(TraceEvents(traces))))
//    r
//  }
//
//  abstract class DeviationHandlerBuilder {
//    def validResonse(sender: ActorRef, eventId: UUID, event: TraceEvent, traces: Seq[TraceEvent]): Unit
//    def invalidResponse(sender: ActorRef, eventId: UUID): Unit
//  }
//
//  def deviationHandler(repo: AnalyticsRepository, builder: DeviationHandlerBuilder): DeviationHandlerBase = new DeviationHandlerBase {
//    val repository: AnalyticsRepository = repo
//    def useNoDeviation(sender: ActorRef, eventId: UUID): Unit = builder.invalidResponse(sender, eventId)
//    def useDeviation(sender: ActorRef, eventId: UUID, event: TraceEvent, traces: Seq[TraceEvent]): Unit = builder.validResonse(sender, eventId, event, traces)
//  }
//}
//
//class DeviationHandlerSpec extends ActorsSpec("DeviationHandlerSpec") {
//  isolated
//  import DeviationHandlerSpec._
//
//  "Deviation Handler" should {
//    "Find data" in {
//      var resultSender: ActorRef = null
//      var resultEvent: TraceEvent = null
//      var resultTraces: Seq[TraceEvent] = null
//      var resultEventID: UUID = null
//
//      val builder = new DeviationHandlerBuilder {
//        def validResonse(sender: ActorRef, eventId: UUID, event: TraceEvent, traces: Seq[TraceEvent]): Unit = {
//          resultSender = sender
//          resultTraces = traces
//          resultEvent = event
//          resultEventID = eventId
//        }
//        def invalidResponse(sender: ActorRef, eventId: UUID): Unit = {
//          resultSender = sender
//          resultEventID = eventId
//        }
//      }
//
//      val h = deviationHandler(repository, builder)
//
//      forall(repository.traceRepository.allEventIds) { (eventID: UUID) =>
//        resultSender = null
//        resultTraces = null
//        resultEvent = null
//        resultEventID = null
//        h.onModuleInformation(ActorRef.noSender, DeviationHandler.DeviationModuleInfo(eventID))
//
//        val event = repository.traceRepository.event(eventID).get
//
//        resultSender must equalTo(ActorRef.noSender)
//        resultEventID must equalTo(eventID)
//        resultEvent must equalTo(event)
//        resultTraces must equalTo(traces)
//      }
//    }
//    "Not find data for unknown event IDs" in {
//      var resultSender: ActorRef = null
//      var resultEvent: TraceEvent = null
//      var resultTraces: Seq[TraceEvent] = null
//      var resultEventID: UUID = null
//
//      val builder = new DeviationHandlerBuilder {
//        def validResonse(sender: ActorRef, eventId: UUID, event: TraceEvent, traces: Seq[TraceEvent]): Unit = {
//          resultSender = sender
//          resultTraces = traces
//          resultEvent = event
//          resultEventID = eventId
//        }
//        def invalidResponse(sender: ActorRef, eventId: UUID): Unit = {
//          resultSender = sender
//          resultEventID = eventId
//        }
//      }
//
//      val h = deviationHandler(repository, builder)
//
//      val uniqueUUID = Generators.uniqueUUID(repository.traceRepository.allEventIds.toSet)
//
//      h.onModuleInformation(ActorRef.noSender, DeviationHandler.DeviationModuleInfo(uniqueUUID))
//
//      resultEventID must equalTo(uniqueUUID)
//      resultSender must equalTo(ActorRef.noSender)
//      resultTraces must equalTo(null)
//      resultEvent must equalTo(null)
//    }
//    "Send found data to the JSON builder" in {
//      val deviationsHandler = system.actorOf(DeviationHandler.props(repository, FakeJsonBuilder.props(testActor)))
//
//      val r = forall(repository.traceRepository.allEventIds.take(1)) { (eventID: UUID) =>
//        deviationsHandler ! DeviationHandler.DeviationModuleInfo(eventID)
//        val event = repository.traceRepository.event(eventID).get
//        val traces = repository.traceRepository.trace(event.trace)
//        val expected = ValidResult(testActor, eventID, event, traces)
//        expectMsgPF() {
//          case x: DeviationResult => x must equalTo(expected)
//        }
//      }
//
//      system.stop(deviationsHandler)
//      r
//    }
//    "Get JSON out" in {
//      val deviationsHandler = system.actorOf(DeviationHandler.props(repository))
//
//      val r = forall(repository.traceRepository.allEventIds.take(1)) { (eventID: UUID) =>
//        deviationsHandler ! DeviationHandler.DeviationModuleInfo(eventID)
//        val event = repository.traceRepository.event(eventID).get
//        val traces = repository.traceRepository.trace(event.trace)
//        val expected = ValidResult(testActor, eventID, event, traces)
//        val tracesJson = DeviationJsonBuilder.createJson(expected)
//        expectMsgPF() {
//          case x: ClientController.Update => x.js must equalTo(tracesJson)
//        }
//      }
//
//      system.stop(deviationsHandler)
//      r
//    }
//  }
//
//}
