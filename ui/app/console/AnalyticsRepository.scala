///**
// * Copyright (C) 2013 Typesafe <http://typesafe.com/>
// */
//package console
//
//import activator.analytics.repository._
//import com.typesafe.trace.store.{ LocalMemoryTraceRepository, MemoryTraceRepository }
//
//case class AnalyticsRepository(actorStatsRepository: ActorStatsRepository,
//  dispatcherTimeSeriesRepository: DispatcherTimeSeriesRepository,
//  errorStatsRepository: ErrorStatsRepository,
//  histogramSpanStatsRepository: HistogramSpanStatsRepository,
//  hostStatsRepository: HostStatsRepository,
//  mailboxTimeSeriesRepository: MailboxTimeSeriesRepository,
//  messageRateTimeSeriesRepository: MessageRateTimeSeriesRepository,
//  metadataStatsRepository: MetadataStatsRepository,
//  percentilesSpanStatsRepository: PercentilesSpanStatsRepository,
//  playRequestSummaryRepository: PlayRequestSummaryRepository,
//  playStatsRepository: PlayStatsRepository,
//  recordStatsRepository: RecordStatsRepository,
//  remoteStatusStatsRepository: RemoteStatusStatsRepository,
//  spanRepository: SpanRepository,
//  spanTimeSeriesRepository: SpanTimeSeriesRepository,
//  summarySpanStatsRepository: SummarySpanStatsRepository,
//  systemMetricsTimeSeriesRepository: SystemMetricsTimeSeriesRepository,
//  lifecycleRepository: RepositoryLifecycleHandler,
//  traceRepository: MemoryTraceRepository)
//
//object AnalyticsRepository {
//  def fromSingletonMemoryObjects: AnalyticsRepository =
//    AnalyticsRepository(
//      actorStatsRepository = LocalMemoryActorStatsRepository,
//      dispatcherTimeSeriesRepository = LocalMemoryDispatcherTimeSeriesRepository,
//      errorStatsRepository = LocalMemoryErrorStatsRepository,
//      histogramSpanStatsRepository = LocalMemoryHistogramSpanStatsRepository,
//      hostStatsRepository = LocalMemoryHostStatsRepository,
//      mailboxTimeSeriesRepository = LocalMemoryMailboxTimeSeriesRepository,
//      messageRateTimeSeriesRepository = LocalMemoryMessageRateTimeSeriesRepository,
//      metadataStatsRepository = LocalMemoryMetadataStatsRepository,
//      percentilesSpanStatsRepository = LocalMemoryPercentilesSpanStatsRepository,
//      playRequestSummaryRepository = LocalMemoryPlayRequestSummaryRepository,
//      playStatsRepository = LocalMemoryPlayStatsRepository,
//      recordStatsRepository = LocalMemoryRecordStatsRepository,
//      remoteStatusStatsRepository = LocalMemoryRemoteStatusStatsRepository,
//      spanRepository = LocalMemorySpanRepository,
//      spanTimeSeriesRepository = LocalMemorySpanTimeSeriesRepository,
//      summarySpanStatsRepository = LocalMemorySummarySpanStatsRepository,
//      systemMetricsTimeSeriesRepository = LocalMemorySystemMetricsRepository,
//      lifecycleRepository = LocalRepositoryLifecycleHandler,
//      traceRepository = LocalMemoryTraceRepository)
//
//  def freshMemoryObjects: AnalyticsRepository =
//    AnalyticsRepository(
//      actorStatsRepository = new MemoryActorStatsRepository,
//      dispatcherTimeSeriesRepository = new MemoryDispatcherTimeSeriesRepository,
//      errorStatsRepository = new MemoryErrorStatsRepository,
//      histogramSpanStatsRepository = new MemoryHistogramSpanStatsRepository,
//      hostStatsRepository = new MemoryHostStatsRepository,
//      mailboxTimeSeriesRepository = new MemoryMailboxTimeSeriesRepository,
//      messageRateTimeSeriesRepository = new MemoryMessageRateTimeSeriesRepository,
//      metadataStatsRepository = new MemoryMetadataStatsRepository,
//      percentilesSpanStatsRepository = new MemoryPercentilesSpanStatsRepository,
//      playRequestSummaryRepository = new MemoryPlayRequestSummaryRepository(20 * 60 * 1000),
//      playStatsRepository = new MemoryPlayStatsRepository,
//      recordStatsRepository = new MemoryRecordStatsRepository,
//      remoteStatusStatsRepository = new MemoryRemoteStatusStatsRepository,
//      spanRepository = new MemorySpanRepository,
//      spanTimeSeriesRepository = new MemorySpanTimeSeriesRepository,
//      summarySpanStatsRepository = new MemorySummarySpanStatsRepository,
//      systemMetricsTimeSeriesRepository = new MemorySystemMetricsTimeSeriesRepository,
//      lifecycleRepository = new RepositoryLifecycleHandler,
//      traceRepository = new MemoryTraceRepository())
//
//}
