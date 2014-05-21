///**
// * Copyright (C) 2013 Typesafe <http://typesafe.com/>
// */
//package console
//package handler
//
//import akka.actor.{ ActorRef, Props }
//import activator.analytics.data._
//import console.handler.rest.DeviationsJsonBuilder
//import console.AnalyticsRepository
//
//case class ChunkRange(min: Int, max: Int)
//
//object DeviationsHandler {
//  def props(repository: AnalyticsRepository,
//    defaultLimit: Int,
//    builderProps: Props = DeviationsJsonBuilder.props()): Props =
//    Props(classOf[DeviationsHandler], builderProps, repository, defaultLimit)
//
//  case class DeviationsModuleInfo(scope: Scope,
//    time: TimeRange,
//    dataFrom: Option[Long],
//    chunkRange: Option[ChunkRange]) extends ModuleInformationBase {
//    def useActorStats: Boolean = scope.dispatcher.isDefined || scope.tag.isDefined || scope.path.isDefined
//  }
//}
//
//trait DeviationsHandlerBase extends RequestHandlerLike[DeviationsHandler.DeviationsModuleInfo] {
//  import DeviationsHandler._
//
//  def useDeviationsResult(sender: ActorRef, result: Either[Seq[ErrorStats], Seq[ActorStats]]): Unit
//
//  private def filterByTime(from: Option[Long], stats: ErrorStats): ErrorStats = {
//    val filtered = for {
//      f <- from
//    } yield stats.copy(
//      metrics = stats.metrics.copy(
//        deviations =
//          stats.metrics.deviations.filterByTime(f)))
//
//    val recounted = for {
//      f <- filtered
//    } yield f.copy(
//      metrics = f.metrics.copy(
//        counts = Counts(
//          errors = f.metrics.deviations.errors.size,
//          warnings = f.metrics.deviations.warnings.size,
//          deadLetters = f.metrics.deviations.deadLetters.size,
//          unhandledMessages = f.metrics.deviations.unhandledMessages.size,
//          deadlocks = f.metrics.deviations.deadlockedThreads.size)))
//
//    recounted.getOrElse(stats)
//  }
//
//  def onModuleInformation(sender: ActorRef, mi: DeviationsModuleInfo): Unit = {
//    if (mi.useActorStats) {
//      val stats = repository.actorStatsRepository.findWithinTimePeriod(mi.time, mi.scope)
//      val result: Seq[ActorStats] = mi.chunkRange match {
//        case None => Seq(ActorStats.concatenate(stats, mi.time, mi.scope))
//        case Some(ChunkRange(min, max)) => ActorStats.chunk(min, max, stats, mi.time, mi.scope)
//      }
//      useDeviationsResult(sender, Right(result))
//    } else {
//      val stats = repository.errorStatsRepository.findWithinTimePeriod(mi.time, mi.scope.node, mi.scope.actorSystem)
//      val result: Seq[ErrorStats] = mi.chunkRange match {
//        case None ⇒ Seq(filterByTime(mi.dataFrom, ErrorStats.concatenate(stats, mi.time, mi.scope.node, mi.scope.actorSystem)))
//        case Some(ChunkRange(min, max)) ⇒ ErrorStats.chunk(min, max, stats, mi.time, mi.scope.node, mi.scope.actorSystem) map { filterByTime(mi.dataFrom, _) }
//      }
//      useDeviationsResult(sender, Left(result))
//    }
//  }
//}
//
//class DeviationsHandler(builderProps: Props,
//  val repository: AnalyticsRepository,
//  val defaultLimit: Int) extends RequestHandler[DeviationsHandler.DeviationsModuleInfo] with DeviationsHandlerBase {
//  import console.handler.rest.DeviationsJsonBuilder._
//
//  val builder = context.actorOf(builderProps, "deviationsBuilder")
//
//  def useDeviationsResult(sender: ActorRef, result: Either[Seq[ErrorStats], Seq[ActorStats]]): Unit = {
//    builder ! DeviationsResult(sender, result)
//  }
//}
