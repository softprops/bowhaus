package bowhaus

import java.net.URI
import java.util.Date

import com.twitter.bijection._
import com.twitter.util.Future
import com.twitter.storehaus._
import com.twitter.storehaus.redis._

case class Package(name: String, url: String)

object Packages {
  def apply(stores: PackageStores, storePrefix: String) =
    new Packages(stores, storePrefix)
}

class Packages(stores: PackageStores, prefix: String) {
  import bowhaus.Bijections._

  private val hk = "%s:bowhaus:hits" format(prefix)
  private def pk(name: String) = "%s:bowhaus:packages:%s" format(prefix, name)

  def create(name: String, url: URI): Future[Either[String, String]] =
    stores.packages.get(pk(name)).flatMap(
      _.map(_ => Future.value(Left("package already exists")))
       .getOrElse(
         stores.packages.put((pk(name), Some(
           Map("url" -> url.toString, "created_at" -> new Date().getTime.toString))))
           .flatMap({
             _ =>
               stores.packageHits.put(((hk, pk(name)), Some(0)))
               Future.value(Right("ok"))
           }))
    )

  def get(name: String): Future[Option[Package]] =
    stores.packageUrls.get((pk(name), "url")).map(
      _.map { url =>
        stores.packageHits.merge(((hk, pk(name)), 1))
        Package(name, url)
      }
    )

  def list: Future[Iterable[Package]] =
    stores.hits.get(hk).flatMap({
      _.map({ hs =>
        FutureOps.mapCollect(stores.packageUrls.multiGet(hs.map { case (key, _) => (key, "url") }.toSet))
                 .map((_.map({
                   case ((key, _), Some(url)) => Some(Package(key, url))
                   case _ => None
                 }).flatten))
      }).getOrElse(Future.value(Nil))
    })

  def like(name: String): Future[Iterable[Package]] =
    Future.value(Nil) // todo: impl me
}
