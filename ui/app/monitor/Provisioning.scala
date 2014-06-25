/**
 * Copyright (C) 2014 Typesafe <http://typesafe.com/>
 */

package monitor

import akka.actor.ActorRef
import java.io.File
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

object Provisioning {
  import snap.HttpHelper._
  val responseTag = "ProvisioningStatus"

  trait Credentials {
    def apply(request: WSRequestHolder)(implicit ec: ExecutionContext): Future[WSResponse]
    def failureDiagnostics: String
  }

  case class UsernamePasswordCredentials(username: String, password: String, usernameKey: String = "username", passwordKey: String = "password") extends Credentials {
    def apply(request: WSRequestHolder)(implicit ec: ExecutionContext): Future[WSResponse] = request.post(Map(usernameKey -> Seq(username), passwordKey -> Seq(password)))
    def failureDiagnostics: String = s"Username: $username"
  }

  case class LoginException(message: String, failureDiagnostics: String, url: String) extends Exception(message)

  sealed trait Status
  case class ProvisioningError(message: String, exception: Throwable) extends Status
  case class LoggingIn(diagnostics: String, url: String) extends Status
  case class Downloading(url: String) extends Status
  case class Progress(value: Either[Int, Double]) extends Status
  case class DownloadComplete(url: String) extends Status
  case object Validating extends Status
  case object Extracting extends Status
  case object Complete extends Status

  // Used to inhibit double notification of errors to the sink
  case class DownloadException(cause: Throwable) extends Exception

  implicit val provisioningErrorWrites: Writes[ProvisioningError] =
    emitResponse(responseTag)(in => Json.obj("type" -> "provisioningError",
      "message" -> in.message))

  implicit val loggingInWrites: Writes[LoggingIn] =
    emitResponse(responseTag)(in => Json.obj("type" -> "loggingIn",
      "username" -> in.diagnostics,
      "url" -> in.url))

  implicit val downloadingWrites: Writes[Downloading] =
    emitResponse(responseTag)(in => Json.obj("type" -> "downloading",
      "url" -> in.url))

  implicit val progressWrites: Writes[Progress] =
    emitResponse(responseTag)(in => Json.obj("type" -> "progress",
      in.value match {
        case Left(b) => "bytes" -> b
        case Right(p) => "percent" -> p
      }))

  implicit val downloadCompleteWrites: Writes[DownloadComplete] =
    emitResponse(responseTag)(in => Json.obj("type" -> "downloadComplete",
      "url" -> in.url))

  implicit val validatingWrites: Writes[Validating.type] =
    emitResponse(responseTag)(_ => Json.obj("type" -> "validating"))

  implicit val extractingWrites: Writes[Extracting.type] =
    emitResponse(responseTag)(_ => Json.obj("type" -> "extracting"))

  implicit val completeWrites: Writes[Complete.type] =
    emitResponse(responseTag)(_ => Json.obj("type" -> "complete"))

  def notificationProgressBuilder(url: String,
    notificationSink: ActorRef): ProgressObserver = new ProgressObserver {
    def onCompleted(): Unit =
      notificationSink ! DownloadComplete(url)

    def onError(error: Throwable): Unit =
      notificationSink ! ProvisioningError(s"Error downloading $url: ${error.getMessage}", error)

    def onNext(data: ChunkData): Unit = {
      data.contentLength match {
        case None =>
          notificationSink ! Progress(Left(data.total))
        case Some(cl) =>
          notificationSink ! Progress(Right((data.total.toDouble / cl.toDouble) * 100.0))
      }
    }
  }

  private def postprocessResults(expected: Future[File],
    validator: File => File,
    targetLocation: File,
    notificationSink: ActorRef)(implicit ec: ExecutionContext): Future[File] = {
    expected.transform(x => x, e => DownloadException(e)).map { file =>
      notificationSink ! Validating
      validator(file)
      notificationSink ! Extracting
      FileHelper.unZipFile(file, targetLocation)
    }
    expected.onComplete {
      case Success(_) => notificationSink ! Complete
      case Failure(error @ LoginException(message, username, url)) =>
        notificationSink ! ProvisioningError(s"Cannot login to $url with username: $username and password given: $message", error)
      case Failure(DownloadException(_)) => // Already reported
      case Failure(error) => notificationSink ! ProvisioningError(s"Error provisioning: ${error.getMessage}", error)
    }
    expected
  }

  def provision(downloadUrl: String,
    validator: File => File,
    targetLocation: File,
    notificationSink: ActorRef,
    timeout: Timeout = Timeout(30.seconds))(implicit ec: ExecutionContext): Future[File] = {
    notificationSink ! Downloading(downloadUrl)
    postprocessResults(retrieveFileHttp(WS.url(downloadUrl).withFollowRedirects(true),
      notificationProgressBuilder(downloadUrl, notificationSink),
      timeout = timeout),
      validator,
      targetLocation,
      notificationSink)
  }

  //Set-Cookie: value[; expires=date][; domain=domain][; path=path][; secure]
  private def serializeCookie(in: WSCookie): String = {
    import java.text.SimpleDateFormat
    import java.util.{ Date, Locale, TimeZone }
    val sdf = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss zzz", Locale.ROOT)
    sdf.setTimeZone(TimeZone.getTimeZone("GMT"))
    List[Option[String]](
      (in.name, in.value) match {
        case (Some(n), Some(v)) => Some(s"$n=$v")
        case (Some(n), None) => Some(s"$n=")
        case (None, Some(v)) => Some(v)
        case _ => None
      },
      in.expires.map { e =>
        val d = new Date(e)
        sdf.format(d)
      },
      in.maxAge.map(m => s"MAX-AGE=$m"),
      Some(s"DOMAIN=${in.domain}"),
      Some(s"PATH=${in.path}"),
      if (in.secure) Some("secure") else None).flatten.mkString("; ")
  }

  def provisionWithLogin(credentials: Credentials,
    loginUrl: String,
    downloadUrl: String,
    validator: File => File,
    targetLocation: File,
    notificationSink: ActorRef,
    timeout: Timeout = Timeout(30.seconds))(implicit ec: ExecutionContext): Future[File] = {
    // wget --save-cookies cookies.txt  --post-data 'username=jim.powers@typesafe.com&password=xxxxx' --no-check-certificate https://login.appdynamics.com/sso/login/
    notificationSink ! LoggingIn(credentials.failureDiagnostics, loginUrl)
    for {
      login <- credentials(WS.url(loginUrl).withFollowRedirects(true))
      cookies = login.cookies.map(serializeCookie)
      () = if (cookies.isEmpty) throw new LoginException(s"Confirm that you can log into: $loginUrl", credentials.failureDiagnostics, loginUrl)
      () = notificationSink ! Downloading(downloadUrl)
      file <- postprocessResults(retrieveFileHttp(WS.url(downloadUrl).withHeaders(cookies.map(c => "Set-Cookie" -> c): _*).withFollowRedirects(true),
        notificationProgressBuilder(downloadUrl, notificationSink),
        timeout = timeout),
        validator,
        targetLocation,
        notificationSink)
    } yield file
  }
}
