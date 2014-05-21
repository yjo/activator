///**
// * Copyright (C) 2013 Typesafe <http://typesafe.com/>
// */
//package console.handler.rest
//
//import akka.actor.{ ActorRef, Props }
//import console.ClientController.Update
//import com.typesafe.trace.TraceEvent
//import play.api.libs.json.{ Json, JsObject, JsValue, JsArray, Writes, JsString, JsNull }
//import com.typesafe.trace.uuid.UUID
//import play.api.libs.json.Json.JsValueWrapper
//import activator.analytics.data.{ TimeRange, Scope, ActorStats }
//
//class DeviationJsonBuilder extends JsonBuilderActor {
//  import DeviationJsonBuilder._
//
//  def receive = {
//    case r: DeviationResult => r.receiver ! Update(createJson(r))
//  }
//}
//
//object DeviationJsonBuilder {
//  import com.typesafe.trace._
//  import JsonBuilder._, TraceEventJsonBuilder._, TraceTreeJsonBuilder._
//
//  def props(): Props = Props(classOf[DeviationJsonBuilder])
//
//  sealed trait DeviationResult {
//    def receiver: ActorRef
//  }
//  case class InvalidResult(receiver: ActorRef, eventID: UUID) extends DeviationResult
//  case class ValidResult(receiver: ActorRef, eventID: UUID, event: TraceEvent, traces: Seq[TraceEvent]) extends DeviationResult
//
//  def createJson(result: DeviationResult): JsObject =
//    Json.obj(
//      "type" -> "deviation",
//      "data" ->
//        (result match {
//          case InvalidResult(_, eventID) => createInvalidResultJson(eventID)
//          case ValidResult(_, eventID, event, traces) => createValidResultJson(eventID, event, traces)
//        }))
//
//  def createInvalidResultJson(eventId: UUID): JsValue = JsNull
//
//  def createValidResultJson(eventId: UUID, event: TraceEvent, traces: Seq[TraceEvent]): JsValue =
//    Json.obj(
//      "traceEvent" -> createTraceEventJson(event),
//      "traceTree" -> createTraceTreeJson(TraceTree(traces)))
//}
//
//object TraceEventJsonBuilder {
//  import com.typesafe.trace._
//  import JsonBuilder._
//
//  def createTraceEventsJson(traces: Seq[TraceEvent]): JsArray = {
//    new JsArray(traces.map(createTraceEventJson))
//  }
//
//  def createTraceEventJson(trace: TraceEvent): JsObject = {
//    val annotation: JsObject = trace.annotation match {
//      case RemotingLifecycle(_, _, _, _, _, _) | SystemShutdown(_) | SystemStarted(_) |
//        TopLevelActorCreated(_) | TopLevelActorRequested(_, _) => Json.obj()
//      case x: ActorAnnotation ⇒
//        Json.obj(
//          "actorInfo" -> createActorInfoJson(x.info)) ++
//          (x match {
//            case ActorRequested(_, actor) ⇒ Json.obj("actor" -> createActorInfoJson(actor))
//            case ActorReceived(_, message) ⇒ Json.obj("message" -> message)
//            case ActorTold(_, message, sender) ⇒ optJsonObj("sender", sender, createActorInfoJson) ++ Json.obj("message" -> message)
//            case ActorCompleted(_, message) ⇒ Json.obj("message" -> message)
//            case ActorFailed(_, reason, supervisor) ⇒
//              Json.obj(
//                "reason" -> reason,
//                "supervisor" -> createActorInfoJson(supervisor))
//            case ActorAutoReceived(_, message) ⇒ Json.obj("message" -> message)
//            case ActorAutoCompleted(_, message) ⇒ Json.obj("message" -> message)
//            case ActorAsked(_, message) ⇒ Json.obj("message" -> message)
//            case ActorCreated(_) | TempActorCreated(_) | TempActorStopped(_) => Json.obj()
//          })
//      case x: SysMsgAnnotation ⇒
//        val sysMsgType = x.message.getClass.getSimpleName.replace("SysMsg$", "").replace("SysMsg", "")
//        Json.obj(
//          "actorInfo" -> createActorInfoJson(x.info),
//          "sysMsgType" -> sysMsgType,
//          "message" -> createSysMsgJson(x.message))
//      case x: ActorSelectionAnnotation ⇒
//        Json.obj(
//          "actorSelectionInfo" -> createActorSelectionInfoJson(x.info)) ++
//          (x match {
//            case ActorSelectionTold(_, message, sender) ⇒
//              Json.obj("message" -> message) ++ optJsonObj("sender", sender, createActorInfoJson)
//            case ActorSelectionAsked(_, message) ⇒
//              Json.obj("message" -> message)
//          })
//      case x: FutureAnnotation ⇒
//        Json.obj("futureInfo" -> createFutureInfoJson(x.info)) ++
//          (x match {
//            case FutureScheduled(_, taskInfo) ⇒
//              Json.obj("taskInfo" -> createTaskInfoJson(taskInfo))
//            case FutureSucceeded(_, result, resultInfo) ⇒
//              Json.obj(
//                "result" -> result,
//                "resultInfo" -> createInfoJson(resultInfo))
//            case FutureFailed(_, exception) ⇒
//              Json.obj("cause" -> exception)
//            case FutureAwaitTimedOut(_, duration) ⇒
//              Json.obj("timeout" -> duration)
//            case FutureAwaited(_) | FutureCallbackAdded(_) |
//              FutureCallbackCompleted(_) | FutureCallbackStarted(_) | FutureCreated(_) => Json.obj()
//          })
//
//      case x: RunnableAnnotation ⇒ Json.obj("taskInfo" -> createTaskInfoJson(x.info))
//
//      case x: ScheduledAnnotation ⇒
//        Json.obj("taskInfo" -> createTaskInfoJson(x.info)) ++
//          (x match {
//            case ScheduledOnce(_, delay) ⇒ Json.obj("delay" -> delay)
//            case ScheduledCancelled(_) | ScheduledCompleted(_) | ScheduledStarted(_) => Json.obj()
//          })
//
//      case x: RemoteMessageAnnotation ⇒
//        Json.obj(
//          "message" -> x.message,
//          "actorInfo" -> createActorInfoJson(x.info)) ++
//          (x match {
//            case RemoteMessageSent(_, message, messageSize) ⇒
//              Json.obj("messageSize" -> messageSize)
//            case RemoteMessageReceived(_, message, messageSize) ⇒
//              Json.obj("messageSize" -> messageSize)
//            case RemoteMessageCompleted(_, _) => Json.obj()
//          })
//
//      case x: EventStreamAnnotation ⇒
//        Json.obj("message" -> x.message) ++
//          (x match {
//            case EventStreamDeadLetter(_, sender, recipient) ⇒
//              Json.obj(
//                "sender" -> createActorInfoJson(sender),
//                "recipient" -> createActorInfoJson(recipient))
//            case EventStreamUnhandledMessage(_, sender, recipient) ⇒
//              Json.obj(
//                "sender" -> createActorInfoJson(sender),
//                "recipient" -> createActorInfoJson(recipient))
//            case EventStreamError(_) | EventStreamWarning(_) => Json.obj()
//          })
//
//      case MarkerStarted(name) ⇒ Json.obj("name" -> name)
//      case MarkerEnded(name) ⇒ Json.obj("name" -> name)
//      case Marker(name, data) ⇒
//        Json.obj(
//          "name" -> name,
//          "data" -> data)
//
//      case GroupStarted(name) ⇒ Json.obj("name" -> name)
//      case GroupEnded(name) ⇒ Json.obj("name" -> name)
//
//      case RemoteStatus(statusType, serverNode, clientNode, cause) ⇒
//        Json.obj("statusType" -> statusType.toString) ++
//          optJson("serverNode", serverNode) ++
//          optJson("clientNode", clientNode) ++
//          optJson("cause", cause)
//
//      case status: DispatcherStatus ⇒
//        Json.obj(
//          "dispatcher" -> status.dispatcher,
//          "corePoolSize" -> status.metrics.corePoolSize,
//          "maximumPoolSize" -> status.metrics.maximumPoolSize,
//          "keepAliveTime" -> status.metrics.keepAliveTime,
//          "rejectedHandler" -> status.metrics.rejectedHandler,
//          "activeThreadCount" -> status.metrics.activeThreadCount,
//          "taskCount" -> status.metrics.taskCount,
//          "completedTaskCount" -> status.metrics.completedTaskCount,
//          "largestPoolSize" -> status.metrics.largestPoolSize,
//          "poolSize" -> status.metrics.poolSize,
//          "queueSize" -> status.metrics.queueSize)
//
//      case metrics: SystemMetrics ⇒
//        Json.obj(
//          "runningActors" -> metrics.runningActors,
//          "startTime" -> metrics.startTime,
//          "upTime" -> metrics.upTime,
//          "availableProcessors" -> metrics.availableProcessors,
//          "daemonThreadCount" -> metrics.daemonThreadCount,
//          "threadCount" -> metrics.threadCount,
//          "peakThreadCount" -> metrics.peakThreadCount,
//          "committedHeap" -> metrics.committedHeap,
//          "maxHeap" -> metrics.maxHeap,
//          "usedHeap" -> metrics.usedHeap,
//          "committedNonHeap" -> metrics.committedNonHeap,
//          "maxNonHeap" -> metrics.maxNonHeap,
//          "usedNonHeap" -> metrics.usedNonHeap,
//          "systemLoadAverage" -> metrics.systemLoadAverage)
//
//      case deadlockedThreads: DeadlockedThreads ⇒
//        Json.obj(
//          "message" -> deadlockedThreads.message,
//          "deadlocks" -> new JsArray(deadlockedThreads.deadlocks.map(new JsString(_)).toSeq))
//
//      case x: ActionAnnotation ⇒
//        import activator.analytics.rest.http.PlayRequestSummaryRepresentation._
//        x match {
//          case ActionResolved(resolutionInfo) ⇒ Json.obj("resolutionInfo" -> createResolvedInfoJson(resolutionInfo))
//          case ActionInvoked(invocationInfo) ⇒ Json.obj("invocationInfo" -> createInvocationInfoJson(invocationInfo))
//          case ActionResultGenerationStart ⇒ Json.obj()
//          case ActionResultGenerationEnd ⇒ Json.obj()
//          case ActionChunkedInputStart ⇒ Json.obj()
//          case ActionChunkedInputEnd ⇒ Json.obj()
//          case ActionChunkedResult(resultInfo) ⇒
//            Json.obj("httpResponseCode" -> resultInfo.httpResponseCode)
//          case ActionSimpleResult(resultInfo) ⇒
//            Json.obj("httpResponseCode" -> resultInfo.httpResponseCode)
//          case ActionAsyncResult ⇒ Json.obj()
//          case ActionRouteRequest(requestInfo, result) ⇒
//            Json.obj(
//              "requestInfo" -> createActionRequestInfoJson(requestInfo),
//              "result" -> result.toString)
//          case ActionError(requestInfo, message, stackTrace) ⇒
//            Json.obj(
//              "requestInfo" -> createActionRequestInfoJson(requestInfo),
//              "message" -> message,
//              "stackTrace" -> new JsArray(stackTrace.map(new JsString(_))))
//          case ActionHandlerNotFound(requestInfo) ⇒
//            Json.obj(
//              "requestInfo" -> createActionRequestInfoJson(requestInfo))
//          case ActionBadRequest(requestInfo, error) ⇒
//            Json.obj(
//              "requestInfo" -> createActionRequestInfoJson(requestInfo),
//              "error" -> error)
//        }
//
//      case x: NettyAnnotation ⇒
//        x match {
//          case NettyHttpReceivedStart ⇒ Json.obj()
//          case NettyHttpReceivedEnd ⇒ Json.obj()
//          case NettyPlayReceivedStart ⇒ Json.obj()
//          case NettyPlayReceivedEnd ⇒ Json.obj()
//          case NettyResponseHeader(size) ⇒
//            Json.obj("size" -> size)
//          case NettyResponseBody(size) ⇒
//            Json.obj("size" -> size)
//          case NettyWriteChunk(overhead, size) ⇒
//            Json.obj(
//              "overhead" -> overhead,
//              "size" -> size)
//          case NettyReadBytes(size) ⇒
//            Json.obj("size" -> size)
//        }
//
//      case x: IterateeAnnotation ⇒
//        Json.obj("info" -> createIterateeInfoJson(x.info)) ++
//          (x match {
//            case IterateeCreated(_) ⇒ Json.obj()
//            case IterateeFolded(_) ⇒ Json.obj()
//            case IterateeDone(_) ⇒ Json.obj()
//            case IterateeContinued(_, input: IterateeInput.Tag, nextInfo) ⇒
//              Json.obj("nextInfo" -> createIterateeInfoJson(nextInfo)) ++
//                ((input: @unchecked) match {
//                  case IterateeInput.El(value) ⇒
//                    Json.obj(
//                      "inputType" -> "El",
//                      "inputValue" -> value)
//                  case IterateeInput.Empty ⇒
//                    Json.obj("inputType" -> "Empty")
//                  case IterateeInput.EOF ⇒
//                    Json.obj("inputType" -> "EOF")
//                })
//            case IterateeError(_) ⇒ Json.obj()
//          })
//    }
//
//    val typeValue: String = {
//      val name = trace.annotation.getClass.getSimpleName
//      // case objects ends with $
//      if (name.endsWith("$")) name.dropRight(1) else name
//    }
//
//    (if (trace.parent != UUID.nilUUID()) Json.obj("parent" -> trace.parent.toString)
//    else Json.obj()) ++
//      Json.obj(
//        "id" -> trace.id.toString,
//        "trace" -> trace.trace.toString,
//        "sampled" -> trace.sampled,
//        "node" -> trace.node,
//        "actorSystem" -> trace.actorSystem,
//        "host" -> trace.host,
//        "timestamp" -> trace.timestamp,
//        "nanoTime" -> trace.nanoTime,
//        "type" -> typeValue,
//        "annotation" -> annotation)
//  }
//
//  def createActorInfoJson(info: ActorInfo): JsObject = {
//    Json.obj(
//      "actorPath" -> info.path,
//      "remote" -> info.remote,
//      "router" -> info.router,
//      "tags" -> new JsArray(info.tags.toSeq.map(new JsString(_)))) ++
//      info.dispatcher.map(d => Json.obj("dispatcher" -> d)).getOrElse(Json.obj())
//  }
//
//  def createIterateeInfoJson(info: IterateeInfo): JsObject = {
//    Json.obj("uuid" -> info.uuid.toString)
//  }
//
//  def createResolvedInfoJson(info: ActionResolvedInfo): JsObject = {
//    Json.obj(
//      "controller" -> info.controller,
//      "method" -> info.method,
//      "parameterTypes" -> new JsArray(info.parameterTypes.map(new JsString(_))),
//      "verb" -> info.verb,
//      "comments" -> info.comments,
//      "path" -> info.path)
//  }
//
//  def createInvocationInfoJson(info: ActionInvocationInfo): JsObject = {
//    Json.obj(
//      "controller" -> info.controller,
//      "method" -> info.method,
//      "pattern" -> info.pattern,
//      "id" -> info.id,
//      "uri" -> info.uri,
//      "path" -> info.path,
//      "httpMethod" -> info.httpMethod,
//      "version" -> info.version,
//      "remoteAddress" -> info.remoteAddress) ++
//      optJson("host", info.host) ++
//      optJson("domain", info.domain) ++
//      optJsonObj("session", info.session, (_: Map[String, String]).foldLeft(Json.obj()) { case (s, (key, value)) => s ++ Json.obj(key -> value) })
//  }
//
//  def createActionRequestInfoJson(requestInfo: ActionRequestInfo): JsObject = {
//    Json.obj(
//      "id" -> requestInfo.id,
//      "tags" -> requestInfo.tags.foldLeft(Json.obj()) { case (s, (k, v)) ⇒ s ++ Json.obj(k -> v) },
//      "uri" -> requestInfo.uri,
//      "path" -> requestInfo.path,
//      "method" -> requestInfo.method,
//      "version" -> requestInfo.version,
//      "queryString" -> requestInfo.queryString.foldLeft(Json.obj()) { case (s, (k, v)) ⇒ s ++ Json.obj(k -> new JsArray(v.map(new JsString(_)))) },
//      "headers" -> requestInfo.headers.foldLeft(Json.obj()) { case (s, (k, v)) ⇒ s ++ Json.obj(k -> new JsArray(v.map(new JsString(_)))) })
//  }
//
//  def createInfoJson(info: Info): JsObject = {
//    info match {
//      case i: ActorInfo ⇒ createActorInfoJson(i)
//      case i: ActorSelectionInfo ⇒ createActorSelectionInfoJson(i)
//      case i: FutureInfo ⇒ createFutureInfoJson(i)
//      case i: TaskInfo ⇒ createTaskInfoJson(i)
//      case i: IterateeInfo ⇒ createIterateeInfoJson(i)
//      case NoInfo => Json.obj()
//    }
//  }
//
//  def createActorSelectionInfoJson(info: ActorSelectionInfo): JsObject = {
//    Json.obj(
//      "anchorInfo" -> createActorInfoJson(info.anchor),
//      "selectionPath" -> info.path)
//  }
//
//  def createFutureInfoJson(info: FutureInfo): JsObject = {
//    Json.obj("uuid" -> info.uuid.toString)
//  }
//
//  def createTaskInfoJson(taskInfo: TaskInfo): JsObject = {
//    Json.obj(
//      "uuid" -> taskInfo.uuid.toString,
//      "dispatcher" -> taskInfo.dispatcher)
//  }
//
//  def createSysMsgJson(message: SysMsg): JsObject = {
//    message match {
//      case RecreateSysMsg(cause) ⇒
//        Json.obj("cause" -> cause)
//      case SuperviseSysMsg(child) ⇒
//        Json.obj("child" -> createActorInfoJson(child))
//      case ChildTerminatedSysMsg(child) ⇒
//        Json.obj("child" -> createActorInfoJson(child))
//      case LinkSysMsg(subject) ⇒
//        Json.obj("subject" -> createActorInfoJson(subject))
//      case UnlinkSysMsg(subject) ⇒
//        Json.obj("subject" -> createActorInfoJson(subject))
//      case WatchSysMsg(watchee, watcher) ⇒
//        Json.obj("watchee" -> createActorInfoJson(watchee))
//        Json.obj("watcher" -> createActorInfoJson(watcher))
//      case UnwatchSysMsg(watchee, watcher) ⇒
//        Json.obj("watchee" -> createActorInfoJson(watchee))
//        Json.obj("watcher" -> createActorInfoJson(watcher))
//      case FailedSysMsg(child, cause) ⇒
//        Json.obj(
//          "child" -> createActorInfoJson(child),
//          "cause" -> cause)
//      case DeathWatchSysMsg(actor, existenceConfirmed, addressTerminated) ⇒
//        Json.obj(
//          "watched" -> createActorInfoJson(actor),
//          "existenceConfirmed" -> existenceConfirmed,
//          "addressTerminated" -> addressTerminated)
//      case CreateSysMsg | NoMessageSysMsg | ResumeSysMsg | SuspendSysMsg | TerminateSysMsg => Json.obj()
//    }
//  }
//}
//
//object TraceTreeJsonBuilder {
//  import com.typesafe.trace._
//  import JsonBuilder._
//  import TraceEventJsonBuilder._
//
//  def createTraceTreeJson(traceTree: TraceTree): JsObject =
//    traceTree.root match {
//      case Some(node) => createTraceTreeNodeJson(node)
//      case None => Json.obj()
//    }
//
//  def createTraceTreeNodeJson(node: TraceTree.Node): JsObject =
//    Json.obj(
//      "event" -> createTraceEventJson(node.event),
//      "children" -> new JsArray(node.children.map(createTraceTreeNodeJson)))
//}
