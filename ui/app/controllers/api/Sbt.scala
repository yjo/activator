/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package controllers.api

import play.api.libs.json._
import com.typesafe.sbtrc._
import play.api.mvc._
import play.filters.csrf._
import snap.AppManager
import akka.pattern._
import akka.actor._
import scala.concurrent.ExecutionContext.Implicits.global
import play.Logger
import scala.concurrent.Future
import snap.NotifyWebSocket
import java.net.URLEncoder
import snap.UpdateSourceFiles
import snap.JsonHelper._

object Sbt extends Controller {
  implicit val timeout = snap.Akka.longTimeoutThatIsAProblem

  private def jsonAction(f: JsValue => Future[Result]): Action[AnyContent] = CSRFCheck {
    Action.async { request =>
      request.body.asJson.map({ json =>
        try f(json)
        catch {
          case e: Exception =>
            Logger.info("json action failed: " + e.getMessage(), e)
            Future.successful(BadRequest(e.getClass.getName + ": " + e.getMessage))
        }
      }).getOrElse(Future.successful(BadRequest("expecting JSON body")))
    }
  }

  def requestExecution() = jsonAction { json =>
    val appId = (json \ "appId").as[String]
    val command = (json \ "command").as[String]

    val resultFuture = AppManager.loadApp(appId) flatMap { app =>
      app.actor.ask(snap.RequestExecution(command)) map {
        case executionId: Long =>
          Ok(Json.obj("id" -> JsNumber(executionId)))
        case other =>
          throw new RuntimeException("Unexpected reply to request execution " + other)
      }
    }
    resultFuture
  }

  def possibleAutocompletions() = jsonAction { json =>
    val appId = (json \ "appId").as[String]
    val partialCommand = (json \ "partialCommand").as[String]

    val resultFuture = AppManager.loadApp(appId) flatMap { app =>
      app.actor.ask(snap.PossibleAutocompletions(partialCommand)) map {
        case choicesAny: Set[_] =>
          val choices = choicesAny.map(_.asInstanceOf[sbt.protocol.Completion])
          val jsonChoices = JsArray(choices.toList map { choice =>
            Json.toJson(choice)
          })
          Ok(Json.obj("choices" -> jsonChoices))
        case other =>
          throw new RuntimeException("Unexpected reply to autocompletions " + other)
      }
    }
    resultFuture
  }
}
