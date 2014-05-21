///**
// * Copyright (C) 2013 Typesafe <http://typesafe.com/>
// */
//package console
//package handler
//
//import akka.actor.{ ActorRef, Props }
//import scala.concurrent._
//import ExecutionContext.Implicits.global
//import activator.analytics.data._
//import activator.analytics.data.MetadataStatsMetrics
//import console.handler.rest.OverviewJsonBuilder.OverviewResult
//import activator.analytics.data.MetadataStats
//import console.ScopeModifiers
//import activator.analytics.rest.http.SortingHelpers.SortDirection
//import console.handler.rest.OverviewJsonBuilder
//import console.AnalyticsRepository
//
//object OverviewHandler {
//  def props(repository: AnalyticsRepository,
//    defaultLimit: Int,
//    builderProps: Props = OverviewJsonBuilder.props()): Props =
//    Props(classOf[OverviewHandler], repository, builderProps, defaultLimit)
//
//  case class OverviewModuleInfo(scope: Scope,
//    modifiers: ScopeModifiers,
//    time: TimeRange,
//    pagingInformation: Option[PagingInformation],
//    sortOn: OverviewSort,
//    sortDirection: SortDirection,
//    dataFrom: Option[Long],
//    traceId: Option[String]) extends MultiValueModuleInformation[OverviewSort]
//
//  def mergeMetadata(spanStatMetadata: MetadataStats, metadataStats: MetadataStats, limit: Int): MetadataStats = {
//    val allPaths = spanStatMetadata.metrics.paths ++ metadataStats.metrics.paths
//    val limitedPaths = allPaths.toSeq.sortWith((a, b) ⇒ a < b).take(limit).toSet
//    new MetadataStats(
//      timeRange = metadataStats.timeRange,
//      scope = metadataStats.scope,
//      metrics = MetadataStatsMetrics(
//        spanTypes = spanStatMetadata.metrics.spanTypes,
//        paths = limitedPaths,
//        totalActorCount = Some(allPaths.size),
//        dispatchers = spanStatMetadata.metrics.dispatchers ++ metadataStats.metrics.dispatchers,
//        nodes = spanStatMetadata.metrics.nodes ++ metadataStats.metrics.nodes,
//        actorSystems = spanStatMetadata.metrics.actorSystems ++ metadataStats.metrics.actorSystems,
//        tags = spanStatMetadata.metrics.tags ++ metadataStats.metrics.tags,
//        playPatterns = metadataStats.metrics.playPatterns,
//        playControllers = metadataStats.metrics.playControllers))
//  }
//
//  def extractSortOn(in: Option[String]): OverviewSort = OverviewSorts.DefineMe
//}
//
//trait OverviewHandlerBase extends PagingRequestHandler[OverviewSort, OverviewHandler.OverviewModuleInfo] {
//  import OverviewHandler._
//
//  def useMetadataStats(sender: ActorRef, stats: MetadataStats, errorStats: ErrorStats, currentStorageTime: Long): Unit
//  def onModuleInformation(sender: ActorRef, mi: OverviewModuleInfo): Unit = withPagingDefaults(mi) { (offset, limit) =>
//    val metadataFuture = future { repository.metadataStatsRepository.findFiltered(mi.time, mi.scope, mi.modifiers.anonymous, mi.modifiers.temporary) }
//    val spanFuture = future { repository.summarySpanStatsRepository.findMetadata(mi.time, mi.scope, mi.modifiers.anonymous, mi.modifiers.temporary) }
//    val deviationFuture = future { repository.errorStatsRepository.findWithinTimePeriod(mi.time, mi.scope.node, mi.scope.actorSystem) }
//    val currentStorageTimeFuture = future { repository.lifecycleRepository.currentStorageTime }
//    for {
//      metadata <- metadataFuture
//      spans <- spanFuture
//      deviations <- deviationFuture
//      currentStorageTime <- currentStorageTimeFuture
//    } useMetadataStats(sender, mergeMetadata(spans, metadata, limit), ErrorStats.concatenate(deviations, mi.time, mi.scope.node, mi.scope.actorSystem), currentStorageTime)
//  }
//}
//
//class OverviewHandler(val repository: AnalyticsRepository,
//  builderProps: Props,
//  val defaultLimit: Int) extends OverviewHandlerBase {
//  val builder = context.actorOf(builderProps, "overviewBuilder")
//  def useMetadataStats(sender: ActorRef, stats: MetadataStats, errorStats: ErrorStats, currentStorageTime: Long): Unit = {
//    builder ! OverviewResult(receiver = sender,
//      metadata = stats,
//      deviations = errorStats,
//      currentStorageTime = currentStorageTime)
//  }
//
//}
//
//sealed trait OverviewSort
//object OverviewSorts {
//  case object DefineMe extends OverviewSort
//}
