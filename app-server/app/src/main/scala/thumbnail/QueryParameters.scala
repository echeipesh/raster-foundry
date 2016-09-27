package com.azavea.rf.thumbnail

import java.util.UUID
import java.sql.Timestamp
import java.time.Instant

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Rejection}
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.azavea.rf.utils.queryparams._

/** Case class representing all /thumbnail query paremeters */
case class ThumbnailQueryParameters(
  sceneId: Option[UUID]
)

/** Combined all relevant query parameters for /thumbnails */
case class CombinedThumbnailQueryParameters(
  thumbnailParams: ThumbnailQueryParameters
)

trait ThumbnailQueryParameterDirective extends QueryParametersCommon {

  val thumbnailSpecificQueryParameters = parameters(
    'sceneId.as[UUID].?
  ).as(ThumbnailQueryParameters)

  val thumbnailQueryParameters = (
    thumbnailSpecificQueryParameters
  ).as(CombinedThumbnailQueryParameters)
}
