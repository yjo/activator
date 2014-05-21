//package console.handler.rest
//
//import play.api.libs.json.{ Json, JsObject, JsValue, JsArray, Writes, JsString }
//import activator.analytics.data.DeviationDetail
//
//object DevationDetailJsonBuilder {
//  def createDeviationDetailJsonSeq(deviationDetails: Seq[DeviationDetail]): JsArray =
//    new JsArray(deviationDetails.map(createDeviationDetailJson(_)))
//
//  def createDeviationDetailJson(deviationDetail: DeviationDetail): JsObject =
//    Json.obj(
//      "event" -> deviationDetail.eventId.toString,
//      "trace" -> deviationDetail.traceId.toString,
//      "message" -> deviationDetail.message,
//      "timestamp" -> deviationDetail.timestamp)
//}
