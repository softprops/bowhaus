package bowhaus

import java.net.URI
import unfiltered.netty
import unfiltered.request._
import unfiltered.response._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

import scala.util.control.Exception.allCatch

import bowhaus.Conversions._

object Endpoints {
  def apply(packageStores: PackageStores, redisPrefix: String): netty.async.Plan =
    new Endpoints(packageStores, redisPrefix)
}

class Endpoints(packageStores: PackageStores, redisPrefix: String)
  extends netty.async.Plan
  with netty.ServerErrorResponse {
  val one = PackageConversion
  val many = PackagesConversion
  val packages = Packages(packageStores, redisPrefix)
  def json(jv: JValue) = JsonContent ~> ResponseString(compact(render(jv)))
  import QParams._
  def intent =  {
    case r @ POST(Path(Seg("packages" :: Nil))) & Params(params) =>
      val expected = for {
        name <- lookup("name") is required()
        url  <- (lookup("url") is required()
                  is watch(_.flatMap(u => allCatch.opt(new URI(u))), { u => "%s must be a uri" format u})
                  is pred({ u => Option(u.getScheme).filter(_.startsWith("git")).isDefined }, { _ => "must be a git repo" }))
      } yield {
        packages.create(name.get, url.get).map(
          _.fold({ e => r.respond(Conflict) },
                 { e => r.respond(Created) })
        )
      }
      expected(params).orFail {
        f => r.respond(BadRequest)
      }
    case r @ GET(Path(Seg("packages" :: Nil))) =>
      packages.list.map(
        ps => r.respond(json(many(ps)))
      )
    case r @ GET(Path(Seg("packages" :: name :: Nil))) =>
      packages.get(name).map(
        _.map(p => r.respond(json(one(p))))
          .getOrElse(r.respond(NotFound))
      )
    case r @ GET(Path(Seg("packages" :: "search" :: name :: Nil))) =>
      packages.like(name).map(
        ps => r.respond(json(many(ps)))
      )
  }
}
