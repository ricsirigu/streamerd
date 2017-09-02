package streamerd

import scala.util.{Try, Success, Failure}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import scala.scalajs.js.JSApp
import org.scalajs.dom.{document, window, Element}
import org.scalajs.dom.ext.{Ajax, AjaxException}
import scalatags.JsDom.all._

import io.circe.parser.decode
import io.circe.generic.auto._, io.circe.syntax._

import cats.syntax.all._
import cats.instances.list._, cats.instances.option._
import typeclasses._


object MainJS extends JSApp with AjaxHelpers with ViewHelpers {
  def placeholder = document.getElementById("body-placeholder")

  def main(): Unit = window.onload = { _ =>
    ajax(DirContentsReq("/")).onComplete(renderResponse)
  }

  def handleApi(response: APIResponse): Either[Throwable, ClientOperation] = response match {
    case resp: APIResponse => view(resp).map(RenderTag)
    case _ => Left(ClientError(s"Can not handle $response"))
  }

  def view(x: Any): Either[ClientError, HtmlTag] = x match {
    case DirContentsResp(files, parent: Option[FileModel]) =>
      (files.traverse(view) |@| parent.traverse(view)).map { (filesViews, maybeParentView) =>
        ul( (maybeParentView ++ filesViews).map { f => li(f) }.toList ) }

    case FileModel(path, name, tpe) =>
      tpe match {
        case FileType.Directory => fileView("folder icon"      , buttonFragment(path, name))
        case FileType.Parent    => fileView("level up icon"    , buttonFragment(path, ".."))
        case FileType.Misc      => fileView("file outline icon", span(name))
        case FileType.Video     => fileView("film icon"        , buttonFragment(VideoReq(path), name))
      }

    case VideoResp(name, stream, parent, previous, next) =>
      for {
        previousView <- view(previous)
        nextView     <- view(next)
        parentView   <- view(parent)
        streamView   <- view(StreamView(stream))
      } yield div(
        h1(name),
        parentView,
        streamView,
        previousView,
        nextView
      )

    case StreamView(stream) =>
      Right(
        video( width := 710, height := 400, attr("controls") := true )(
          source(src := stream, `type` := "video/mp4")
        )
      )

    case EmptyView => Right(p())

    case opt: Option[_] => view(opt.getOrElse(EmptyView))

    case _ => Left(ClientError(s"Can not render view: $x"))
  }
}

trait ViewHelpers { this: AjaxHelpers =>
  def buttonFragment(request: APIRequest, name: String): HtmlTag =
    button(onclick := ajaxCallback(request))(name)

  def buttonFragment(path: String, name: String): HtmlTag =
    buttonFragment(DirContentsReq(path), name)

  def fileView(iconClass: String, fragment: HtmlTag): Right[Nothing, HtmlTag] =
    Right( p(i(`class` := iconClass), fragment) )
}

trait AjaxHelpers {
  def placeholder: Element

  def handleApi(response: APIResponse): Either[Throwable, ClientOperation]

  def ajax(request: APIRequest): Future[ClientOperation] =
    for {
      response  <- Ajax.post(url = "/api", data = request.asJson.noSpaces)
      respText   = response.responseText
      decoded   <- decode[APIResponse](respText).toFuture
      operation <- handleApi(decoded).toFuture
    } yield operation

  def renderResponse(response: Try[ClientOperation]): Unit = response match {
    case Success(RenderString(str)) => placeholder.innerHTML = str
    case Success(RenderTag   (tag)) => placeholder.innerHTML = ""
      placeholder.appendChild(tag.render)

    case Failure(err: AjaxException) => placeholder.innerHTML = s"Ajax exception: ${err.xhr.responseText}"
    case Failure(err) => placeholder.innerHTML = s"Unknown error: ${err.toString}"
  }

  def ajaxCallback(request: APIRequest): () => Unit =
    () => ajax(request).onComplete(renderResponse)
}