package bowhaus

import com.twitter.bijection._
import com.twitter.finagle.redis.Client
import com.twitter.finagle.redis.util.StringToChannelBuffer
import com.twitter.storehaus._
import com.twitter.storehaus.redis._
import org.jboss.netty.buffer.ChannelBuffer

/** provides access to package related stores */
trait PackageStores {
  def packages: Store[String, Map[String, String]]
  def packageUrls: Store[(String, String), String]
  def hits: Store[String, Seq[(String, Double)]]
  def packageHits: Store[(String, String), Double] 
}

class RedisPackageStores(redisHost: String) extends PackageStores {
  import bowhaus.Bijections._
  private val cli = Client(redisHost) 
  val packages: Store[String, Map[String, String]] =
    RedisHashStore(cli).convert(StringToChannelBuffer(_: String))
  val packageUrls: Store[(String, String), String] =
    new UnpivotedRedisHashStore(RedisHashStore(cli))
          .convert({ case (key, url) => (StringToChannelBuffer(key), StringToChannelBuffer(url)) })
  val hits: Store[String, Seq[(String, Double)]] =
    RedisSortedSetStore(cli)
      .convert(StringToChannelBuffer(_: String))
  val packageHits: Store[(String, String), Double] =
    RedisSortedSetStore(cli).members
      .convert({ case (set, member) => (StringToChannelBuffer(set), StringToChannelBuffer(member)) })

  /** shutdown the stores */
  def shutdown = cli.release
}
