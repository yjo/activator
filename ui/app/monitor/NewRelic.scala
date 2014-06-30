/**
 * Copyright (C) 2014 Typesafe <http://typesafe.com/>
 */

package monitor

import akka.actor._
import java.io.File
import snap.{ FileHelper, NewRelic => NR }
import scala.util.{ Try, Failure, Success }
import scala.concurrent.ExecutionContext
import akka.event.LoggingAdapter

import akka.actor.ActorRef
import java.io.File
import play.api.libs.ws.ssl.{ DefaultSSLLooseConfig, DefaultSSLConfig, DefaultSSLConfigParser }
import snap.HttpHelper.ProgressObserver

import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.ws._
import snap.{ JsonHelper, FileHelper }
import akka.util.Timeout
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Json._
import JsonHelper._
import play.api.Play.current
import play.api.libs.ws.ning._
import com.ning.http.client.AsyncHttpClientConfig

object NewRelic {
  def props(config: NR.Config,
    executionContext: ExecutionContext): Props =
    Props(new NewRelic(new Underlying(config)(_)(executionContext)))

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

  case class EnableProject(destination: File, key: String, appName: String) extends Request {
    def response: Response = ProjectEnabled(this)
  }

  case class IsProjectEnabled(destination: File) extends Request {
    def response(result: Boolean): Response = IsProjectEnabledResult(result, this)
  }

  sealed trait Response {
    def request: Request
  }
  case class Provisioned(request: Provision) extends Response
  case class ErrorResponse(message: String, request: Request) extends Response
  case class AvailableResponse(result: Boolean, request: Request) extends Response
  case class ProjectEnabled(request: Request) extends Response
  case class IsProjectEnabledResult(result: Boolean, request: Request) extends Response

  class Underlying(config: NR.Config)(log: LoggingAdapter)(implicit ec: ExecutionContext) {
    import Provisioning._

    def reportError(error: Throwable, message: String, request: Request, sender: ActorRef): Unit = {
      log.error(error, message)
      sender ! request.error(message)
    }

    def onMessage(request: Request, sender: ActorRef, self: ActorRef, context: ActorContext): Unit = request match {
      case r @ Provision(sink) =>
        val ns = actorWrapper(sink)
        provision(
          simpleDownloadExecutor(defaultWSClient,
            config.url, ns, config.timeout),
          FileHelper.verifyFile(_, config.sha),
          config.extractRoot(), ns) onComplete {
            case Success(_) => sender ! r.response
            case Failure(error) =>
              reportError(error, s"Error processing provisioning request: ${error.getMessage}", r, sender)
          }
      case r @ Available =>
        Try(NR.hasNewRelic(config.extractRoot())) match {
          case Success(v) => sender ! r.response(v)
          case Failure(e) =>
            log.error(e, "Failure during New Relic availability check")
            sender ! r.error(s"Failure during New Relic availability check: ${e.getMessage}")
        }
      case r @ EnableProject(destination, key, name) =>
        Try(NR.provisionNewRelic(config.extractRoot(), destination, key, name)) match {
          case Success(_) => sender ! r.response
          case Failure(e) =>
            log.error(e, "Failure during enabling project")
            sender ! r.error(s"Failure during enabling project: ${e.getMessage}")
        }
      case r @ IsProjectEnabled(destination) =>
        Try(NR.isProjectEnabled(destination)) match {
          case Success(x) => sender ! r.response(x)
          case Failure(e) =>
            log.error(e, "Failure testing if project enabled for New Relic")
            sender ! r.error(s"Failure testing if project enabled for New Relic: ${e.getMessage}")
        }
    }
  }
}

class NewRelic(newRelicBuilder: LoggingAdapter => NewRelic.Underlying) extends Actor with ActorLogging {
  val newRelic = newRelicBuilder(log)

  def receive: Receive = {
    case r: NewRelic.Request => newRelic.onMessage(r, sender, self, context)
  }
}
