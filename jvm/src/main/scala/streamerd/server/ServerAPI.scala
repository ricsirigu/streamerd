package streamerd.server

import java.net.InetSocketAddress

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import org.apache.commons.io.IOUtils

import scala.collection.JavaConverters.seqAsJavaListConverter
import streamerd.server.StreamableSyntax._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object ServerAPI extends ServerAPI

trait ServerAPI {

  def createServer(port: Int)(handler: PartialHandler,
                              errorHandler: TotalHandler = defaultErrorHandler): HttpServer = {
    val server = HttpServer.create(new InetSocketAddress(port), 0)

    server.createContext("/", (e: HttpExchange) => Future {
      val Response(payloadIsGenerator, contentType, responseCode, extraHeaders, writeMethod) =
        handler.applyOrElse(e, errorHandler)

      val headers = e.getResponseHeaders

      headers.put("Content-Type", List(contentType).asJava)

      for {
        (k, v) <- extraHeaders
      } headers.put(k, List(v).asJava)

      val is = payloadIsGenerator()
      val os = e.getResponseBody

      try {
        e.sendResponseHeaders(responseCode, 0)
        writeMethod match {
          case Some(f) => f(is, os)
          case None => IOUtils.copy(is, os)
        }
      } finally {
        is.close()
        os.close()
      }
    })
    server
  }

  val defaultErrorHandler: TotalHandler = _ => Response("Not Found".stream, responseCode = 404)

}
