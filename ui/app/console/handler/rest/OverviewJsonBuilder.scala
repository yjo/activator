//package console.handler.rest
//
//import akka.actor.{ ActorRef, Props }
//import activator.analytics.data._
//import play.api.libs.json._
//import console.ClientController.Update
//import play.api.libs.json.JsObject
//import activator.analytics.data.MetadataStats
//
//class OverviewJsonBuilder extends JsonBuilderActor {
//  import OverviewJsonBuilder._
//
//  def receive = {
//    case r: OverviewResult => r.receiver ! Update(createJson(r.metadata, r.deviations, r.currentStorageTime))
//  }
//}
//
//object OverviewJsonBuilder {
//  def props(): Props =
//    Props(classOf[OverviewJsonBuilder])
//
//  case class OverviewResult(receiver: ActorRef, metadata: MetadataStats, deviations: ErrorStats, currentStorageTime: Long)
//
//  def createJson(metadata: MetadataStats, deviations: ErrorStats, currentStorageTime: Long): JsObject = {
//    Json.obj(
//      "type" -> "overview",
//      "data" ->
//        Json.obj(
//          "metadata" ->
//            Json.obj(
//              "playPatternCount" -> metadata.metrics.playPatterns.size,
//              "actorPathCount" -> metadata.metrics.paths.size),
//          "deviations" -> Json.obj(
//            "deviationCount" -> deviations.metrics.counts.total),
//          "currentStorageTime" -> currentStorageTime))
//  }
//}
