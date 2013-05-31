package bowhaus

import com.twitter.bijection.Conversion

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

object Conversions {
  object PackageConversion extends Conversion[Package, JValue] {
    def apply(p: Package) = ("name" -> p.name) ~ ("url" -> p.url)
  }

  object PackagesConversion extends Conversion[Iterable[Package], JValue] {
    def apply(ps: Iterable[Package]) = ps.map(PackageConversion.apply)
  }
}
