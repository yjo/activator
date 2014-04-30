/**
 * Copyright (C) 2014 Typesafe <http://typesafe.com/>
 */

package monitor

import akka.actor.ActorRef
import java.io.File
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

object Provisioning {
  import snap.HttpHelper._
  val responseTag = "ProvisioningStatus"

  sealed trait Status
  case class ProvisioningError(message: String, exception: Throwable) extends Status
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

  def provision(downloadUrl: String,
    validator: File => File,
    targetLocation: File,
    notificationSink: ActorRef,
    timeout: Timeout = Timeout(30.seconds))(implicit ec: ExecutionContext): Future[File] = {
    notificationSink ! Downloading(downloadUrl)
    val result = retrieveFileHttp(WS.url(downloadUrl).withFollowRedirects(true),
      notificationProgressBuilder(downloadUrl, notificationSink),
      timeout = timeout).transform(x => x, e => DownloadException(e)).map { file =>
        notificationSink ! Validating
        validator(file)
        notificationSink ! Extracting
        FileHelper.unZipFile(file, targetLocation)
      }
    result onComplete {
      case Success(_) => notificationSink ! Complete
      case Failure(DownloadException(_)) => // Already reported
      case Failure(error) => notificationSink ! ProvisioningError(s"Error provisioning: ${error.getMessage}", error)
    }
    result
  }
}
