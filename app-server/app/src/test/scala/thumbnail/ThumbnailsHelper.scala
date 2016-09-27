package com.azavea.rf.thumbnail

import java.util.UUID
import com.azavea.rf.scene.CreateScene
import com.azavea.rf.datamodel.enums._
import com.azavea.rf.AuthUtils
import java.sql.Timestamp
import java.time.Instant


trait ThumbnailSpecHelper {
  val authorization = AuthUtils.generateAuthHeader("Default")
  val baseThumbnail = "/api/thumbnails/"
  val userId = "dfac6307-b5ef-43f7-beda-b9f208bb7726"
  val publicOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726")
  val fakeOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7725")

  val newScene = CreateScene(
    publicOrgId, 0, PUBLIC, 20.2f, List("Test", "Public", "Low Resolution"), "TEST_ORG",
    Map("instrument type" -> "satellite", "splines reticulated" -> 0):Map[String, Any], None,
    Some(Timestamp.from(Instant.parse("2016-09-19T14:41:58.408544Z"))),
    PROCESSING, PROCESSING, PROCESSING, None, None, "test scene bucket"
  )

  val newThumbnail1 = CreateThumbnail(
    publicOrgId, 128, 128, "small", newScene.toScene(userId).id, "https://website.com/example.png"
  )

  val newThumbnail2 = CreateThumbnail(
    publicOrgId, 128, 128, "square", newScene.toScene(userId).id, "https://website.com/lolz.png"
  )
}
