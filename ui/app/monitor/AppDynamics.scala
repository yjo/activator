/**
 * Copyright (C) 2014 Typesafe <http://typesafe.com/>
 */

package monitor

import akka.actor._
import java.io.File
import akka.util.Timeout
import snap.HttpHelper._
import scala.concurrent.duration._
import monitor.Provisioning.{ StatusNotifier, DownloadExecutor, DownloadPrepExecutor }
import play.api.libs.ws.{ WSClient, WSResponse, WSRequestHolder, WSCookie }
import snap.{ FileHelper, AppDynamics => AD }
import scala.util.{ Try, Failure, Success }
import scala.concurrent.{ Future, ExecutionContext }
import akka.event.LoggingAdapter

object AppDynamics {
  def props(config: AD.Config,
    executionContext: ExecutionContext): Props =
    Props(new AppDynamics(new Underlying(config)(_)(executionContext)))

  def unapply(in: Any): Option[Request] = in match {
    case r: Request => Some(r)
    case _ => None
  }

  sealed class Username private[AppDynamics] (val value: String)
  sealed class Password private[AppDynamics] (val value: String)

  object Username {
    def apply(in: String): Username = {
      val v = in.trim()
      assert(v.nonEmpty, "Username may not be empty")
      new Username(v)
    }
  }

  object Password {
    def apply(in: String): Password = {
      val v = in.trim()
      assert(v.nonEmpty, "Password may not be empty")
      new Password(v)
    }
  }
  sealed trait Request {
    def error(message: String): Response =
      ErrorResponse(message, this)
  }

  case class Provision(notificationSink: ActorRef, username: Username, password: Password) extends Request {
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

  private def serializeCookie(in: WSCookie): String = {
    (in.name, in.value) match {
      case (Some(n), Some(v)) => s"$n=$v"
      case (None, Some(v)) => v
      case _ => ""
    }
  }

  trait Credentials {
    def apply(request: WSRequestHolder)(implicit ec: ExecutionContext): Future[WSResponse]
    def failureDiagnostics: String
  }

  case class UsernamePasswordCredentials(username: String, password: String, usernameKey: String = "username", passwordKey: String = "password") extends Credentials {
    def apply(request: WSRequestHolder)(implicit ec: ExecutionContext): Future[WSResponse] = request.post(Map(usernameKey -> Seq(username), passwordKey -> Seq(password)))
    def failureDiagnostics: String = s"Username: $username"
  }

  def prepareDownload(client: WSClient,
    credentials: Credentials,
    loginUrl: String,
    downloadUrl: String,
    notificationSink: StatusNotifier,
    timeout: Timeout = Timeout(30.seconds))(implicit ec: ExecutionContext): DownloadPrepExecutor = new DownloadPrepExecutor {
    import Provisioning._
    def execute(): Future[DownloadExecutor] = {
      notificationSink.authenticating(credentials.failureDiagnostics, loginUrl)
      for {
        login <- credentials(client.url(loginUrl).withFollowRedirects(false).withRequestTimeout(timeout.duration.toMillis.toInt))
        cookies = login.cookies.map(serializeCookie).filter(_.nonEmpty)
        () = if (cookies.isEmpty) throw new AuthenticationException(s"Confirm that you can log into: $loginUrl", credentials.failureDiagnostics, loginUrl)
      } yield {
        val dl = downloadUrl
        new DownloadExecutor {
          def downloadUrl: String = dl
          def execute(): Future[File] =
            retrieveFileHttp(client.url(downloadUrl).withHeaders("Cookie" -> cookies.mkString("; ")).withFollowRedirects(true),
              notificationProgressBuilder(downloadUrl, notificationSink),
              timeout = timeout)
          def failureDiagnostics: String = s"Download url: $downloadUrl"
        }

      }
    }
    def failureDiagnostics: String = credentials.failureDiagnostics
  }

  class Underlying(config: AD.Config)(log: LoggingAdapter)(implicit ec: ExecutionContext) {
    import Provisioning._

    def reportError(error: Throwable, message: String, request: Request, sender: ActorRef): Unit = {
      log.error(error, message)
      val r = request.error(message)
    }

    def onMessage(request: Request, sender: ActorRef, self: ActorRef, context: ActorContext): Unit = request match {
      case r @ Provision(sink, username, password) =>
        val ns = actorWrapper(sink)
        prepareDownload(defaultWSClient,
          UsernamePasswordCredentials(username.value, password.value),
          config.loginUrl,
          config.url,
          ns,
          config.timeout).execute().flatMap(de => provision(de, x => x, config.extractRoot(), ns)) onComplete {
            case Success(_) => sender ! r.response
            case Failure(error) =>
              reportError(error, s"Failure during provisioning: ${error.getMessage}", r, sender)
              // This shouldn't be necessary.  Somewhere an exception if being eaten.
              ns.provisioningError(s"Failure during provisioning: ${error.getMessage}", error)
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
    case r: AppDynamics.Request => newRelic.onMessage(r, sender, self, context)
  }
}
