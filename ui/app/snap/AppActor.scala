/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package snap

import akka.actor._
import akka.event.LoggingAdapter
import akka.pattern._
import com.typesafe.sbtrc._
import com.typesafe.sbtrc.launching.SbtProcessLauncher
import console.ClientController.HandleRequest
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicLong
import JsonHelper._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Json._
import scala.concurrent.duration._
import scala.util.{ Success, Failure }
import play.api.Play
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

sealed trait AppRequest

case class GetTaskActor(id: String, description: String, request: protocol.Request) extends AppRequest
case object GetWebSocketCreated extends AppRequest
case object CreateWebSocket extends AppRequest
case class NotifyWebSocket(json: JsObject) extends AppRequest
case object InitialTimeoutExpired extends AppRequest
case class ForceStopTask(id: String) extends AppRequest
case class UpdateSourceFiles(files: Set[File]) extends AppRequest
case class ProvisionSbtPool(instrumentation: InstrumentationTag, originalMessage: GetTaskActor, sender: ActorRef) extends AppRequest

sealed trait AppReply

case class TaskActorReply(ref: ActorRef) extends AppReply
case object WebSocketAlreadyUsed extends AppReply
case class WebSocketCreatedReply(created: Boolean) extends AppReply

object NewRelicRequest {
  val requestTag = "NewRelicRequest"
  val responseTag = "NewRelicResponse"

  sealed trait Request {
    def error(message: String): Response =
      ErrorResponse(message, this)
  }
  case object Provision extends Request {
    def response: Response = Provisioned
  }
  case object Available extends Request {
    def response(result: Boolean): Response = AvailableResponse(result, this)
  }
  case class EnableProject(key: String, appName: String) extends Request {
    def response: Response = ProjectEnabled(this)
  }
  case object IsProjectEnabled extends Request {
    def response(result: Boolean): Response = IsProjectEnabledResponse(result, this)
  }

  sealed trait Response {
    def request: Request
  }
  case object Provisioned extends Response {
    final val request: Request = Provision
  }
  case class ErrorResponse(message: String, request: Request) extends Response
  case class AvailableResponse(result: Boolean, request: Request) extends Response
  case class ProjectEnabled(request: Request) extends Response
  case class IsProjectEnabledResponse(result: Boolean, request: Request) extends Response

  def extractTypeOnly[T](typeName: String, value: T): Reads[T] =
    extractTagged("type", typeName)(Reads[T](_ => JsSuccess(value)))

  def extractType[T](typeName: String)(reads: Reads[T]): Reads[T] =
    extractTagged("type", typeName)(reads)

  implicit val provisionReads: Reads[Provision.type] =
    extractRequest[Provision.type](requestTag)(extractTypeOnly("provision", Provision))

  implicit val isProjectEnabledReads: Reads[IsProjectEnabled.type] =
    extractRequest[IsProjectEnabled.type](requestTag)(extractTypeOnly("isProjectEnabled", IsProjectEnabled))

  implicit val provisionWrites: Writes[Provision.type] =
    emitRequest(requestTag)(_ => Json.obj("type" -> "provision"))

  implicit val isProjectEnabledWrites: Writes[IsProjectEnabled.type] =
    emitRequest(requestTag)(_ => Json.obj("type" -> "isProjectEnabled"))

  implicit val availableReads: Reads[Available.type] =
    extractRequest[Available.type](requestTag)(extractTypeOnly("available", Available))

  implicit val availableWrites: Writes[Available.type] =
    emitRequest(requestTag)(_ => Json.obj("type" -> "available"))

  implicit val enableProjectReads: Reads[EnableProject] =
    extractRequest[EnableProject](requestTag)(extractType("enable")(((__ \ "key").read[String] and
      (__ \ "name").read[String])(EnableProject.apply _)))

  implicit val enableProjectWrites: Writes[EnableProject] =
    emitRequest(requestTag)(in => Json.obj("type" -> "enable",
      "key" -> in.key,
      "name" -> in.appName))

  implicit val newRelicRequestReads: Reads[Request] = {
    val pr = provisionReads.asInstanceOf[Reads[Request]]
    val ar = availableReads.asInstanceOf[Reads[Request]]
    val epr = enableProjectReads.asInstanceOf[Reads[Request]]
    val iper = isProjectEnabledReads.asInstanceOf[Reads[Request]]
    extractRequest[Request](requestTag)(pr.orElse(ar).orElse(epr).orElse(iper))
  }

