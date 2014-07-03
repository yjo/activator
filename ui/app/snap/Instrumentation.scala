/**
 * Copyright (C) 2014 Typesafe <http://typesafe.com/>
 */
package snap

import java.io._
import java.util.concurrent.TimeUnit

import activator.properties.ActivatorProperties
import akka.util.Timeout
import com.typesafe.config.{ Config => TSConfig }

import scala.concurrent.duration._

sealed trait InstrumentationTag {
  def name: String
}

sealed abstract class Instrumentation(val name: String) {
  def jvmArgs: Seq[String]
  def tag: InstrumentationTag
}

object Instrumentation {
  lazy val activatorHome: File = new File(ActivatorProperties.ACTIVATOR_HOME_FILENAME)
}

case object Inspect extends Instrumentation(Instrumentations.inspectName) { self =>
  case object Tag extends InstrumentationTag {
    final val name: String = self.name
  }
  def jvmArgs: Seq[String] = Seq.empty[String]
  val tag: InstrumentationTag = Tag
}

case class NewRelic(configFile: File, agentJar: File, environment: String = "development") extends Instrumentation(Instrumentations.newRelicName) {
  def jvmArgs: Seq[String] = Seq(
    s"-javaagent:${agentJar.getPath}",
    s"-Dnewrelic.config.file=${configFile.getPath}",
    s"-Dnewrelic.environment=$environment")
  val tag: InstrumentationTag = NewRelic.Tag
}

case class AppDynamics(agentJar: File,
  applicationName: String,
  nodeName: String,
  tierName: String,
  accountName: String,
  accessKey: String,
  hostName: String,
  port: Int,
  sslEnabled: Boolean) extends Instrumentation(Instrumentations.appDynamicsName) {
  def jvmArgs: Seq[String] = Seq(
    s"-javaagent:${agentJar.getPath}",
    s"-Dappdynamics.agent.tierName=${tierName}",
    s"-Dappdynamics.agent.nodeName=${nodeName}",
    s"-Dappdynamics.agent.applicationName=${applicationName}",
    s"-Dappdynamics.agent.runtime.dir=${agentJar.getParentFile.getPath}",
    s"-Dappdynamics.agent.accountName=$accountName",
    s"-Dappdynamics.agent.accountAccessKey=$accessKey",
    s"-Dappdynamics.controller.hostName=$hostName",
    s"-Dappdynamics.controller.port=$port",
    s"-Dappdynamics.controller.ssl.enabled=$sslEnabled")
  def tag: InstrumentationTag = AppDynamics.Tag(applicationName, nodeName, tierName, accountName, accessKey, hostName, port, sslEnabled)
}

object NewRelic {
  sealed abstract class CheckResult(val message: String)
  case object MissingConfigFile extends CheckResult("Missing configuration file")
  case object MissingInstrumentationJar extends CheckResult("Missing instrumentation jar")

  case object Tag extends InstrumentationTag {
    final val name: String = Instrumentations.newRelicName
  }

  final val versionRegex = "\\{version\\}".r

