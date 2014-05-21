//package console.handler.rest
//
//import play.api.libs.json.{ Json, JsObject, JsValue, JsArray, Writes, JsString }
//import activator.analytics.data.{ TimeRange, ErrorStats, DeviationDetail }
//
//object ErrorStatsJsonBuilder {
//  import TimeRangeJsonBuilder._
//  import JsonBuilder._
//  import DevationDetailJsonBuilder._
//
//  def createJson(errorStats: ErrorStats): JsObject = {
//    Json.obj(
//      "deadletterCount" -> errorStats.metrics.counts.deadLetters,
//      "deadletters" -> createDeviationDetailJsonSeq(errorStats.metrics.deviations.deadLetters),
//      "deadlockCount" -> errorStats.metrics.counts.deadlocks,
//      "deadlocks" -> createDeviationDetailJsonSeq(errorStats.metrics.deviations.deadlockedThreads),
//      "deviationCount" -> errorStats.metrics.counts.total,
//      "errorCount" -> errorStats.metrics.counts.errors,
//      "errors" -> createDeviationDetailJsonSeq(errorStats.metrics.deviations.errors),
//      "id" -> errorStats.id.toString,
//      "timerange" -> createTimeRangeJson(errorStats.timeRange),
//      "unhandledMessageCount" -> errorStats.metrics.counts.unhandledMessages,
//      "unhandledMessages" -> createDeviationDetailJsonSeq(errorStats.metrics.deviations.unhandledMessages),
//      "warningCount" -> errorStats.metrics.counts.warnings,
//      "warnings" -> createDeviationDetailJsonSeq(errorStats.metrics.deviations.warnings)) ++
//      optJson("node", errorStats.node) ++
//      optJson("actorSystem", errorStats.actorSystem)
//  }
//
//  def createJsonSeq(errorStatsSeq: Seq[ErrorStats]): JsArray =
//    new JsArray(errorStatsSeq.map(createJson(_)))
//}