  implicit val provisionedWrites: Writes[Provisioned.type] =
    emitResponse(responseTag)(in => Json.obj("type" -> "provisioned",
      "request" -> in.request))

  implicit val isProjectEnabledResponseWrites: Writes[IsProjectEnabledResponse] =
    emitResponse(responseTag)(in => Json.obj("type" -> "isProjectEnabledResponse",
      "result" -> in.result,
      "request" -> in.request))

  implicit val availableResponseWrites: Writes[AvailableResponse] =
    emitResponse(responseTag)(in => Json.obj("type" -> "availableResponse",
      "result" -> in.result,
      "request" -> in.request))

  implicit val projectEnabledWrites: Writes[ProjectEnabled] =
    emitResponse(responseTag)(in => Json.obj("type" -> "projectEnabled",
      "request" -> in.request))

  implicit val errorResponseWrites: Writes[ErrorResponse] =
    emitResponse(responseTag)(in => Json.obj("type" -> "error",
      "message" -> in.message,
      "request" -> in.request))

  implicit val newRelicRequestWrites: Writes[Request] =
    Writes {
      case x: EnableProject => enableProjectWrites.writes(x)
      case x @ IsProjectEnabled => isProjectEnabledWrites.writes(x)
      case x @ Provision => provisionWrites.writes(x)
      case x @ Available => availableWrites.writes(x)
    }

  implicit val newRelicResponseWrites: Writes[Response] =
    Writes {
      case x @ Provisioned => provisionedWrites.writes(x)
      case x: IsProjectEnabledResponse => isProjectEnabledResponseWrites.writes(x)
      case x: AvailableResponse => availableResponseWrites.writes(x)
      case x: ProjectEnabled => projectEnabledWrites.writes(x)
      case x: ErrorResponse => errorResponseWrites.writes(x)
    }

  def unapply(in: JsValue): Option[Request] = Json.fromJson[Request](in).asOpt
}

case class InspectRequest(json: JsValue)
object InspectRequest {
  val tag = "InspectRequest"

  implicit val inspectRequestReads: Reads[InspectRequest] =
    extractRequest[InspectRequest](tag)((__ \ "location").read[JsValue].map(InspectRequest.apply))

  implicit val inspectRequestWrites: Writes[InspectRequest] =
    emitRequest(tag)(in => obj("location" -> in.json))

  def unapply(in: JsValue): Option[InspectRequest] = Json.fromJson[InspectRequest](in).asOpt
}

object AppActor {
  final val runTasks: Set[String] = Set(
    protocol.TaskNames.run,
    protocol.TaskNames.runMain,
    protocol.TaskNames.runEcho,
    protocol.TaskNames.runMainEcho)

  final val instrumentedRun: Map[String, String] = Map(
    protocol.TaskNames.run -> protocol.TaskNames.run,
    protocol.TaskNames.runMain -> protocol.TaskNames.runMain,
    protocol.TaskNames.runEcho -> protocol.TaskNames.run,
    protocol.TaskNames.runMainEcho -> protocol.TaskNames.runMain)

  def isRunRequest(request: protocol.Request): Boolean = request match {
    case protocol.GenericRequest(_, command, _) => runTasks(command)
    case _ => false
  }

  def getRunInstrumentation(request: protocol.Request): InstrumentationTag = request match {
    case protocol.GenericRequest(_, command, params) if runTasks(command) =>
      params.get("instrumentation").asInstanceOf[Option[String]].map(Instrumentations.validate).getOrElse(Instrumentations.InspectTag)
    case _ => throw new RuntimeException(s"Cannot get instrumentation from a non-run request: $request")
  }

  def instrumentedRequest(request: protocol.Request): protocol.Request = request match {
    case r: protocol.GenericRequest =>
      if (isRunRequest(r)) r.copy(name = instrumentedRun(r.name))
      else r
    case r => r
  }
}

class AppActor(val config: AppConfig, val sbtProcessLauncher: SbtProcessLauncher) extends Actor with ActorLogging {
  import AppActor._

  AppManager.registerKeepAlive(self)

