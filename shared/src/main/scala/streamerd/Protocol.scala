package streamerd

sealed trait APIRequest

case class DirContentsReq(path: String) extends APIRequest

case class VideoReq(path: String) extends APIRequest

sealed trait APIResponse

case class DirContentsResp(contents: List[FileModel], parent: Option[FileModel]) extends APIResponse

case class VideoResp(
                      name: String,
                      streamPath: String,
                      parent: FileModel,
                      previous: Option[FileModel],
                      next: Option[FileModel]) extends APIResponse