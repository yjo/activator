///**
// * Copyright (C) 2013 Typesafe <http://typesafe.com/>
// */
//package console.handler.rest
//
//import akka.actor.{ ActorRef, Props }
//import console.ClientController.Update
//import activator.analytics.data.PlayRequestSummary
//import play.api.libs.json.{ Json, JsObject }
//
//class PlayRequestsJsonBuilder extends JsonBuilderActor {
//  import PlayRequestsJsonBuilder._
//
//  def receive = {
//    case r: PlayRequestsResult => r.receiver ! Update(createPlayRequestsJson(r.stats))
//  }
//}
//
//object PlayRequestsJsonBuilder {
//  def props(): Props =
//    Props(classOf[PlayRequestsJsonBuilder])
//
//  case class PlayRequestsResult(receiver: ActorRef, stats: Seq[PlayRequestSummary])
//
//  def createPlayRequestsJson(stats: Seq[PlayRequestSummary]): JsObject = {
//    Json.obj(
//      "type" -> "requests",
//      "data" ->
//        Json.obj(
//          "playRequestSummaries" ->
//            Json.toJson(stats.map { createPlayRequestJson(_) })))
//  }
//
//  def createPlayRequestJson(req: PlayRequestSummary): JsObject = {
//    Json.obj(
//      "traceId" -> req.traceId.toString,
//      "id" -> req.invocationInfo.id,
//      "startTimeMillis" -> req.start.millis,
//      "path" -> req.invocationInfo.path,
//      "controller" -> req.invocationInfo.controller,
//      "controllerMethod" -> req.invocationInfo.method,
//      "httpMethod" -> req.invocationInfo.httpMethod,
//      "httpResponseCode" -> req.response.resultInfo.httpResponseCode,
//      "invocationTimeMillis" -> (req.end.millis - req.start.millis))
//  }
//}