  def location = config.location

  val poolCounter = new AtomicLong(0)

  val uninstrumentedChildFactory = new DefaultSbtProcessFactory(location, sbtProcessLauncher)
  val uninstrumentedSbts = context.actorOf(Props(new ChildPool(uninstrumentedChildFactory)), name = "sbt-pool")

  private var instrumentedSbtPools: Map[InstrumentationTag, ActorRef] = Map.empty[InstrumentationTag, ActorRef]

  def addInstrumentedSbtPool(tag: InstrumentationTag, factory: SbtProcessFactory): Unit = {
    tag match {
      case Instrumentations.InspectTag =>
      case i =>
        instrumentedSbtPools.get(i).foreach(_ ! PoisonPill)
        instrumentedSbtPools += (i -> context.actorOf(Props(new ChildPool(factory)), name = s"sbt-pool-${i.name}-${poolCounter.getAndIncrement()}"))
    }
  }

  def getSbtPoolFor(tag: InstrumentationTag): Option[ActorRef] = tag match {
    case Instrumentations.InspectTag => Some(uninstrumentedSbts)
    case i => instrumentedSbtPools.get(i)
  }

  val newRelicActor: ActorRef = context.actorOf(monitor.NewRelic.props(NewRelic.fromConfig(Play.current.configuration.underlying), defaultContext))
  val socket = context.actorOf(Props(new AppSocketActor(newRelicActor)), name = "socket")

  val projectWatcher = context.actorOf(Props(new ProjectWatcher(location, newSourcesSocket = socket, sbtPool = uninstrumentedSbts)),
    name = "projectWatcher")

  var webSocketCreated = false

  var tasks = Map.empty[String, ActorRef]

  context.watch(uninstrumentedSbts)
  context.watch(socket)
  context.watch(projectWatcher)

  // we can stay alive due to socket connection (and then die with the socket)
  // or else we just die after being around a short time
  context.system.scheduler.scheduleOnce(2.minutes, self, InitialTimeoutExpired)

  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  override def receive = {
    case Terminated(ref) =>
      if (ref == uninstrumentedSbts || instrumentedSbtPools.values.exists(_ == ref)) {
        log.info(s"sbt pool terminated, killing AppActor ${self.path.name}")
        self ! PoisonPill
      } else if (ref == socket) {
        log.info(s"socket terminated, killing AppActor ${self.path.name}")
        self ! PoisonPill
      } else if (ref == projectWatcher) {
        log.info(s"projectWatcher terminated, killing AppActor ${self.path.name}")
        self ! PoisonPill
      } else {
        tasks.find { kv => kv._2 == ref } match {
          case Some((taskId, task)) =>
            log.debug("forgetting terminated task {} {}", taskId, task)
            tasks -= taskId
          case None =>
            log.warning("other actor terminated (why are we watching it?) {}", ref)
        }
      }

    case req: AppRequest => req match {
      case m @ GetTaskActor(taskId, description, request) if isRunRequest(request) =>
        val instrumentation = getRunInstrumentation(request)
        getSbtPoolFor(instrumentation) match {
          case None =>
            self ! ProvisionSbtPool(instrumentation, m, sender)
          case Some(pool) =>
            val task = context.actorOf(Props(new ChildTaskActor(taskId, description, pool)),
              name = "task-" + URLEncoder.encode(taskId, "UTF-8"))
            tasks += (taskId -> task)
            context.watch(task)
            log.debug("created task {} {}", taskId, task)
            sender ! TaskActorReply(task)
        }
      case ProvisionSbtPool(instrumentation, originalMessage, originalSender) =>
        getSbtPoolFor(instrumentation) match {
          case Some(_) =>
            self.tell(originalMessage, originalSender)
          case None =>
            instrumentation match {
              case Instrumentations.InspectTag =>
              case Instrumentations.NewRelicTag =>
                val realitiveToRoot = FileHelper.relativeTo(config.location)_
                val nrConfigFile = realitiveToRoot("conf/newrelic.yml")
                val nrJar = realitiveToRoot("lib/newrelic.jar")
                val inst = NewRelic(nrConfigFile, nrJar)
                val processFactory = new DefaultSbtProcessFactory(location, sbtProcessLauncher, inst.jvmArgs)
                addInstrumentedSbtPool(Instrumentations.NewRelicTag, processFactory)
                self.tell(originalMessage, originalSender)
            }
        }
      case GetTaskActor(taskId, description, _) =>
        val pool = uninstrumentedSbts
        val task = context.actorOf(Props(new ChildTaskActor(taskId, description, pool)),
          name = "task-" + URLEncoder.encode(taskId, "UTF-8"))
        tasks += (taskId -> task)
        context.watch(task)
        log.debug("created task {} {}", taskId, task)
        sender ! TaskActorReply(task)
      case GetWebSocketCreated =>
        sender ! WebSocketCreatedReply(webSocketCreated)
      case CreateWebSocket =>
        log.debug("got CreateWebSocket")
        if (webSocketCreated) {
          log.warning("Attempt to create websocket for app a second time {}", config.id)
          sender ! WebSocketAlreadyUsed
        } else {
          webSocketCreated = true
          socket.tell(GetWebSocket, sender)
        }
      case notify: NotifyWebSocket =>
        if (validateEvent(notify.json)) {
          socket.forward(notify)
        } else {
          log.error("Attempt to send invalid event {}", notify.json)
        }
      case InitialTimeoutExpired =>
        if (!webSocketCreated) {
          log.warning("Nobody every connected to {}, killing it", config.id)
          self ! PoisonPill
        }
      case ForceStopTask(id) =>
        tasks.get(id).foreach { ref =>
          log.debug("ForceStopTask for {} sending stop to {}", id, ref)
          ref ! ForceStop
        }
      case UpdateSourceFiles(files) =>
        projectWatcher ! SetSourceFilesRequest(files)
    }
  }

