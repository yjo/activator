/**
 * Copyright (C) 2014 Typesafe <http://typesafe.com/>
 */
package activator

import sbt.complete.Parser
import akka.actor.ActorSystem
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

trait ActivatorCliHelper {
  import ActivatorCliHelper._

  implicit val timeout = akka.util.Timeout(defaultDuration)

  /** Uses SBT complete library to read user input with a given auto-completing parser. */
  def readLine[U](parser: Parser[U], prompt: String = "> ", mask: Option[Char] = None): Option[U] = {
    val reader = new sbt.FullReader(None, parser)
    reader.readLine(prompt, mask) flatMap { line =>
      val parsed = Parser.parse(line, parser)
      parsed match {
        case Right(value) => Some(value)
        case Left(e) => None
      }
    }
  }
}

object ActivatorCliHelper {
  val system = ActorSystem("default")
  val defaultDuration = Duration(system.settings.config.getDuration("activator.timeout", MILLISECONDS), MILLISECONDS)
}
