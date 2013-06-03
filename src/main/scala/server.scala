package bowhaus

import unfiltered.netty.Http
import scopt.immutable.OptionParser

object Server {
  private case class Config(
    serverPort: Int = sys.env.getOrElse("BOWHAUS_PORT", "8080").toInt,
    redisHost: String = sys.env.getOrElse("BOWHAUS_REDIS", "localhost:6379"),
    redisPrefix: String = sys.env.getOrElse("BOWHAUS_NAMESPACE", "testing")) {
    def parse(a: Array[String]) =
      new OptionParser[Config] {
        def options = Seq(
          intOpt("p", "port", "port to listen on") {
            (p, c) => c.copy(serverPort = p)
          },
          opt("r", "redis", "redis host") {
            (h, c) => c.copy(redisHost = h)
          },
          opt("n", "namespace", "prefix for redis keys") {
            (p, c) => c.copy(redisPrefix = p)
          }
        )
      } parse(a, this)
  }

  def main(a: Array[String]) {
    Config().parse(a).map { conf =>
      val stores = new RedisPackageStores(conf.redisHost)
      Http(conf.serverPort)
        .plan(Endpoints(stores, conf.redisPrefix))
        .beforeStop {
          stores.shutdown
        }
        .run()
    } getOrElse {
      sys.error("invalid args: %s" format a.toList)
    }
  }
}
