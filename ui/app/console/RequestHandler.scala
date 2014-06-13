/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package console

import akka.actor.{ ActorRef, ActorLogging, Actor }

trait RequestHandlerLike[S <: ModuleInformationBase] {
  def repository: AnalyticsRepository
  def onModuleInformation(sender: ActorRef, mi: S): Unit
}

trait RequestHandler[S <: ModuleInformationBase] extends Actor with ActorLogging with RequestHandlerLike[S] {
  def receive = {
    case mi: S @unchecked => onModuleInformation(sender, mi)
  }
}

trait PagingRequestHandlerLike[S, M <: MultiValueModuleInformation[S]] extends RequestHandlerLike[M] with WithPaging[M]

trait PagingRequestHandler[S, M <: MultiValueModuleInformation[S]] extends PagingRequestHandlerLike[S, M] with RequestHandler[M]
