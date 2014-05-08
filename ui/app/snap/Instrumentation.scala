/**
 * Copyright (C) 2014 Typesafe <http://typesafe.com/>
 */
package snap

import java.io._
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
import com.typesafe.config.{ Config => TSConfig }
import activator.properties.ActivatorProperties

sealed abstract class InstrumentationTag(val name: String)

sealed abstract class Instrumentation(val tag: InstrumentationTag) {
  def name: String = tag.name
  def jvmArgs: Seq[String]
}

case object Inspect extends Instrumentation(Instrumentations.InspectTag) {
  def jvmArgs: Seq[String] = Seq.empty[String]
}

case class NewRelic(configFile: File, agentJar: File, environment: String = "development") extends Instrumentation(Instrumentations.NewRelicTag) {
  def jvmArgs: Seq[String] = Seq(
    s"-javaagent:${agentJar.getPath}",
    s"-Dnewrelic.config.file=${configFile.getPath}",
    s"-Dnewrelic.environment=$environment")
}

object NewRelic {
  sealed abstract class CheckResult(val message: String)
  case object MissingConfigFile extends CheckResult("Missing configuration file")
  case object MissingInstrumentationJar extends CheckResult("Missing instrumentation jar")

  final val versionRegex = "\\{version\\}".r

  def fromConfig(in: TSConfig): Config = {
    import Instrumentations.withMonitoringConfig
    withMonitoringConfig(in) { configRoot =>
      val config = configRoot.getConfig("new-relic")
      Config(downloadUrlTemplate = config.getString("download-template"),
        version = config.getString("version"),
        sha = config.getString("checksum"),
        timeout = Timeout(config.getMilliseconds("timeout").intValue.millis),
        extractRootTemplate = config.getString("extract-root-template"))
    }
  }

  val libFiles = Seq("newrelic.jar")
  val newRelicConfigFile = "newrelic.yml"

  def provisionNewRelic(source: File, destination: File, key: String, appName: String): Unit = {
    val destRelative = FileHelper.relativeTo(destination)_
    val sourceRelative = FileHelper.relativeTo(FileHelper.relativeTo(source)("newrelic"))_
    val lib = destRelative("lib")
    val conf = destRelative("conf")
    val libRelative = FileHelper.relativeTo(lib)_
    val confRelative = FileHelper.relativeTo(conf)_
    lib.mkdirs()
    libFiles.foreach(f => FileHelper.copyFile(sourceRelative(f), libRelative(f)))
    val processedConfigFile = new StringBuilder()
    processSource(sourceRelative(newRelicConfigFile), NewRelicConfigSourceProcessor.sourceProcessor(key, appName)) { line =>
      processedConfigFile.append(line)
      processedConfigFile.append("\n")
    }
    FileHelper.writeToFile(processedConfigFile.toString.getBytes("utf-8"), confRelative(newRelicConfigFile))
  }

  def isProjectEnabled(root: File): Boolean = {
    val nrRoot = FileHelper.relativeTo(root)_
    val lib = nrRoot("lib")
    val conf = nrRoot("conf")
    val libRelative = FileHelper.relativeTo(lib)_
    val confRelative = FileHelper.relativeTo(conf)_
    def hasFile(file: String): Boolean = nrRoot(file).exists()
    libRelative("newrelic.jar").exists() && confRelative("newrelic.yml").exists()
  }

  trait SourceProcessor {
    def processLine(in: String): String
  }

  def bodyProcessor(proc: String => String): SourceProcessor = new SourceProcessor {
    def processLine(in: String): String = proc(in)
  }

  object NewRelicConfigSourceProcessor {
    val commonRegex = "^common:.*$".r
    val developmentRegex = "^development:.*$".r
    val testRegex = "^test:.*$".r
    val productionRegex = "^production:.*$".r
    val stagingRegex = "^staging:.*$".r
    val licenseKeyPrefix = "  license_key:"
    val licenseKeyRegex = s"^${licenseKeyPrefix}.*$$".r
    val appNamePrefix = "  app_name:"
    val appNameRegex = s"^${appNamePrefix}.*$$".r

    type Transition = String => Option[State]

    sealed trait State {
      def process(in: String): (State, String)
    }
    trait CommonStateProcessor extends State {
      def bodyProcessor: SourceProcessor
      def transition: Transition

      def process(in: String): (State, String) = transition(in) match {
        case Some(state) => (state, in)
        case None => (this, bodyProcessor.processLine(in))
      }
    }
    case class Initial(common: Common) extends State {
      def process(in: String): (State, String) =
        if (commonRegex.findFirstIn(in).nonEmpty) (common, in)
        else (this, in)
    }
    case class Common(bodyProcessor: SourceProcessor, transition: Transition) extends CommonStateProcessor
    case class Development(bodyProcessor: SourceProcessor, transition: Transition) extends CommonStateProcessor
    case class Test(bodyProcessor: SourceProcessor, transition: Transition) extends CommonStateProcessor
    case class Production(bodyProcessor: SourceProcessor, transition: Transition) extends CommonStateProcessor
    case class Staging(bodyProcessor: SourceProcessor, transition: Transition) extends CommonStateProcessor

    def stringId(in: String): String = in

    def writeDeveloperKey(key: String, orElse: String => String)(in: String): String =
      if (licenseKeyRegex.findFirstIn(in).nonEmpty) s"$licenseKeyPrefix '$key'"
      else orElse(in)