  private def validateEvent(json: JsObject): Boolean = {
    // we need either a toplevel "type" or a toplevel "taskId"
    // and then a nested "event" with a "type"
    val hasType = json \ "type" match {
      case JsString(t) => true
      case _ => false
    }
    val hasTaskId = json \ "taskId" match {
      case JsString(t) =>
        json \ "event" \ "type" match {
          case JsString(t) => true
          case _ => false
        }
      case _ => false
    }
    hasType || hasTaskId;
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    log.debug(s"preRestart, ${reason.getClass.getName}: ${reason.getMessage}, on $message")
  }

  override def postStop(): Unit = {
    log.debug("postStop")
  }

  // this actor corresponds to one protocol.Request, and any
  // protocol.Event that are associated with said request.
  // This is spawned from ChildTaskActor for each request.
  class ChildRequestActor(val requestor: ActorRef, val sbt: ActorRef, val request: protocol.Request) extends Actor with ActorLogging {
    sbt ! request

    override def receive = {
      case response: protocol.Response =>
        requestor.forward(response)
        // Response is supposed to arrive at the end,
        // after all Event
        log.debug("request responded to, request actor self-destructing")
        self ! PoisonPill
      case event: protocol.Event =>
        requestor.forward(event)
    }
  }

  private sealed trait ChildTaskRequest
  private case object ForceStop extends ChildTaskRequest

  // this actor's lifetime corresponds to one sequence of interactions with
  // an sbt instance obtained from the sbt pool.
  // It gets the pool from the app; reserves an sbt in the pool; and
  // forwards any messages you like to that pool.
  class ChildTaskActor(val taskId: String, val taskDescription: String, val pool: ActorRef) extends Actor {

    val reservation = SbtReservation(id = taskId, taskName = taskDescription)

    var requestSerial = 0
    def nextRequestName() = {
      requestSerial += 1
      "subtask-" + requestSerial
    }

    pool ! RequestAnSbt(reservation)

    private def handleRequest(requestor: ActorRef, sbt: ActorRef, request: protocol.Request) = {
      context.actorOf(Props(new ChildRequestActor(requestor = requestor,
        sbt = sbt, request = request)), name = nextRequestName())
    }

    private def errorOnStopped(requestor: ActorRef, request: protocol.Request) = {
      requestor ! protocol.ErrorResponse(s"Task has been stopped (task ${reservation.id} request ${request})")
    }

    private def handleTerminated(ref: ActorRef, sbtOption: Option[ActorRef]): Unit = {
      if (Some(ref) == sbtOption) {
        log.debug("sbt actor died, task actor self-destructing")
        self ! PoisonPill // our sbt died
      }
    }

