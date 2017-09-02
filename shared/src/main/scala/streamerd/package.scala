import scala.concurrent.Future

package object streamerd {
  implicit class EitherToFuture[A <: Throwable, B](e: Either[A, B]){
    def toFuture: Future[B] = e match {
      case Right(e) => Future.successful(e)
      case Left(e) => Future.failed(e)
    }
  }

}
