package com.azavea.rf.thumbnail

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import concurrent.duration._
import org.scalatest.{Matchers, WordSpec}
import spray.json._

import slick.lifted._

import com.azavea.rf.AuthUtils
import com.azavea.rf.datamodel.enums._
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.utils.Config
import com.azavea.rf.utils.PaginatedResponse
import com.azavea.rf.{DBSpec, Router}

import scala.util.{Success, Failure, Try}

class ThumbnailSpec extends WordSpec
    with ThumbnailSpecHelper
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {

  implicit val ec = system.dispatcher
  implicit def database = db

  val uuid = new UUID(123456789, 123456789)
  val baseThumbnailRow = ThumbnailsRow(
    uuid,
    new Timestamp(1234687268),
    new Timestamp(1234687268),
    uuid,
    128,
    128,
    "large",
    uuid,
    "https://website.com"
  )

  "Creating a row" should {
    "add a row to the table" ignore {
      val result = ThumbnailService.insertThumbnail(baseThumbnailRow)
      assert(result === Success)
    }
  }

  "Getting a row" should {
    "return the expected row" ignore {
      assert(ThumbnailService.getThumbnail(uuid) === baseThumbnailRow)
    }
  }

  "Updating a row" should {
    "change the expected values" ignore {
      val newThumbnailsRow = ThumbnailsRow(
        uuid,
        new Timestamp(1234687268),
        new Timestamp(1234687268),
        uuid,
        256,
        128,
        "large",
        uuid,
        "https://website.com"
      )
      val result = ThumbnailService.updateThumbnail(newThumbnailsRow, uuid)
      assert(result === 1)
      ThumbnailService.getThumbnail(uuid) map {
        case Some(resp) => assert(resp.widthPx === 256)
        case _ => Failure(new Exception("Field not updated successfully"))
      }
    }
  }

  "Deleting a row" should {
    "remove a row from the table" ignore {
      val result = ThumbnailService.deleteThumbnail(uuid)
      assert(result === 1)
    }
  }


  "/api/thumbnails/{uuid}" should {
    "return a 404 for non-existent thumbnail" ignore {
      Get(s"${baseThumbnail}${publicOrgId}") ~> thumbnailRoutes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return a thumbnail" ignore {
      val thumbnailId = ""
      Get(s"${baseThumbnail}${thumbnailId}/") ~> thumbnailRoutes ~> check {
        responseAs[ThumbnailsRow]
      }
    }

    "update a thumbnail" ignore {
      // Add change to thumbnail here
    }

    "delete a thumbnail" ignore {
      val thumbnailId = ""
      Delete(s"${baseThumbnail}${thumbnailId}/") ~> thumbnailRoutes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
  }

  "/api/thumbnails/" should {
    "not require authentication" in {
      Get("/api/thumbnails/") ~> thumbnailRoutes ~> check {
        responseAs[PaginatedResponse[ThumbnailsRow]]
      }
    }

    "filter by one scene correctly" ignore {
      Get(s"/api/thumbnails/?scene=${publicOrgId}") ~> thumbnailRoutes ~> check {
        responseAs[PaginatedResponse[ThumbnailsRow]].count shouldEqual 2
      }
    }

    "filter by one (non-existent) scene correctly" in {
      val url = s"/api/thumbnails/?scene=${fakeOrgId}"
      Get(url) ~> thumbnailRoutes ~> check {
        responseAs[PaginatedResponse[ThumbnailsRow]].count shouldEqual 0
      }
    }

    "sort by one field correctly" ignore {
      val url = s"/api/buckets/?sort=..."
      Get(url) ~> thumbnailRoutes ~> check {
        /** Sorting behavior isn't described in the spec currently but might be someday */
        responseAs[PaginatedResponse[ThumbnailsRow]]
      }
    }

    "reject all non-get requests" in {
      val url = s"/api/buckets/"
      Put(url) ~> thumbnailRoutes ~> check {
        reject
      }
      Post(url) ~> thumbnailRoutes ~> check {
        reject
      }
      Head(url) ~> thumbnailRoutes ~> check {
        reject
      }
    }
  }
}
