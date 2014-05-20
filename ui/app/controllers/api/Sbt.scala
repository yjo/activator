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
}
