package console

import org.specs2.mutable._
import play.api.libs.json.Json

class JsonHandlerSpec extends Specification {
  "JSON parser" should {
    "parse empty scope" in {
      val json = Json.parse(
        """
          {
            "scope" : {}
          }
        """)
      implicit val parser = JsonHandler.scopeReads
      val scope = (json \ "scope").as[InternalScope]
      scope.node must equalTo(None)
    }

    "parse full scope" in {
      val json = Json.parse(
        """
          {
            "scope" : {
              "node" : "n1",
              "actorSystem" : "as1",
              "dispatcher" : "d1",
              "tag" : "t1",
              "actorPath" : "a1"
            }
          }
        """)
      implicit val parser = JsonHandler.scopeReads
      val scope = (json \ "scope").as[InternalScope]
      scope.node must equalTo(Some("n1"))
      scope.actorSystem must equalTo(Some("as1"))
      scope.dispatcher must equalTo(Some("d1"))
      scope.tag must equalTo(Some("t1"))
      scope.actorPath must equalTo(Some("a1"))
    }

    "parse one inner module" in {
      val json = Json.parse(
        """
          {
            "modules" : [
              {
                "name" : "name1",
                "paging" : {
                  "offset" : 101,
                  "limit" : 11
                },
                "scope" : {
                  "node" : "n1"
                }
              }
            ]
          }
        """)
      implicit val parser = JsonHandler.innerModuleReads
      val innerModule = (json \ "modules").as[List[InnerModuleInformation]]
      innerModule.size must equalTo(1)
      innerModule.head.name must equalTo("name1")
      innerModule.head.scope.node must equalTo(Some("n1"))
      innerModule.head.pagingInformation must not be empty
      innerModule.head.pagingInformation.get.offset must equalTo(101)
      innerModule.head.pagingInformation.get.limit must equalTo(11)
      innerModule.head.sortCommand must equalTo(None)
      innerModule.head.scope.actorPath must equalTo(None)
    }

    "parse multiple inner modules" in {
      val json = Json.parse(
        """
          {
            "modules" : [
              {
                "name" : "name1",
                "sortCommand" : "sortOnThis",
                "traceId" : "traceId1",
                "scope" : {
                  "node" : "n1"
                }
              },
              {
                "name" : "name2",
                "scope" : {
                  "node" : "n2",
                  "actorSystem" : "as2"
                }
              },
              {
                "name" : "name3",
                "paging" : {
                  "offset" : 1,
                  "limit" : 1000
                },
                "sortCommand" : "sortMeOnThis",
                "scope" : {
                  "node" : "n3",
                  "actorSystem" : "as3",
                  "dispatcher" : "d3",
                  "actorPath" : "a3"
                }
              }
            ]
          }
        """)
      implicit val parser = JsonHandler.innerModuleReads
      val innerModules = (json \ "modules").as[List[InnerModuleInformation]]
      innerModules.size must equalTo(3)
      innerModules(0).name must equalTo("name1")
      innerModules(0).traceId must equalTo(Some("traceId1"))
      innerModules(0).scope.node must equalTo(Some("n1"))
      innerModules(0).sortCommand must equalTo(Some("sortOnThis"))
      innerModules(0).scope.tag must equalTo(None)

      innerModules(1).name must equalTo("name2")
      innerModules(1).traceId must equalTo(None)
      innerModules(1).scope.node must equalTo(Some("n2"))
      innerModules(1).scope.actorSystem must equalTo(Some("as2"))
      innerModules(1).scope.tag must equalTo(None)

      innerModules(2).name must equalTo("name3")
      innerModules(2).traceId must equalTo(None)
      innerModules(2).pagingInformation must not be empty
      innerModules(2).pagingInformation.get.offset must equalTo(1)
      innerModules(2).pagingInformation.get.limit must equalTo(1000)
      innerModules(2).sortCommand must equalTo(Some("sortMeOnThis"))
      innerModules(2).scope.node must equalTo(Some("n3"))
      innerModules(2).scope.actorSystem must equalTo(Some("as3"))
      innerModules(2).scope.dispatcher must equalTo(Some("d3"))
      innerModules(2).scope.actorPath must equalTo(Some("a3"))
      innerModules(2).scope.tag must equalTo(None)
    }
  }
}
