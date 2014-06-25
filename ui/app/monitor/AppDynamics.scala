/**
 * Copyright (C) 2014 Typesafe <http://typesafe.com/>
 */

package monitor

import akka.actor._
import java.io.File
import snap.{ FileHelper, AppDynamics => AD }
import scala.util.{ Try, Failure, Success }
import scala.concurrent.ExecutionContext
import akka.event.LoggingAdapter

object AppDynamics {
  def props(config: AD.Config,
    executionContext: ExecutionContext): Props =
    Props(new AppDynamics(new Underlying(config)(_)(executionContext)))

  def unapply(in: Any): Option[Request] = in match {
    case r: Request => Some(r)
    case _ => None
  }

  sealed trait Request {
    def error(message: String): Response =
      ErrorResponse(message, this)
  }

  case class Provision(notificationSink: ActorRef) extends Request {
    def response: Response = Provisioned(this)
  }

  case object Available extends Request {
    def response(result: Boolean): Response = AvailableResponse(result, this)
  }

  sealed trait Response {
    def request: Request
  }
  case class Provisioned(request: Provision) extends Response
  case class ErrorResponse(message: String, request: Request) extends Response
  case class AvailableResponse(result: Boolean, request: Request) extends Response

  class Underlying(config: AD.Config)(log: LoggingAdapter)(implicit ec: ExecutionContext) {
    def onMessage(request: Request, sender: ActorRef, self: ActorRef, context: ActorContext): Unit = request match {
      case r @ Provision(sink) =>
        Provisioning.provision(config.url,
          x => x,
          config.extractRoot(),
          sink,
          config.timeout) onComplete {
            case Success(_) => sender ! r.response
            case Failure(error) =>
              log.error(error, "Failure during provisioning")
              sender ! r.error(s"Error processing provisioning request: ${error.getMessage}")
          }
      case r @ Available =>
        Try(AD.hasAppDynamics(config.extractRoot())) match {
          case Success(v) => sender ! r.response(v)
          case Failure(e) =>
            log.error(e, "Failure during AppDynamics availability check")
            sender ! r.error(s"Failure during AppDynamics availability check: ${e.getMessage}")
        }
    }
  }
}

class AppDynamics(newRelicBuilder: LoggingAdapter => AppDynamics.Underlying) extends Actor with ActorLogging {
  val newRelic = newRelicBuilder(log)

  def receive: Receive = {
    case r: AppDynamics.Request => newRelic.onMessage(r, sender(), self, context)
  }
}
