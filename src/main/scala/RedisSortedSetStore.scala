package bowhaus

import com.twitter.algebird.Monoid
import com.twitter.util.Future
import com.twitter.finagle.redis.Client
import com.twitter.storehaus.Store
import com.twitter.storehaus.algebra.MergeableStore
import org.jboss.netty.buffer.ChannelBuffer

object RedisSortedSetStore {
  def apply(client: Client) =
    new RedisSortedSetStore(client)
}

/** A Store representation of a redis sorted set
 *  where keys represent the name of the set and values
 *  represent both the member's name and score within the set
 */
class RedisSortedSetStore(client: Client)
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

    /** projects a view of this set's members */
    def members: MergeableStore[(ChannelBuffer, ChannelBuffer), Double] =
      new RedisSortedSetMembershipStore(client)

    /** project a view of this set's members */
    def members(set: ChannelBuffer): MergeableStore[ChannelBuffer, Double] =
      new RedisSortedSetMembershipView(client, set)

    override def close = client.release
}

/**
 * A member-oriented view of a redis sorted set bound to a specific
 * set. Keys represent members. Values represent the members score
 * within the given set.
 *
 * These stores also have mergeable semantics via zIncrBy for a member's
 * score
 */
class RedisSortedSetMembershipView(client: Client, set: ChannelBuffer)
  extends Store[ChannelBuffer, Double]
     with MergeableStore[ChannelBuffer, Double] {
   private lazy val underlying = new RedisSortedSetMembershipStore(client)
   val monoid = implicitly[Monoid[Double]]

   override def get(k: ChannelBuffer): Future[Option[Double]] =
     underlying.get((set, k))

   override def put(kv: (ChannelBuffer, Option[Double])): Future[Unit] =
     underlying.put(((set,kv._1), kv._2))

   override def merge(kv: (ChannelBuffer, Double)): Future[Unit] =
     underlying.merge((set, kv._1), kv._2)

  override def close = client.release
}

/** A member-oriented view of redis sorted sets.
 *  Keys represent the both a name of the set and the member.
 *  Values represent the member's current score within a set.
 *  An absent score also indicates an absence of membership in the set.
 *
 *  These stores also have mergeable semantics via zIncrBy for a member's
 *  score
 */
class RedisSortedSetMembershipStore(client: Client)
  extends Store[(ChannelBuffer, ChannelBuffer), Double] 
     with MergeableStore[(ChannelBuffer, ChannelBuffer), Double] {
   val monoid = implicitly[Monoid[Double]]

   /** @return a member's score or None if the member is not in the set */
   override def get(k: (ChannelBuffer, ChannelBuffer)): Future[Option[Double]] =
     client.zScore(k._1, k._1).map(_.map(_.toDouble))
   
   /** Adds or removes members from sets with an initial scoring. A score of None indicates the
    *  member should be removed from the set */
   override def multiPut[K1 <: (ChannelBuffer, ChannelBuffer)](kv: Map[K1, Option[Double]]): Map[K1, Future[Unit]]  = {
     // we are exploiting redis's built-in support for removals (zRem)
     // by partioning deletions and updates into 2 maps indexed by the first
     // component of the composite key, the key of the set
     def emptyMap = Map.empty[ChannelBuffer, List[(K1, Double)]].withDefaultValue(Nil)
     val (del, persist) = ((emptyMap, emptyMap) /: kv) {
       case ((deleting, storing), (key, Some(score))) =>
         (deleting, storing.updated(key._1, (key, score) :: storing(key._1)))
       case ((deleting, storing), (key, None)) =>
         (deleting.updated(key._1, (key, 0.0) :: deleting(key._1)), storing)
     }
     (del.map {
       case (k, members) =>
         val value = client.zRem(k, members.map(_._1._2))
         members.map(_._1 -> value.unit)
     }.flatten ++ persist.map {
       case (k, members) =>
         members.map {
           case (k1, score) =>
             (k1 -> client.zAdd(k, score, k1._2).unit)
         }
     }.flatten).toMap
   }

    /** Performs an zIncrBy operation on a set for a given member */
    override def merge(kv: ((ChannelBuffer, ChannelBuffer), Double)): Future[Unit] =
      client.zIncrBy(kv._1._1, kv._2, kv._1._2).unit

    override def close = client.release
}
