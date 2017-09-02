package streamerd.server

import java.io.{InputStream, OutputStream}

case class Response(
                     payload: () => InputStream,
                     contentType: String = "text/plain",
                     responseCode: Int = 200,
                     headers: Map[String, String] = Map("Accept-Ranges" -> "bytes"),
                     writeMethod: Option[(InputStream, OutputStream) => Unit] = None
                   )
