package streamerd

import cats.kernel.Monoid

case class ClientError(message: String) extends RuntimeException(message)

object ClientError {
  implicit def monoid: Monoid[ClientError] = new Monoid[ClientError] {
    def empty = ClientError("<br/>")
    def combine(a: ClientError, b: ClientError): ClientError =
      ClientError(s"${a.message}<br/>${b.message}")
  }
}
