package streamerd.server

case class ServerError(message: String) extends RuntimeException(message)
