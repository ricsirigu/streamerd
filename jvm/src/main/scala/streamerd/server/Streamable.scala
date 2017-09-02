package streamerd.server

import java.io.{File, InputStream}

import org.apache.commons.io.{FileUtils, IOUtils}

/**
  * Bundle of operations supported by the type A
  *
  * @tparam A
  */
trait Streamable[A] {
  def stream(a: A): () => InputStream
}

/**
  * Typeclass implementation
  */
object Streamable {

  implicit val streamableFile: Streamable[File] = f =>
    () => FileUtils.openInputStream(f)

  implicit val streamableString: Streamable[String] = str =>
    () => IOUtils.toInputStream(str, defaultEncoding)
}

/**
  * With StreamableOps we are decorating a type A with the methods
  * provided by the typeclass Streamable. In this case the stream method.
  */
object StreamableSyntax {

  implicit class StreamableOps[A](a: A)(implicit typeclass: Streamable[A]) {
    def stream: () => InputStream = typeclass.stream(a)
  }

}