  def fromConfig(in: TSConfig): Config = {
    import snap.Instrumentations.withMonitoringConfig
    withMonitoringConfig(in) { configRoot =>
      val config = configRoot.getConfig("new-relic")
      Config(downloadUrlTemplate = config.getString("download-template"),
        version = config.getString("version"),
        sha = config.getString("checksum"),
        timeout = Timeout(config.getDuration("timeout", TimeUnit.MILLISECONDS).intValue.millis),
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

  case class Config(
    downloadUrlTemplate: String,
    version: String,
    sha: String,
    timeout: Timeout,
    extractRootTemplate: String) {
    import Instrumentation._

    val url: String = versionRegex.replaceAllIn(downloadUrlTemplate, version)

    def extractRoot(relativeTo: File = activatorHome): File = new File(relativeTo, versionRegex.replaceAllIn(extractRootTemplate, version))

    def verifyFile(in: File): File =
      FileHelper.verifyFile(in, sha)

    def extractFile(in: File, relativeTo: File = activatorHome): File =
      FileHelper.unZipFile(in, extractRoot(relativeTo = relativeTo))
  }
}

object AppDynamics {
  case class Tag(applicationName: String,
    nodeName: String,
    tierName: String,
    accountName: String,
    accessKey: String,
    hostName: String,
    port: Int,
    sslEnabled: Boolean) extends InstrumentationTag {
    final val name: String = s"$tierName-$nodeName-$applicationName-$accountName-$accessKey-$hostName-$port-$sslEnabled"
  }

  sealed abstract class CheckResult(val message: String)
  case object IncompleteProvisioning extends CheckResult("AppDynamics provisioning incomplete")

  def fromConfig(in: TSConfig): Config = {
    import snap.Instrumentations.withMonitoringConfig
    withMonitoringConfig(in) { configRoot =>
      val config = configRoot.getConfig("appdynamics")
      Config(loginUrl = config.getString("login-url"),
        downloadUrlTemplate = config.getString("download-template"),
        timeout = Timeout(config.getDuration("timeout", TimeUnit.MILLISECONDS).intValue.millis),
        extractRootTemplate = config.getString("extract-root-template"))
    }
  }

  def hasAppDynamics(source: File): Boolean = {
    val result = source.exists() && source.isDirectory && source.listFiles().nonEmpty
    println(s"Checking $source - result: $result")
    result
  }

  def deprovision(target: File): Unit = FileHelper.deleteAll(target)

  case class Config(
    loginUrl: String,
    downloadUrlTemplate: String,
    timeout: Timeout,
    extractRootTemplate: String) {
    import Instrumentation._

    val url: String = downloadUrlTemplate

    def extractRoot(relativeTo: File = activatorHome): File = new File(relativeTo, extractRootTemplate)

    def extractFile(in: File, relativeTo: File = activatorHome): File =
      FileHelper.unZipFile(in, extractRoot(relativeTo = relativeTo))
  }
}

object Instrumentations {
  import play.api.libs.functional.syntax._
  import play.api.libs.json._
  import snap.JsonHelper._

  val inspectName = "inspect"
  val newRelicName = "newRelic"
  val appDynamicsName = "appDynamics"

  def withMonitoringConfig[T](in: TSConfig)(body: TSConfig => T): T = {
    val c = in.getConfig("activator.monitoring")
    body(c)
  }

  final val allInstrumentations = Set(inspectName, newRelicName, appDynamicsName)

  implicit val inspectWrites: Writes[Inspect.type] =
    emitTagged("type", inspectName)(_ => Json.obj())

  implicit val newRelicWrites: Writes[NewRelic] =
    emitTagged("type", newRelicName) {
      case NewRelic(configFile, agentJar, environment) =>
        Json.obj("configFile" -> configFile,
          "agentJar" -> agentJar,
          "environment" -> environment)
    }

  implicit val appDynamicsWrites: Writes[AppDynamics] =
    emitTagged("type", appDynamicsName) {
      case AppDynamics(agentJar, applicationName, nodeName, tierName, accountName, accessKey, hostName, port, sslEnabled) =>
        Json.obj("agentJar" -> agentJar,
          "applicationName" -> applicationName,
          "nodeName" -> nodeName,
          "tierName" -> tierName,
          "accountName" -> accountName,
          "accessKey" -> accessKey,
          "hostName" -> hostName,
          "port" -> port,
          "sslEnabled" -> sslEnabled)
    }

  implicit val inspectReads: Reads[Inspect.type] =
    extractTagged("type", inspectName)(Reads(_ => JsSuccess(Inspect)))

  implicit val newRelicReads: Reads[NewRelic] =
    extractTagged("type", newRelicName) {
      ((__ \ "configFile").read[File] and
        (__ \ "agentJar").read[File] and
        (__ \ "environment").read[String])(NewRelic.apply _)
    }

  implicit val appDynamicsReads: Reads[AppDynamics] =
    extractTagged("type", appDynamicsName) {
      ((__ \ "agentJar").read[File] and
        (__ \ "applicationName").read[String] and
        (__ \ "nodeName").read[String] and
        (__ \ "tierName").read[String] and
        (__ \ "accountName").read[String] and
        (__ \ "accessKey").read[String] and
        (__ \ "hostName").read[String] and
        (__ \ "port").read[Int] and
        (__ \ "sslEnabled").read[Boolean])(AppDynamics.apply _)
    }

  implicit val instrumentationWrites: Writes[Instrumentation] =
    Writes {
      case Inspect => inspectWrites.writes(Inspect)
      case x: NewRelic => newRelicWrites.writes(x)
      case x: AppDynamics => appDynamicsWrites.writes(x)
    }

  implicit val instrumentationReads: Reads[Instrumentation] =
    inspectReads.asInstanceOf[Reads[Instrumentation]] orElse newRelicReads.asInstanceOf[Reads[Instrumentation]] orElse appDynamicsReads.asInstanceOf[Reads[Instrumentation]]
}
