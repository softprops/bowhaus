package bowhaus

import java.util.Date

import com.twitter.bijection._
import com.twitter.finagle.redis.Client
import com.twitter.finagle.redis.util.{ CBToString, StringToChannelBuffer }
import com.twitter.util.Future
import com.twitter.storehaus._
import com.twitter.storehaus.redis._
import org.jboss.netty.buffer.ChannelBuffer

case class Package(name: String, url: String)

object Packages {
  def apply(storePrefix: String) = new Packages(storePrefix, Client("localhost:6379"))
}

class Packages(storePrefix: String, cli: Client) {
  import bowhaus.Bijections._

  private val packages: Store[String, Map[String, String]] =
    RedisHashStore(cli).convert(StringToChannelBuffer(_: String))

  private val packageUrls: Store[(String, String), String] =
    new UnpivotedRedisHashStore(RedisHashStore(cli))
          .convert({ case (key, url) => (StringToChannelBuffer(key), StringToChannelBuffer(url)) })

  private val allHits: Store[String, Seq[(String, Double)]] =
    RedisSortedSetStore(cli)
      .convert(StringToChannelBuffer(_: String))

  private val hits: Store[(String, String), Double] =
    RedisSortedSetStore(cli).members
      .convert({ case (set, member) => (StringToChannelBuffer(set), StringToChannelBuffer(member)) })

  private val hk = "%s:bowhaus:hits" format(storePrefix)
  private def pk(name: String) = "%s:bowhaus:packages:%s" format(storePrefix, name)

  def create(name: String, url: String): Future[Either[String, String]] =
    packages.get(pk(name)).flatMap(
      _.map(_ => Future.value(Left("package already exists")))
       .getOrElse(
         packages.put((pk(name), Some(
           Map("url" -> url, "created_at" -> new Date().getTime.toString))))
           .flatMap({
             _ =>
               hits.put(((hk, pk(name)), Some(0)))
               Future.value(Right("ok"))
           }))
    )

  def get(name: String): Future[Option[Package]] =
    packageUrls.get((pk(name), "url")).map(
      _.map(url => Package(name, url))
    )

  def list: Future[Iterable[Package]] =
    allHits.get(hk).flatMap({ mhs =>
      val pkgs: Option[Future[Iterable[Package]]] = mhs.map({ hs =>
        FutureOps.mapCollect(packageUrls.multiGet(hs.map { case (key, _) => (key, "url") }.toSet))
                 .map((_.map({
                   case ((key, _), Some(url)) => Some(Package(key, url))
                   case _ => None
                 }).flatten))
      })
      pkgs.getOrElse(Future.value(Nil))
    })

  def like(name: String): Future[Iterable[Package]] =
    Future.value(Nil) // todo: impl me
}
