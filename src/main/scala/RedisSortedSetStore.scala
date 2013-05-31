package bowhaus

import com.twitter.util.Future
import com.twitter.finagle.redis.Client
import com.twitter.storehaus.Store
import org.jboss.netty.buffer.ChannelBuffer

object RedisSortedSetStore {
  def apply(client: Client) =
    new RedisSortedSetStore(client)
}

class RedisSortedSetStore(val client: Client) 
  extends Store[ChannelBuffer, Seq[(ChannelBuffer, Double)]] {

    /** Returns the whole set as a tuple of seq of (member, score) */
    override def get(k: ChannelBuffer): Future[Option[Seq[(ChannelBuffer, Double)]]] =
      client.zRange(k, 0, -1, true).map(
        _.left.toOption.map( _.asTuples)
      )
    
    /** Replaces or deletes the whole set. Setting the set effectivly results
     *  in a delete of the previous sets key and multiple calls to zAdd for each member. */
    override def put(kv: (ChannelBuffer, Option[Seq[(ChannelBuffer, Double)]])) =
      kv match {
        case (set, Some(scorings)) =>
          client.del(Seq(set)).flatMap { _ =>
            members.multiPut(scorings.map {
              case (member, score) => ((set, member), Some(score))
            }.toMap)
            Future.Unit
          }
        case (set, None) =>
          client.del(Seq(set)).unit
      }

    def members = new RedisSortedSetMembershipStore(this)
}

class RedisSortedSetMembershipStore(store: RedisSortedSetStore) 
  extends Store[(ChannelBuffer, ChannelBuffer), Double] {

   /** @return a member's score or None if the member is not in the set */
   override def get(k: (ChannelBuffer, ChannelBuffer)): Future[Option[Double]] =
     store.client.zScore(k._1, k._1).map(_.map(_.toDouble))
   
   /** Adds or removes a member from the set with an initial scoring. A scoring of None
    *  indicates the member should be removed. */
   override def put(kv: ((ChannelBuffer, ChannelBuffer), Option[Double])): Future[Unit] =
     kv match {
       case ((set, member), Some(score)) =>
         store.client.zAdd(set, score, member).unit
       case ((set, member), None) =>
         store.client.zRem(set, Seq(member)).unit
     }

   // todo: multiPut via zRem
   // todo: merge with zIncrBy
}
