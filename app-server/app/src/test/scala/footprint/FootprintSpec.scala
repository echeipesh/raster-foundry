package com.azavea.rf.footprint

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.actor.ActorSystem
import concurrent.duration._
import spray.json._

import geotrellis.vector.io._
import geotrellis.vector.{MultiPolygon, Polygon, Point, Geometry}
import geotrellis.slick.Projected

import com.azavea.rf.utils.Config
import com.azavea.rf.{DBSpec, Router}
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.utils.PaginatedResponse
import com.azavea.rf.AuthUtils

class FootprintSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {
  implicit val ec = system.dispatcher
  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(20).second)

  val anonAuthorization = AuthUtils.generateAuthHeader("Default")
  val authorization = AuthUtils.generateAuthHeader("User")

  "/api/footprints" should {
    "return a paginated list of footprints" in {
      Get("/api/footprints") ~> footprintRoutes ~> check {
        responseAs[PaginatedResponse[FootprintWithGeojson]]
      }
    }
    "allow the creation of new footprints" in {
      val poly = Projected(
        MultiPolygon(Polygon(Seq(Point(100,100), Point(110,100), Point(110,110), Point(100,110), Point(100,100)))),
        3857
      )
      val newFootprint = FootprintWithGeojsonCreate(
        java.util.UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726"),
        poly.geom.toGeoJson.parseJson.asJsObject
      )
      val request = Post("/api/footprints").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newFootprint.toJson(footprintWithGeojsonCreateFormat).toString()
        )
      )
      // request.toString() shouldEqual "something"
      request ~> footprintRoutes ~> check {
        responseAs[FootprintWithGeojson]
      }
    }
    "filter footprints by point" in {
      val poly = Projected(
        MultiPolygon(Polygon(Seq(Point(100,100), Point(110,100), Point(110,110), Point(100,110), Point(100,100)))),
        3857
      )
      val newFootprint = FootprintWithGeojsonCreate(
        java.util.UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726"),
        poly.geom.toGeoJson.parseJson.asJsObject
      )
      Post("/api/footprints").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newFootprint.toJson(footprintWithGeojsonCreateFormat).toString()
        )
      ) ~> footprintRoutes
      Get("/api/footprints/?x=101&y=101") ~> footprintRoutes ~> check {
        val res = responseAs[PaginatedResponse[FootprintWithGeojson]]
        res.count === 1
      }
    }
    "filter footprints by bounding box" in {
      val poly1 = Projected(
        MultiPolygon(Polygon(Seq(Point(0,0), Point(1,0), Point(1,1), Point(0,1), Point(0,0)))),
        3857
      )
      val newFootprint1 = FootprintWithGeojsonCreate(
        java.util.UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726"),
        poly1.geom.toGeoJson.parseJson.asJsObject
      )
      Post("/api/footprints").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newFootprint1.toJson(footprintWithGeojsonCreateFormat).toString()
        )
      ) ~> footprintRoutes
      val poly2 = Projected(
        MultiPolygon(
          Polygon(Seq(Point(10,10), Point(20,10), Point(20,20), Point(10,20), Point(10,10)))
        ),
        3857
      )
      val newFootprint2 = FootprintWithGeojsonCreate(
        java.util.UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726"),
        poly2.geom.toGeoJson.parseJson.asJsObject
      )
      Post("/api/footprints").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newFootprint2.toJson(footprintWithGeojsonCreateFormat).toString()
        )
      ) ~> footprintRoutes


      Get("/api/footprints/?bbox=5,5,30,30") ~> footprintRoutes ~> check {
        val res = responseAs[PaginatedResponse[FootprintWithGeojson]] 
        res.count === 1
      }
    }
  }
}