    override def receive = gettingReservation(Nil)

    private def gettingReservation(requestQueue: List[(ActorRef, protocol.Request)]): Receive = {
      case req: ChildTaskRequest => req match {
        case ForceStop =>
          pool ! ForceStopAnSbt(reservation.id) // drops our reservation
          requestQueue.reverse.foreach(tuple => errorOnStopped(tuple._1, tuple._2))
          context.become(forceStopped(None))
      }
      case req: protocol.Request =>
        context.become(gettingReservation((sender, req) :: requestQueue))
      case SbtGranted(filled) =>
        val sbt = filled.sbt.getOrElse(throw new RuntimeException("we were granted a reservation with no sbt"))
        // send the queue
        requestQueue.reverse.foreach(tuple => handleRequest(tuple._1, sbt, tuple._2))

        // monitor sbt death
        context.watch(sbt)
        // now enter have-sbt mode
        context.become(haveSbt(sbt))

      // when we die, the reservation should be auto-released by ChildPool
    }

    private def haveSbt(sbt: ActorRef): Receive = {
      case req: protocol.Request => handleRequest(sender, sbt, req)
      case ForceStop => {
        pool ! ForceStopAnSbt(reservation.id)
        context.become(forceStopped(Some(sbt)))
      }
      case Terminated(ref) => handleTerminated(ref, Some(sbt))
    }

    private def forceStopped(sbtOption: Option[ActorRef]): Receive = {
      case req: protocol.Request => errorOnStopped(sender, req)
      case Terminated(ref) => handleTerminated(ref, sbtOption)
      case SbtGranted(filled) =>
        pool ! ReleaseAnSbt(reservation.id)
    }
  }

  class ProvisioningSinkUnderlying(log: LoggingAdapter) {
    import monitor.Provisioning._
    def onMessage(status: Status, sender: ActorRef, self: ActorRef, context: ActorContext): Unit = status match {
      case ProvisioningError(message, exception) =>
        log.error(exception, message)
        context stop self
      case Downloading(url) =>
        log.info(s"Downloading $url")
      case Progress(Left(value)) =>
        log.info(s"... progress: $value bytes downloaded")
      case Progress(Right(value)) =>
        log.info(s"... progress: $value% complete")
      case DownloadComplete(url) =>
        log.info(s"Downloaded $url")
      case Validating =>
        log.info("... validating")
      case Extracting =>
        log.info("... extracting")
      case Complete =>
        log.info("Provisioning complete")
        context stop self
    }
  }

  class ProvisioningSink(underlyingBuilder: LoggingAdapter => ProvisioningSinkUnderlying) extends Actor with ActorLogging {
    val underlying = underlyingBuilder(log)
    import monitor.Provisioning._
    override def receive: Receive = {
      case x: Status => underlying.onMessage(x, sender, self, context)
    }
  }

  class AppSocketActor(newRelicActor: ActorRef) extends WebSocketActor[JsValue] with ActorLogging {
<<<<<<< HEAD

=======
>>>>>>> Basic support for using New Relic
    import WebSocketActor.timeout

    def askNewRelic[T <: monitor.NewRelic.Response](msg: monitor.NewRelic.Request, omsg: NewRelicRequest.Request, onFailure: Throwable => String)(body: T => Unit)(implicit tag: ClassTag[T]): Unit = {
      newRelicActor.ask(msg).mapTo[monitor.NewRelic.Response].onComplete {
        case Success(r: monitor.NewRelic.ErrorResponse) => produce(toJson(omsg.error(r.message)))
        case Success(`tag`(r)) => body(r)
        case Success(r: monitor.NewRelic.Response) =>
          log.error(s"Unexpected response from request: $msg got: $r expected: ${tag.toString()}")
        case Failure(f) =>
          val errorMsg = onFailure(f)
          log.error(f, errorMsg)
          produce(toJson(omsg.error(errorMsg)))
      }
    }

