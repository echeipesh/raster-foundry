package com.azavea.rf.thumbnail

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.auth.Authentication
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.utils.{Database, UserErrorHandler, RouterHelper}

trait ThumbnailRoutes extends Authentication
    with ThumbnailQueryParameterDirective
    with PaginationDirectives
    with RouterHelper {

  implicit def database: Database
  implicit val ec: ExecutionContext

  val getThumbnailRoutes: Route = {
    pathEndOrSingleSlash {
      anonWithPage { (user, page) =>
        get {
          thumbnailSpecificQueryParameters { thumbnailSpecificQueryParameters =>
            onSuccess(ThumbnailService.getThumbnails(page, thumbnailSpecificQueryParameters)) { thumbs =>
              complete(thumbs)
            }
          }
        }
      } ~
      pathPrefix(JavaUUID) { thumbnailId =>
        pathEndOrSingleSlash {
          anonWithPage { (user, page) =>
            get {
              onSuccess(ThumbnailService.getThumbnail(thumbnailId)) {
                case Some(thumbnail) => complete(thumbnail)
                case _ => complete(StatusCodes.NotFound)
              }
            }
          }
        }
      }
    }
  }

  def thumbnailRoutes = {
    pathPrefix("api" / "thumbnails") {
      getThumbnailRoutes
    }
  }
}
