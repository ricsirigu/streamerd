package streamerd

import cats.{Applicative, Monoid}
import cats.syntax.monoid._
import scala.util.{Either => BaseEither}

object typeclasses {
  implicit def applicativeEither[A: Monoid]: Applicative[Either[A, ?]] = new Applicative[Either[A, ?]] {

    def pure[B](x: B): Either[A, B] = Right(x)

    def ap[B, C](ff: Either[A, B => C])(fa: Either[A, B]): BaseEither[A, C] = (ff, fa) match {
      case (Right(f), Right(b)) => Right(f(b))
      case (Left(a), Right(b)) => Left(a)
      case (Right(b), Left (a)) => Left(a)
      case (Left(a1), Left(a2)) => Left(a1 |+| a2)

    }
  }
}