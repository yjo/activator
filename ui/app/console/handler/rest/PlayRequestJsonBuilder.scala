///**
// * Copyright (C) 2013 Typesafe <http://typesafe.com/>
// */
//package console.handler.rest
//
//import akka.actor.{ ActorRef, Props }
//import activator.analytics.data.PlayRequestSummary
//import play.api.libs.json._
//import play.api.libs.json.JsArray
//import console.ClientController.Update
//import play.api.libs.json.JsObject
//import com.typesafe.trace.{ ActorInfo, ActionSimpleResult, ActionChunkedResult, ActionResponseAnnotation }
//
//class PlayRequestJsonBuilder extends JsonBuilderActor {
//  import PlayRequestJsonBuilder._
//
//  def receive = {
//    case r: PlayRequestResult => r.receiver ! Update(createJson(r))
//  }
//}
//
//object PlayRequestJsonBuilder {
//  def props(): Props =
//    Props(classOf[PlayRequestJsonBuilder])
//
//  case class PlayRequestResult(receiver: ActorRef, stats: PlayRequestSummary, actorInfo: Set[ActorInfo])
//
//  def createJson(result: PlayRequestResult): JsObject = {
//    Json.obj(
//      "type" -> "request",
//      "data" ->
//        Json.obj(
//          "playRequestSummary" -> createPlayRequestJson(result)))
//  }
//
//  def createPlayRequestJson(prr: PlayRequestResult): JsObject = {
//    val result = prr.stats
//    Json.obj(
//      "traceId" -> result.traceId.toString,
//      "id" -> result.invocationInfo.id,
//      "path" -> result.invocationInfo.path,
//      "remoteAddress" -> result.invocationInfo.remoteAddress,
//      "uri" -> result.invocationInfo.uri,
//      "version" -> result.invocationInfo.version,
//      "controller" -> result.invocationInfo.controller,
//      "controllerMethod" -> result.invocationInfo.method,
//      "httpMethod" -> result.invocationInfo.httpMethod,
//      "startTimeMillis" -> result.start.millis,
//      "endTimeMillis" -> result.end.millis,
//      "invocationTimeMillis" -> (result.end.millis - result.start.millis),
//      "httpResponseCode" -> result.response.resultInfo.httpResponseCode,
//      "headers" -> generateHeaders(result.requestInfo.headers),
//      "bytesIn" -> result.bytesIn,
//      "bytesOut" -> result.bytesOut,
//      "domain" -> result.invocationInfo.domain,
//      "host" -> result.host,
//      "node" -> result.node,
//      "summaryType" -> result.summaryType.toString,
//      "type" -> generateType(result.response),
//      "actors" -> generateActorInfo(prr.actorInfo))
//  }
//
//  private def generateHeaders(headers: Map[String, Seq[String]]): JsValue = {
//    Json.obj(
//      "type" ->
//        headers.map {
//          case (k, v) => k -> JsArray(v.map(JsString(_)))
//        })
//  }
//
//  private def generateType(info: ActionResponseAnnotation): JsString = info match {
//    case _: ActionSimpleResult => JsString("simple")
//    case _ => JsString("chunked")
//  }
//
//  private def generateActorInfo(info: Set[ActorInfo]): JsValue = {
//    Json.toJson(info.map { i =>
//      Json.obj("path" -> i.path)
//    })
//  }
//}
