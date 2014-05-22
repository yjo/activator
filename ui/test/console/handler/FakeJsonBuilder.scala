package console.handler

import akka.actor.{ ActorRef, Props, ActorSystem, Actor, SupervisorStrategy }

object FakeJsonBuilder {
  def props(probe: ActorRef): Props =
    Props(classOf[FakeJsonBuilder], probe)
}

class FakeJsonBuilder(probe: ActorRef) extends Actor {
  def receive: Receive = {
    case msg =>
      probe.tell(msg, sender)
  }
}
