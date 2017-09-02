package streamerd.server

sealed trait Method

case object GET extends Method

case object POST extends Method
