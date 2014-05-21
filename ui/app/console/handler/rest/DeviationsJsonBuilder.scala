///**
// * Copyright (C) 2013 Typesafe <http://typesafe.com/>
// */
//package console.handler.rest
//
//import akka.actor.{ ActorRef, Props }
//import console.ClientController.Update
//import play.api.libs.json.{ Json, JsObject, JsValue, JsArray, Writes, JsString }
//
//class DeviationsJsonBuilder extends JsonBuilderActor {
//  import DeviationsJsonBuilder._
//
//  def receive = {
//    case r: DeviationsResult => r.receiver ! Update(createJson(r.result))
//  }
//}
//
//object DeviationsJsonBuilder {
//  import activator.analytics.data._
//
//  def props(): Props = Props(classOf[DeviationsJsonBuilder])
//
//  case class DeviationsResult(receiver: ActorRef, result: Either[Seq[ErrorStats], Seq[ActorStats]])
//
//  // This is not quite right.  The old Atmos code post-processed the JSON stripping out a lot of stuff
//  // by calling JsonParser.deviations.  I think that both JSON "shapes" below should satisfy the client
//  def createJson(result: Either[Seq[ErrorStats], Seq[ActorStats]]): JsObject = result match {
//    case Left(errorStats) =>
//      Json.obj(
//        "type" -> "deviations",
//        "data" ->
//          Json.obj(
//            "deviations" -> ErrorStatsJsonBuilder.createJsonSeq(errorStats)))
//    case Right(actorStats) =>
//      Json.obj(
//        "type" -> "deviations",
//        "data" ->
//          Json.obj(
//            "deviations" -> ActorJsonBuilder.createActorJsonSeq(actorStats)))
//  }
//}
