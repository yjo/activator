///**
// * Copyright (C) 2013 Typesafe <http://typesafe.com/>
// */
//package console.handler.rest
//
//import akka.actor.{ ActorRef, Props }
//import console.ClientController.Update
//import activator.analytics.repository.ActorStatsSorted
//import play.api.libs.json.{ Json, JsObject }
//
//class ActorsJsonBuilder extends JsonBuilderActor {
//  import ActorsJsonBuilder._
//
//  def receive = {
//    case r: ActorsResult => r.receiver ! Update(createJson(r.actorStats))
//  }
//}
//
//object ActorsJsonBuilder {
//  def props(): Props =
//    Props(classOf[ActorsJsonBuilder])
//
//  case class ActorsResult(receiver: ActorRef, actorStats: ActorStatsSorted)
//
//  def createJson(stats: ActorStatsSorted): JsObject = {
//    Json.obj(
//      "type" -> "actors",
//      "data" ->
//        Json.obj(
//          "actors" -> createActorsJson(stats)))
//  }
//
//  def createActorsJson(stats: ActorStatsSorted): JsObject =
//    Json.obj(
//      "actors" -> Json.toJson(stats.stats.map { ActorJsonBuilder.createActorJson(_) }),
//      "offset" -> stats.offset,
//      "limit" -> stats.limit,
//      "total" -> stats.total)
//}
