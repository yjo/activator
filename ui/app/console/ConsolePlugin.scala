///**
// * Copyright (C) 2013 Typesafe <http://typesafe.com/>
// */
//package console
//
//import play.api.{ Logger, Plugin, Application }
//import com.typesafe.config.{ ConfigFactory, Config }
//import akka.actor.{ PoisonPill, ActorRef, Props, ActorSystem }
//import scala.util.control.NonFatal
//import activator.analytics.analyzer.AnalyzerManager
//import com.typesafe.trace.ReceiveMain
//import scala.concurrent.duration._
//
//class ConsolePlugin(app: Application) extends Plugin {
//  private var env: ConsolePluginEnvironment = null
//  def config = env.config
//  def actorSystem = env.system
//  def clientHandlerActor = env.clientHandlerActor
//  def defaultPageLimit = config.getInt("activator.default-page-limit")
//
//  override def onStart(): Unit = {
//    require(env eq null)
//    env = ConsolePluginEnvironment(app.configuration.underlying)
//    // TODO : uncomment lines below when Activator runs on Play 2.3 and Scala 2.11
//    //ReceiveMain.main(Array())
//    //AnalyzerManager.create(config)
//  }
//
//  override def onStop(): Unit = {
//    try {
//      require(env ne null)
//      // TODO : uncomment lines below when Activator runs on Play 2.3 and Scala 2.11
//      //AnalyzerManager.delete()
//      //ReceiveMain.shutdownReceiver()
//      env.close()
//    } finally {
//      env = null
//    }
//  }
//}
//
//private case class ConsolePluginEnvironment(config: Config, system: ActorSystem, clientHandlerActor: ActorRef) extends java.io.Closeable {
//  override def close(): Unit = {
//    try {
//      clientHandlerActor ! PoisonPill
//      system.shutdown()
//      system.awaitTermination()
//    } catch {
//      case NonFatal(e) =>
//        Logger.error("Failed to close actor system: ${e.getClass.getName}: ${e.getMessage}")
//    }
//  }
//}
//
//private object ConsolePluginEnvironment {
//  def apply(config: Config): ConsolePluginEnvironment = {
//    val system = ActorSystem("ConsoleActorSystem")
//    val clientHandler = system.actorOf(ClientController.derivedProps(AnalyticsRepository.fromSingletonMemoryObjects,
//      config.getInt("activator.default-page-limit"),
//      Some(config.getLong("console.update-frequency").milliseconds)), "clientController")
//    config.checkValid(ConfigFactory.defaultReference, "activator")
//    ConsolePluginEnvironment(config, system, clientHandler)
//  }
//}