    def handleNewRelicRequest(in: NewRelicRequest.Request): Unit = {
      import monitor.NewRelic._
      in match {
        case x @ NewRelicRequest.Provision =>
          val sink = context.actorOf(Props(new ProvisioningSink(log => new ProvisioningSinkUnderlying(log))))
          askNewRelic[Provisioned](monitor.NewRelic.Provision(sink), x,
            f => s"Failed to provision New Relic: ${f.getMessage}")(_ => produce(toJson(x.response)))
        case x @ NewRelicRequest.Available =>
          askNewRelic[AvailableResponse](monitor.NewRelic.Available, x,
            f => s"Failed New Relic availability check: ${f.getMessage}")(r => produce(toJson(x.response(r.result))))
        case x @ NewRelicRequest.EnableProject(key, name) =>
          askNewRelic[ProjectEnabled](monitor.NewRelic.EnableProject(config.location, key, name), x,
            f => s"Failed to enable project[${config.location}] for New Relic: ${f.getMessage}")(_ => produce(toJson(x.response)))
        case x @ NewRelicRequest.IsProjectEnabled =>
          askNewRelic[IsProjectEnabledResult](monitor.NewRelic.IsProjectEnabled(config.location), x,
            f => s"Failed check if New Relic enabled: ${f.getMessage}")(r => produce(toJson(x.response(r.result))))
      }
    }

    override def onMessage(json: JsValue): Unit = {
      json match {
<<<<<<< HEAD
        case NewRelicRequest(m) => handleNewRelicRequest(m)
=======
        case NewRelicRequest(m) => m match {
          case x @ NewRelicRequest.Provision =>
            val sink = context.actorOf(Props(new ProvisioningSink(log => new ProvisioningSinkUnderlying(log))))
            newRelicActor.ask(monitor.NewRelic.Provision(sink)).onComplete {
              case Success(r: monitor.NewRelic.ErrorResponse) => produce(toJson(x.error(r.message)))
              case Success(r: monitor.NewRelic.Provisioned) => produce(toJson(x.response))
              case Failure(f) =>
                log.error(f, s"Failed to provision New Relic: ${f.getMessage}")
                produce(toJson(x.error(s"Failed to provision New Relic: ${f.getMessage}")))
            }
          case x @ NewRelicRequest.Available =>
            newRelicActor.ask(monitor.NewRelic.Available).onComplete {
              case Success(r: monitor.NewRelic.ErrorResponse) => produce(toJson(x.error(r.message)))
              case Success(r: monitor.NewRelic.AvailableResponse) => produce(toJson(x.response(r.result)))
              case Failure(f) =>
                log.error(f, s"Failed New Relic availability check: ${f.getMessage}")
                produce(toJson(x.error(s"Failed New Relic availability check: ${f.getMessage}")))
            }
          case x @ NewRelicRequest.EnableProject(key, name) =>
            newRelicActor.ask(monitor.NewRelic.EnableProject(config.location, key, name)).onComplete {
              case Success(r: monitor.NewRelic.ErrorResponse) => produce(toJson(x.error(r.message)))
              case Success(r: monitor.NewRelic.ProjectEnabled) => produce(toJson(x.response))
              case Failure(f) =>
                log.error(f, s"Failed to provision New Relic: ${f.getMessage}")
                produce(toJson(x.error(s"Failed to provision New Relic: ${f.getMessage}")))
            }
          case x @ NewRelicRequest.IsProjectEnabled =>
            newRelicActor.ask(monitor.NewRelic.IsProjectEnabled(config.location)).onComplete {
              case Success(r: monitor.NewRelic.ErrorResponse) => produce(toJson(x.error(r.message)))
              case Success(r: monitor.NewRelic.IsProjectEnabledResult) => produce(toJson(x.response(r.result)))
              case Failure(f) =>
                log.error(f, s"Failed check if New Relic enabled: ${f.getMessage}")
                produce(toJson(x.error(s"Failed check if New Relic enabled: ${f.getMessage}")))
            }

        }
>>>>>>> Basic support for using New Relic
        case InspectRequest(m) => for (cActor <- consoleActor) cActor ! HandleRequest(json)
        case WebSocketActor.Ping(ping) => produce(WebSocketActor.Pong(ping.cookie))
        case _ => log.info("unhandled message on web socket: {}", json)
      }
    }

    override def subReceive: Receive = {
      case NotifyWebSocket(json) =>
        log.debug("sending message on web socket: {}", json)
        produce(json)
    }

    override def postStop(): Unit = {
      log.debug("postStop")
    }
  }
}
