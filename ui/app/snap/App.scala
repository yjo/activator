/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package snap

import java.util.UUID
import akka.actor._
import java.util.concurrent.atomic.AtomicInteger
import activator.properties.ActivatorProperties
import java.net.URLEncoder

final case class SocketId(appId: String, socketId: UUID)

class App(val id: SocketId, val config: AppConfig, val system: ActorSystem) extends ActorWrapper {
  require(config.id == id.appId)

  val appInstance = App.nextInstanceId.getAndIncrement()
  override def toString = s"App(${config.id}@$appInstance})"
  val actorName = "app-" + URLEncoder.encode(config.id, "UTF-8") + "-" + appInstance

  val actor = system.actorOf(Props(new AppActor(config)),
    name = actorName)

  system.actorOf(Props(new ActorWatcher(actor, this)), "app-actor-watcher-" + appInstance)

}

object App {
  val nextInstanceId = new AtomicInteger(1)
}
