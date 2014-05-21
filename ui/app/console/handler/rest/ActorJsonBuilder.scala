///**
// * Copyright (C) 2013 Typesafe <http://typesafe.com/>
// */
//package console.handler.rest
//
//import akka.actor.{ ActorRef, Props }
//import console.ClientController.Update
//import activator.analytics.data.{ Scope, ActorStats }
//import play.api.libs.json.{ Json, JsObject, JsValue, JsArray, Writes, JsString }
//import activator.analytics.data.BasicTypes.DurationNanos
//import java.util.concurrent.TimeUnit
//import java.util.concurrent.TimeUnit._
//
//class ActorJsonBuilder extends JsonBuilderActor {
//  import ActorJsonBuilder._
//
//  def receive = {
//    case r: ActorResult => r.receiver ! Update(createJson(r.stats))
//  }
//}
//
//object ActorJsonBuilder {
//  import ScopeJsonBuilder._
//  import TimeRangeJsonBuilder._
//  import DevationDetailJsonBuilder._
//
//  def props(): Props =
//    Props(classOf[ActorJsonBuilder])
//
//  val DefaultOutputDurationTimeUnit = MICROSECONDS
//
//  case class ActorResult(receiver: ActorRef, stats: ActorStats)
//
//  def createJson(stats: ActorStats): JsObject = {
//    Json.obj(
//      "type" -> "actor",
//      "data" ->
//        Json.obj(
//          "actor" -> createActorJson(stats)))
//  }
//
//  def createActorJsonSeq(statsSeq: Seq[ActorStats]): JsArray =
//    new JsArray(statsSeq.map(createActorJson(_)))
//
//  def createActorJson(stats: ActorStats): JsObject = {
//    Json.obj(
//      "timerange" -> createTimeRangeJson(stats.timeRange),
//      "scope" -> createScopeJson(stats.scope),
//      "createdCount" -> stats.metrics.counts.createdCount,
//      "stoppedCount" -> stats.metrics.counts.stoppedCount,
//      "failedCount" -> stats.metrics.counts.failedCount,
//      "restartCount" -> stats.metrics.counts.restartCount,
//      "deviationCount" -> stats.metrics.counts.deviationCount,
//      "errorCount" -> stats.metrics.counts.errorCount,
//      "errors" -> createDeviationDetailJsonSeq(stats.metrics.deviationDetails.errors),
//      "warningCount" -> stats.metrics.counts.warningCount,
//      "warnings" -> createDeviationDetailJsonSeq(stats.metrics.deviationDetails.warnings),
//      "deadletterCount" -> stats.metrics.counts.deadLetterCount,
//      "deadletters" -> createDeviationDetailJsonSeq(stats.metrics.deviationDetails.deadLetters),
//      "unhandledMessageCount" -> stats.metrics.counts.unhandledMessageCount,
//      "unhandledMessages" -> createDeviationDetailJsonSeq(stats.metrics.deviationDetails.unhandledMessages),
//      "deadlockCount" -> stats.metrics.deviationDetails.deadlockedThreads.size,
//      "deadlocks" -> createDeviationDetailJsonSeq(stats.metrics.deviationDetails.deadlockedThreads),
//      "processedMessagesCount" -> stats.metrics.counts.processedMessagesCount,
//      "tellMessagesCount" -> stats.metrics.counts.tellMessagesCount,
//      "askMessagesCount" -> stats.metrics.counts.askMessagesCount,
//      "meanMailboxSize" -> stats.metrics.meanMailboxSize,
//      "maxMailboxSize" -> stats.metrics.mailbox.maxMailboxSize,
//      "maxMailboxSizeTimestamp" -> stats.metrics.mailbox.maxMailboxSizeTimestamp,
//      "maxMailboxSizeAddressNode" -> stats.metrics.mailbox.maxMailboxSizeAddress.node,
//      "maxMailboxSizeAddressPath" -> stats.metrics.mailbox.maxMailboxSizeAddress.path,
//      "meanTimeInMailbox" -> generateValueUnitPair(stats.metrics.meanTimeInMailbox),
//      "maxTimeInMailbox" -> generateValueUnitPair(stats.metrics.mailbox.maxTimeInMailbox),
//      "maxTimeInMailboxTimestamp" -> stats.metrics.mailbox.maxTimeInMailboxTimestamp,
//      "maxTimeInMailboxAddressNode" -> stats.metrics.mailbox.maxTimeInMailboxAddress.node,
//      "maxTimeInMailboxAddressPath" -> stats.metrics.mailbox.maxTimeInMailboxAddress.path,
//      "latestTraceEventTimestamp" -> stats.metrics.latestTraceEventTimestamp,
//      "latestMessageTimestamp" -> stats.metrics.latestMessageTimestamp,
//      "totalMessageRate" -> stats.metrics.messageRateMetrics.totalMessageRate,
//      "receiveRate" -> stats.metrics.messageRateMetrics.receiveRate,
//      "askRate" -> stats.metrics.messageRateMetrics.askRate,
//      "tellRate" -> stats.metrics.messageRateMetrics.tellRate,
//      "meanProcessedMessageRate" -> stats.meanProcessedMessageRate,
//      "meanProcessedMessageRateUnit" -> "messages/second",
//      "rateUnit" -> "messages/second",
//      "meanProcessedMessageRate" -> stats.meanProcessedMessageRate,
//      "meanProcessedMessageRateUnit" -> "messages/second",
//      "meanBytesReadRate" -> stats.meanBytesReadRate,
//      "meanBytesReadRateUnit" -> "bytes/second",
//      "meanBytesWrittenRate" -> stats.meanBytesWrittenRate,
//      "meanBytesWrittenRateUnit" -> "bytes/second")
//  }
//
//  def generateValueUnitPair(
//    duration: DurationNanos,
//    timeUnit: TimeUnit = DefaultOutputDurationTimeUnit): JsValue = {
//
//    def parseTimeUnit(time: TimeUnit): JsString = time match {
//      case MICROSECONDS => JsString("µs")
//      case MILLISECONDS => JsString("ms")
//      case NANOSECONDS => JsString("ns")
//      case _ => JsString("s")
//    }
//
//    Json.obj("value" -> timeUnit.convert(duration, NANOSECONDS)) ++ Json.obj("unit" -> parseTimeUnit(timeUnit))
//  }
//}
