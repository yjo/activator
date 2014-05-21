//package console.handler
//
//import akka.actor.ActorPath
//import activator.analytics.data.{
//  ActorStats,
//  TimeRange,
//  Scope,
//  ErrorStats,
//  ErrorStatsMetrics,
//  DeviationDetails,
//  DeviationDetail,
//  Counts
//}
//import activator.analytics.data.TimeRangeType._
//import com.typesafe.trace.uuid.UUID
//import scala.util.Random
//import com.typesafe.trace._
//import scala.language.implicitConversions
//import scala.language.higherKinds
//
//object Generators {
//  implicit def liftGen[T](gen: => T): () => T = () => gen
//
//  def genActorScopes(paths: Set[ActorPath],
//    tags: Set[String],
//    hosts: Set[String],
//    dispatchers: Set[String],
//    systems: Set[String]): Set[Scope] =
//    for {
//      path <- paths
//      tag <- tags.map(x => Some(x): Option[String]).union(Set(None))
//      host <- hosts
//      dispatcher <- dispatchers
//      system <- systems
//    } yield Scope(Some(path.toString), tag, Some(host), Some(dispatcher), Some(system), None, None)
//
//  def genTimeRanges(start: Int, end: Int, incr: Int, rangeType: TimeRangeType): Seq[TimeRange] =
//    Range(start, end, incr).map(TimeRange.rangeFor(_, rangeType))
//
//  def genActorStats(scopes: Set[Scope], timeRanges: Seq[TimeRange])(body: (Int, Scope, TimeRange) => ActorStats): Seq[ActorStats] = {
//    var index: Int = 1
//    for {
//      scope <- scopes.toSeq
//      range <- timeRanges
//    } yield {
//      val r = body(index, scope, range)
//      index += 1
//      r
//    }
//  }
//
//  def genUUID(): UUID = new UUID(Random.nextLong(), Random.nextLong())
//
//  def choose[T](v: T, vs: T*): T =
//    Random.nextInt(vs.size + 1) match {
//      case 0 => v
//      case i => vs(i - 1)
//    }
//
//  def genMultiple[T](times: Int, gen: () => T): Seq[T] = (1 to times).map(_ => gen())
//
//  def genMaxMultiple[T](maxTimes: Int, gen: () => T): Seq[T] = genMultiple(Random.nextInt(maxTimes), gen)
//
//  def genContainer[M[_], T](times: Int, gen: () => T)(implicit conv: Seq[T] => M[T]): M[T] = conv(genMultiple(times, gen))
//
//  def genMaxContainer[M[_], T](maxTimes: Int, gen: () => T)(implicit conv: Seq[T] => M[T]): M[T] = conv(genMaxMultiple(maxTimes, gen))
//
//  def genChoose[T](gen: () => T, gens: (() => T)*): () => T =
//    Random.nextInt(gens.size + 1) match {
//      case 0 => gen
//      case i => gens(i - 1)
//    }
//
//  def genString(maxNameLength: Int = 10): String =
//    new String(Random.alphanumeric.take(Random.nextInt(maxNameLength) + 1).toArray)
//
//  def genActorPath(namePrefix: String = "", maxNameLength: Int = 10): ActorPath =
//    ActorPath.fromString(s"akka://user/$namePrefix${genString(maxNameLength)}")
//
//  def genOption[T](gen: () => T): Option[T] =
//    if (Random.nextBoolean()) Some(gen())
//    else None
//
//  def genPoint[T](v: T): () => T = () => v
//
//  def genActorInfo(actorPathGen: () => ActorPath = genActorPath(),
//    dispatcherGen: () => Option[String] = genOption(genString()),
//    remoteGen: () => Boolean = Random.nextBoolean(),
//    routerGen: () => Boolean = Random.nextBoolean(),
//    tagsGen: () => Set[String] = Set[String]()): ActorInfo =
//    ActorInfo(path = actorPathGen().toString, dispatcher = dispatcherGen(), remote = remoteGen(), router = routerGen(), tags = tagsGen())
//
//  def genActorRequested(info: ActorInfo = genActorInfo(), actor: ActorInfo = genActorInfo()): ActorRequested = ActorRequested(info, actor)
//  def genActorCreated(info: ActorInfo = genActorInfo()): ActorCreated = ActorCreated(info)
//  def genActorTold(info: ActorInfo = genActorInfo(), message: String = genString(), sender: Option[ActorInfo] = genOption(genActorInfo())): ActorTold = ActorTold(info, message, sender)
//  def genActorAutoReceived(info: ActorInfo = genActorInfo(), message: String = genString()): ActorAutoReceived = ActorAutoReceived(info, message)
//  def genActorAutoCompleted(info: ActorInfo = genActorInfo(), message: String = genString()): ActorAutoCompleted = ActorAutoCompleted(info, message)
//  def genActorReceived(info: ActorInfo = genActorInfo(), message: String = genString()): ActorReceived = ActorReceived(info, message)
//  def genActorCompleted(info: ActorInfo = genActorInfo(), message: String = genString()): ActorCompleted = ActorCompleted(info, message)
//  def genActorAsked(info: ActorInfo = genActorInfo(), message: String = genString()): ActorAsked = ActorAsked(info, message)
//  def genActorFailed(info: ActorInfo = genActorInfo(), reason: String = genString(), supervisor: ActorInfo = genActorInfo()): ActorFailed = ActorFailed(info, reason, supervisor)
//  def genTempActorCreated(info: ActorInfo = genActorInfo()): TempActorCreated = TempActorCreated(info)
//  def genTempActorStopped(info: ActorInfo = genActorInfo()): TempActorStopped = TempActorStopped(info)
//
//  def genMessageTraceAnnotation(info: ActorInfo = genActorInfo(),
//    message: String = genString(),
//    sender: Option[ActorInfo] = genOption(genActorInfo()),
//    supervisor: ActorInfo = genActorInfo(),
//    reason: String = genString(),
//    failThreshold: Double = 0.1): Seq[ActorAnnotation] =
//    Seq(genActorTold(info, message, sender), genActorReceived(info, message), if (Random.nextDouble() <= failThreshold) genActorFailed(info, reason, supervisor) else genActorCompleted(info, message))
//
//  def genNMessageTraceAnnotations(copies: Int,
//    info: ActorInfo = genActorInfo(),
//    messageGen: () => String = genString(),
//    senderGen: () => Option[ActorInfo] = () => genOption(genActorInfo()),
//    supervisor: ActorInfo = genActorInfo(),
//    reasonGen: () => String = genString(),
//    failThreshold: Double = 0.1): Seq[Seq[ActorAnnotation]] =
//    (1 to copies).map(_ => genMessageTraceAnnotation(info, messageGen(), senderGen(), supervisor, reasonGen(), failThreshold)).toSeq
//
//  def randomFlatten(in: Seq[Seq[ActorAnnotation]]): Seq[ActorAnnotation] = {
//    def flattenWith(choice: Int, current: Seq[Seq[ActorAnnotation]], accum: Seq[ActorAnnotation]): Seq[ActorAnnotation] = {
//      if (current.isEmpty) accum
//      else {
//        val c = current(choice)
//        val (h, t) = (c.head, c.tail)
//        val (prefix, suffix) = current.splitAt(choice)
//        val naccum = accum :+ h
//        val ncurrent = if (t.isEmpty) prefix ++ suffix.tail else (prefix :+ t) ++ suffix.tail
//        val ns = ncurrent.size
//        val nc = if (ns == 0) 0 else Random.nextInt(ns)
//        flattenWith(nc, ncurrent, naccum)
//      }
//    }
//    flattenWith(Random.nextInt(in.size), in, Seq.empty[ActorAnnotation])
//  }
//
//  def localUUIDGenGen(changeWithin: Int = 10): () => UUID = {
//    var remaining = Random.nextInt(changeWithin)
//    var uuid = new UUID()
//
//    { () =>
//      if (remaining == 0) {
//        uuid = new UUID()
//        remaining = Random.nextInt(changeWithin)
//      }
//      remaining -= 1
//      uuid
//    }
//  }
//
//  def genTraceEvents(annotations: Seq[Annotation],
//    trace: UUID = new UUID(),
//    localGen: () => UUID = localUUIDGenGen(),
//    sampled: Int = 1,
//    node: String = "node1",
//    host: String = "host1",
//    actorSystem: String = "system1",
//    startNanoTime: Long = System.nanoTime()): Seq[TraceEvent] =
//    annotations.foldLeft((UUID.nilUUID(), startNanoTime, Seq.empty[TraceEvent])) {
//      case ((parent, nanoTime, accum), v) =>
//        val id = new UUID()
//        val milliTime: Long = nanoTime / 1000000
//        val te = TraceEvent(v,
//          id,
//          trace,
//          localGen(),
//          parent,
//          sampled,
//          node,
//          host,
//          actorSystem,
//          milliTime,
//          nanoTime)
//        (id, nanoTime + Random.nextInt(200) + 20, accum :+ te)
//    }._3
//
//  def uniqueUUID(in: Set[UUID]): UUID = {
//    var test = new UUID()
//    while (in(test)) {
//      test = new UUID()
//    }
//    test
//  }
//
//  def genTimestampGen(start: Long = 0L, maxDelta: Int = 100): () => Long = {
//    var current = start
//
//    { () =>
//      current = current + Random.nextInt(maxDelta) + 1
//      current
//    }
//  }
//
//  def genCounts(errorsGen: () => Int = Random.nextInt(5),
//    warningsGen: () => Int = Random.nextInt(5),
//    deadLettersGen: () => Int = Random.nextInt(5),
//    unhandledMessagesGen: () => Int = Random.nextInt(5),
//    deadlocksGen: () => Int = Random.nextInt(5)): () => Counts =
//    () => Counts(errors = errorsGen(),
//      warnings = warningsGen(),
//      deadLetters = deadLettersGen(),
//      unhandledMessages = unhandledMessagesGen(),
//      deadlocks = deadlocksGen())
//
//  def genDeviationDetail(eventIdGen: () => UUID = genUUID(),
//    traceIdGen: () => UUID = genUUID(),
//    messageGen: () => String = genString(50),
//    timestampGen: () => Long = genTimestampGen()): () => DeviationDetail =
//    () => DeviationDetail(eventId = eventIdGen(),
//      traceId = traceIdGen(),
//      message = messageGen(),
//      timestamp = timestampGen())
//
//  def genDeviationDetails(errorsGen: () => Seq[DeviationDetail] = genMaxMultiple(5, genDeviationDetail()),
//    warningsGen: () => Seq[DeviationDetail] = genMaxMultiple(5, genDeviationDetail()),
//    deadLettersGen: () => Seq[DeviationDetail] = genMaxMultiple(5, genDeviationDetail()),
//    unhandledMessagesGen: () => Seq[DeviationDetail] = genMaxMultiple(5, genDeviationDetail()),
//    deadlockedThreadsGen: () => Seq[DeviationDetail] = genMaxMultiple(5, genDeviationDetail())): () => DeviationDetails =
//    () => DeviationDetails(errors = errorsGen().sortBy(_.timestamp).reverse.toList,
//      warnings = warningsGen().sortBy(_.timestamp).reverse.toList,
//      deadLetters = deadLettersGen().sortBy(_.timestamp).reverse.toList,
//      unhandledMessages = unhandledMessagesGen().sortBy(_.timestamp).reverse.toList,
//      deadlockedThreads = deadlockedThreadsGen().sortBy(_.timestamp).reverse.toList)
//
//  def genErrorStatsMetrics(countsGen: () => Counts = genCounts(),
//    deviationsGen: () => DeviationDetails = genDeviationDetails()): () => ErrorStatsMetrics =
//    () => ErrorStatsMetrics(counts = countsGen(), deviations = deviationsGen())
//
//  def genErrorStats(timeRangeGen: () => TimeRange,
//    nodeGen: () => Option[String] = genOption(genString()),
//    actorSystemGen: () => Option[String] = genOption(genString()),
//    errorMetricsGen: () => ErrorStatsMetrics = genErrorStatsMetrics(),
//    idGen: () => UUID = genUUID()): () => ErrorStats =
//    () => ErrorStats(timeRange = timeRangeGen(), node = nodeGen(), actorSystem = actorSystemGen(), metrics = errorMetricsGen(), id = idGen())
//}
