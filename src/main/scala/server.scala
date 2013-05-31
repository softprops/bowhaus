package bowhaus

import unfiltered.netty.Http

object Server {
  def main(a: Array[String]) {
    Http(8080).plan(Endpoints("test")).run()
  }
}
