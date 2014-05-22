package console.handler

import akka.actor.{ ActorRef, Props, ActorSystem, Actor, SupervisorStrategy }
import akka.testkit.{ TestKit, ImplicitSender }
import org.specs2.mutable.{ Specification, SpecificationLike }
import org.specs2.specification._

abstract class ActorsSpec(name: String) extends TestKit(ActorSystem(name)) with SpecificationLike with AfterExample with ImplicitSender {

  override def map(fs: => Fragments) = super.map(fs) ^ step(system.shutdown, global = true)

  def after = system.shutdown
}
