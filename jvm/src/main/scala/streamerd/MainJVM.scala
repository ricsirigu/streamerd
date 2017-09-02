package streamerd

import java.io.File

import cats.data.OptionT
import cats.data.OptionT.{liftF, fromOption => liftOpt}
import cats.instances.either._
import cats.syntax.all._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Error => CirceError}
import org.apache.commons.codec.net.URLCodec
import org.apache.commons.io.{FilenameUtils, IOUtils}
import streamerd.server.ServerAPI._
import streamerd.server.StreamableSyntax._
import streamerd.server.{GET, _}

import scala.collection.JavaConverters._
import scala.util.Try

object MainJVM {

  type Error[A] = Either[Throwable, A]

  implicit class FileString(str: String) {
    def assetFile: File = new File(s"assets/$str")

    def file: File = new File(str)
  }

  implicit class HttpHeaderString(str: String) {
    def rangeHeaderWithCeiling(ceil: Long): Either[Throwable, (Long, Long)] =
      Try {
        str.drop("bytes=".length).split("-").toList.map(_.toLong)
      }
        .toEither.flatMap {
        case from :: to :: Nil => Right(from -> math.min(ceil - 1, to))
        case from :: Nil => Right(from -> (ceil - 1))
        case _ => Left(ServerError("Invalid format of the Range header"))
      }
  }

  implicit class FileAPI(file: File) {
    def contents: Either[Throwable, List[File]] = Try {
      file.listFiles
    }.toEither
      .filterOrElse(null !=, ServerError(s"Error occurred while retrieving the contents of the file $file"))
      .map(_.toList)

    def size: Either[Throwable, Long] = Try { file.length() }.toEither

    def toModel = FileModel(file.getAbsolutePath, file.getName, file.tpe)

    def tpe: FileType = file match {
      case _ if file.isDirectory => FileType.Directory
      case _ if isVideo => FileType.Video
      case _ => FileType.Misc
    }

    def parent: Option[File] = Some(file.getParentFile).filter(null !=)

    def parentModel: Option[FileModel] = parent.map(_.toModel.copy(tpe = FileType.Parent))

    def isVideo: Boolean = Set("mp4", "m4v") contains extension

    def extension: String = FilenameUtils.getExtension(file.getName)

    def leftNeighbor = neighbor(_ => true) { case f :: `file` :: Nil => f }

    def rightNeighbor = neighbor(_ => true) { case `file` :: f :: Nil => f }

    def leftNeighborOfType(allowedTypes: Set[FileType]) =
      neighbor(f => allowedTypes(f.tpe)) { case f :: `file` :: Nil => f }

    def rightNeighborOfType(allowedTypes: Set[FileType]) =
      neighbor(f => allowedTypes(f.tpe)) { case `file` :: f :: Nil => f }

    private[this] def neighbor(filter: File => Boolean)(predicate: PartialFunction[List[File], File]): OptionT[Error, File] = {
      for {
        p <- liftOpt[Error](parent)
        contents <- liftF[Error, List[File]](p.contents)
        neighbour <- liftOpt[Error](contents.filter(filter)
          .sliding(2, 1).collectFirst(predicate))
      } yield neighbour
    }
  }

  implicit class OptionToEither[A](opt: Option[A]) {
    def toEither: Either[ServerError, A] = opt match {
      case Some(x) => Right(x)
      case None => Left(ServerError(s"Could not extract value from Option"))
    }
  }

  def main(args: Array[String]): Unit = {
    val videoPath = """/video/(.+)""".r

    val server = createServer(8080) {
      case GET -> "/" => Response("html/index.html".assetFile.stream, text.html)
      case GET -> "/js/application.js" => Response("js/application.js".assetFile.stream, application.javascript)
      case e @ GET -> videoPath(path) =>
        (for {
          file <- Some(new URLCodec().decode(path).file).filter(_.exists).toEither
          available <- file.size
          range <- e.getRequestHeaders.get("Range").asScala.head.rangeHeaderWithCeiling(available)
          (from, to) = range
          length = to - from + 1
        } yield Response(
          payload = file.stream,
          contentType = video.mp4,
          responseCode = 206,
          headers = Map(
            "Accept-Ranges" -> "bytes",
            "Content-Range" -> s"bytes $from-$to/$available"
          ),
          writeMethod = Some { (is, os) => IOUtils.copyLarge(is, os, from, length) }
        )).leftMap { e => Response(s"Error occurred: ${e.toString}".stream, responseCode = 400) }
          .merge
      case e @ POST -> "/api" =>
        (for {
          req <- Try {
            IOUtils.toString(e.getRequestBody, defaultEncoding)
          }.toEither
          decoded <- decode[APIRequest](req)
          respJson <- handleApi(decoded)
          response = Response(respJson.asJson.noSpaces.stream, application.json)
        } yield response).leftMap {
          case e: CirceError => Response(s"Error occurred while parsing Json: ${e.toString}".stream, responseCode = 400)
          case e: Throwable => Response(s"Error occurred: ${e.toString}".stream, responseCode = 400)
        }.merge
    }
    server.start()
  }

  def handleApi(request: APIRequest): Either[Throwable, APIResponse] = request match {
    case DirContentsReq(path) =>
      for {
        contents <- path.file.contents
        contentsPaths = contents.map(_.toModel).sortBy(_.tpe != FileType.Directory)
        maybeParent = path.file.parentModel
      } yield DirContentsResp(contentsPaths, maybeParent)

    case VideoReq(path) =>
      for {
        parent <- path.file.parentModel.toEither
        maybePrevious <- path.file.leftNeighborOfType(Set(FileType.Video)).map(_.toModel).value
        maybeNext <- path.file.rightNeighborOfType(Set(FileType.Video)).map(_.toModel).value
        streamPath = s"/video/$path"
      } yield VideoResp(path.file.getName, streamPath, parent, maybePrevious, maybeNext)

    case _ => Left(ServerError(s"Unknown Api request $request"))
  }
}

