///**
// * Copyright (C) 2013 Typesafe <http://typesafe.com/>
// */
//package console
//
//import akka.actor._
//import akka.pattern._
//import akka.util.Timeout
//import play.api.libs.iteratee.{ Enumerator, Iteratee }
//import play.api.libs.json.JsValue
//import scala.concurrent.duration._
//import scala.concurrent.{ Future, ExecutionContext }
//import controllers.ConsoleController
//import play.api.Play.current
//
//class ClientController(clientHandlerProps: Props,
//  updateInterval: Option[FiniteDuration]) extends Actor with ActorLogging {
//  import ClientController._
//  import ExecutionContext.Implicits.global
//
//  val tickScheduler: Option[Cancellable] =
//    updateInterval.map(ui => context.system.scheduler.schedule(ui, ui, self, Tick))
//
//  def receive = {
//    case ic @ InitializeCommunication(id, _) =>
//      if (context.child(id).isEmpty)
//        context.actorOf(clientHandlerProps, id) forward ic
//    case Tick => context.children foreach { _ ! Tick }
//    case Shutdown =>
//      context.children foreach { _ ! Shutdown }
//      tickScheduler.map(_.cancel())
//  }
//}
//
//object ClientController {
//  case class CreateClient(id: String)
//  case class Connection(ref: ActorRef, enum: Enumerator[JsValue])
//  case class HandleRequest(payload: JsValue)
//  case class Update(js: JsValue)
//  case class InitializeCommunication(id: String, consumer: ActorRef)
//  case object Tick
//  case object Shutdown
//
//  def derivedProps(repository: AnalyticsRepository,
//    defaultLimit: Int,
//    updateInterval: Option[FiniteDuration]): Props =
//    props(ClientHandler.derivedProps(repository, defaultLimit), updateInterval)
//
//  def props(clientHandlerProps: Props,
//    updateInterval: Option[FiniteDuration] = Some(ConsoleController.config.getLong("console.update-frequency").milliseconds)): Props =
//    Props(classOf[ClientController], clientHandlerProps, updateInterval)
//
//  def join(id: String): Future[(Iteratee[JsValue, _], Enumerator[JsValue])] = {
//    import play.api.libs.concurrent.Execution.Implicits._
//    implicit val timeout = Timeout(1.second)
//    (ConsoleController.clientHandlerActor ? CreateClient(id)).map {
//      case Connection(ref, enumerator) => (Iteratee.foreach[JsValue] { ref ! HandleRequest(_) }.map(_ => ref ! PoisonPill), enumerator)
//    }
//  }
//}