    def writeApplicatioName(name: String, orElse: String => String)(in: String): String =
      if (appNameRegex.findFirstIn(in).nonEmpty) s"$appNamePrefix $name"
      else orElse(in)

    def commonWriter(key: String, name: String): String => String =
      writeDeveloperKey(key, writeApplicatioName(name, stringId))

    def nameWriter(name: String): String => String =
      writeApplicatioName(name, stringId)

    def newRelicConfigProcessorState(key: String, name: String): State = {
      def developmentTransition(in: String): Option[State] =
        developmentRegex.findFirstIn(in).map(_ => Development(bodyProcessor(nameWriter(s"$name (development)")), environmentTransition))
      def stagingTransition(in: String): Option[State] =
        stagingRegex.findFirstIn(in).map(_ => Staging(bodyProcessor(nameWriter(s"$name (staging)")), environmentTransition))
      def testTransition(in: String): Option[State] =
        testRegex.findFirstIn(in).map(_ => Test(bodyProcessor(nameWriter(s"$name (test)")), environmentTransition))
      def productionTransition(in: String): Option[State] =
        productionRegex.findFirstIn(in).map(_ => Production(bodyProcessor(nameWriter(name)), environmentTransition))
      def environmentTransition(in: String): Option[State] =
        developmentTransition(in) orElse stagingTransition(in) orElse testTransition(in) orElse productionTransition(in)

      Initial(Common(bodyProcessor(commonWriter(key, name)), environmentTransition))
    }

    class NewRelicConfigSourceProcessor(var state: NewRelicConfigSourceProcessor.State) extends SourceProcessor {
      def processLine(in: String): String = {
        val (newState, line) = state.process(in)
        state = newState
        line
      }
    }

    def sourceProcessor(key: String, name: String): SourceProcessor = new NewRelicConfigSourceProcessor(newRelicConfigProcessorState(key, name))
  }

  def hasNewRelic(root: File): Boolean = {
    val nrRoot = FileHelper.relativeTo(FileHelper.relativeTo(root)("newrelic"))_
    def hasFile(file: String): Boolean = nrRoot(file).exists()
    hasFile("newrelic.jar") && hasFile("newrelic.yml")
  }

  def processSource(in: File, processor: SourceProcessor)(body: String => Unit): Unit = {
    FileHelper.withFileReader(in) { reader =>
      FileHelper.withBufferedReader(reader) { br =>
        var line = br.readLine()
        while (line != null) {
          body(processor.processLine(line))
          line = br.readLine()
        }
      }
    }
  }

  private lazy val activatorHome: File = new File(ActivatorProperties.ACTIVATOR_HOME_FILENAME)

  case class Config(
    downloadUrlTemplate: String,
    version: String,
    sha: String,
    timeout: Timeout,
    extractRootTemplate: String) {
    val url: String = versionRegex.replaceAllIn(downloadUrlTemplate, version)

    def extractRoot(relativeTo: File = activatorHome): File = new File(relativeTo, versionRegex.replaceAllIn(extractRootTemplate, version))

    def verifyFile(in: File): File =
      FileHelper.verifyFile(in, sha)

    def extractFile(in: File, relativeTo: File = activatorHome): File =
      FileHelper.unZipFile(in, extractRoot(relativeTo = relativeTo))
  }
}

object Instrumentations {
  import play.api.libs.json._
  import play.api.libs.functional.syntax._
  import JsonHelper._

  case object InspectTag extends InstrumentationTag("inspect")
  case object NewRelicTag extends InstrumentationTag("newRelic")

  def fromString(in: String): InstrumentationTag = in.trim() match {
    case InspectTag.name => InspectTag
    case NewRelicTag.name => NewRelicTag
  }

  def withMonitoringConfig[T](in: TSConfig)(body: TSConfig => T): T = {
    val c = in.getConfig("activator.monitoring")
    body(c)
  }

  final val allInstrumentations = Set(InspectTag, NewRelicTag).map(_.name)

  def validate(in: String): InstrumentationTag = {
    val n = in.trim()
    if (allInstrumentations(n)) fromString(n)
    else throw new RuntimeException(s"$n is not a valid instrumentation.  Must be one of: $allInstrumentations")
  }

  implicit val inspectWrites: Writes[Inspect.type] =
    emitTagged("type", InspectTag.name)(_ => Json.obj())

  implicit val newRelicWrites: Writes[NewRelic] =
    emitTagged("type", NewRelicTag.name) {
      case NewRelic(configFile, agentJar, environment) =>
        Json.obj("configFile" -> configFile,
          "agentJar" -> agentJar,
          "environment" -> environment)
    }

  implicit val inspectReads: Reads[Inspect.type] =
    extractTagged("type", InspectTag.name)(Reads(_ => JsSuccess(Inspect)))

  implicit val newRelicReads: Reads[NewRelic] =
    extractTagged("type", NewRelicTag.name) {
      ((__ \ "configFile").read[File] and
        (__ \ "agentJar").read[File] and
        (__ \ "environment").read[String])(NewRelic.apply _)
    }

  implicit val instrumentationWrites: Writes[Instrumentation] =
    Writes(_ match {
      case Inspect => inspectWrites.writes(Inspect)
      case x: NewRelic => newRelicWrites.writes(x)
    })

  implicit val instrumentationReads: Reads[Instrumentation] =
    inspectReads.asInstanceOf[Reads[Instrumentation]] orElse newRelicReads.asInstanceOf[Reads[Instrumentation]]

  private def fileExists(dir: File, in: String): Boolean = new File(dir, in).exists()
}
