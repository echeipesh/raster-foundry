package com.azavea.rf.tile.image

import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.ScenesToProjects
import com.azavea.rf.datamodel.MosaicDefinition
import com.azavea.rf.common.cache._

import com.github.blemale.scaffeine.{ Cache => ScaffeineCache, Scaffeine }
import com.azavea.rf.tile._
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.raster.GridBounds
import geotrellis.proj4._
import geotrellis.slick.Projected
import geotrellis.vector.{Polygon, Extent}
import cats.data._
import cats.implicits._
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._


case class TagWithTTL(tag: String, ttl: Duration)

object Mosaic {
  val memcachedClient = LayerCache.memcachedClient
  val memcached = HeapBackedMemcachedClient(LayerCache.memcachedClient)

  /** Cache the result of metadata queries that may have required walking up the pyramid to find suitable layers */

  /** The caffeine cache to use for tile layer metadata */
  val tileLayerMetadataCache: ScaffeineCache[String, Future[Option[(Int, TileLayerMetadata[SpatialKey])]]] =
    Scaffeine()
      .recordStats()
      .expireAfterAccess(5.minutes)
      .maximumSize(500)
      .build[String, Future[Option[(Int, TileLayerMetadata[SpatialKey])]]]()

  def tileLayerMetadata(id: UUID, zoom: Int)(implicit database: Database): OptionT[Future, (Int, TileLayerMetadata[SpatialKey])] = {
    // TODO: use a way to get the closest zoom level without multiple reads
    def readMetadata(store: AttributeStore, tryZoom: Int): Option[(Int, TileLayerMetadata[SpatialKey])] =
      try {
        Some(tryZoom -> store.readMetadata[TileLayerMetadata[SpatialKey]](LayerId(id.toString, tryZoom)))
      } catch {
        case e: AttributeNotFoundError if tryZoom > 0 => readMetadata(store, tryZoom - 1)
      }

    memcached.cachingOptionT[(Int, TileLayerMetadata[SpatialKey])](s"mosaic-tlm-$id-$zoom") { _ =>
      LayerCache.attributeStoreForLayer(id).mapFilter { store => readMetadata(store, zoom) }
    }
  }

  // TODO: TTL in tagttl does nothing, default memcachd TTL is used anyway
  def mosaicDefinition(projectId: UUID, tagttl: Option[TagWithTTL])(implicit db: Database): OptionT[Future, MosaicDefinition] = {
    val cacheKey = tagttl match {
      case Some(t) => s"mosaic-definition-$projectId-${t.tag}"
      case None => s"mosaic-definition-$projectId"
    }

    memcached.cachingOptionT(cacheKey) { _ =>
      OptionT(ScenesToProjects.getMosaicDefinition(projectId))
    }
  }

  /** Fetch the tile for given resolution. If it is not present, use a tile from a lower zoom level */
  def fetch(id: UUID, zoom: Int, col: Int, row: Int)(implicit database: Database): OptionT[Future, MultibandTile] =
    for {
      (sourceZoom, tlm) <- tileLayerMetadata(id, zoom)
      zoomDiff = zoom - sourceZoom
      resolutionDiff = 1 << zoomDiff
      sourceKey = SpatialKey(col / resolutionDiff, row / resolutionDiff)
      tile <- LayerCache.layerTile(id, sourceZoom, sourceKey) if tlm.bounds.includes(sourceKey)
    } yield {
      val innerCol = col % resolutionDiff
      val innerRow = row % resolutionDiff
      val cols = tile.cols / resolutionDiff
      val rows = tile.rows / resolutionDiff
      tile.crop(GridBounds(
        colMin = innerCol * cols,
        rowMin = innerRow * rows,
        colMax = (innerCol + 1) * cols - 1,
        rowMax = (innerRow + 1) * rows - 1
      )).resample(256, 256)
    }

  /** Fetch the rendered tile for the given zoom level and bbox
    * If no bbox is specified, it will use the project tileLayerMetadata layoutExtent
    */
  def fetchRenderedExtent(id: UUID, zoom: Int, bbox: Option[Projected[Polygon]])(implicit database: Database): OptionT[Future,MultibandTile] = {
    tileLayerMetadata(id, zoom).flatMap { case (sourceZoom, tlm) =>
      val extent: Extent =
        bbox.map { case Projected(poly, srid) =>
          poly.envelope.reproject(CRS.fromEpsgCode(srid), tlm.crs)
        }.getOrElse(tlm.layoutExtent)
      LayerCache.layerTileForExtent(id, sourceZoom, extent)
    }
  }

  /** Fetch all bands of a [[MultibandTile]] and return them without assuming anything of their semantics */
  def raw(projectId: UUID, zoom: Int, col: Int, row: Int)(implicit db: Database): OptionT[Future, MultibandTile] = {
    mosaicDefinition(projectId, None).flatMap { mosaic =>
      val mayhapTiles: Seq[OptionT[Future, MultibandTile]] =
        for ((sceneId, _) <- mosaic.definition) yield
          for (tile <- Mosaic.fetch(sceneId, zoom, col, row)) yield
            tile

      val futureMergeTile: Future[Option[MultibandTile]] =
        Future.sequence(mayhapTiles.map(_.value)).map { maybeTiles =>
          val tiles = maybeTiles.flatten
          if (tiles.nonEmpty)
            Option(tiles.reduce(_ merge _))
          else
            Option.empty[MultibandTile]
        }

      OptionT(futureMergeTile)
    }
  }

  /**   Render a png from TMS pyramids given that they are in the same projection.
    *   If a layer does not go up to requested zoom it will be up-sampled.
    *   Layers missing color correction in the mosaic definition will be excluded.
    *   The size of the image will depend on the selected zoom and bbox.
    *
    *   Note:
    *   Currently, if the render takes too long, it will time out. Given enough requests, this
    *   could cause us to essentially ddos ourselves, so we probably want to change
    *   this from a simple endpoint to an airflow operation: IE the request kicks off
    *   a render job then returns the job id
    *
    *   @param zoomOption  the zoom level to use
    *   @param bboxOption the bounding box for the image
    */
  def render(projectId: UUID, zoomOption: Option[Int], bboxOption: Option[String])(implicit database: Database): OptionT[Future, MultibandTile] = {
    val bboxPolygon: Option[Projected[Polygon]] =
      try {
        bboxOption map { bbox =>
          Projected(Extent.fromString(bbox).toPolygon(), 4326).reproject(LatLng, WebMercator)(3858)
        }
      } catch {
        case e: Exception =>
          throw new IllegalArgumentException("Four comma separated coordinates must be given for bbox").initCause(e)
      }

    val zoom: Int = zoomOption.getOrElse(8)

    mosaicDefinition(projectId, None).flatMap { mosaic =>
      val mayhapTiles: Seq[OptionT[Future, MultibandTile]] =
        mosaic.definition.flatMap { case (sceneId, maybeColorCorrectParams) =>
          maybeColorCorrectParams.map { colorCorrectParams =>
            Mosaic.fetchRenderedExtent(sceneId, zoom, bboxPolygon).flatMap { tile =>
              LayerCache.layerHistogram(sceneId, zoom).map { hist =>
                colorCorrectParams.colorCorrect(tile, hist)
              }
            }
          }.toSeq
        }

      val futureMergeTile: Future[Option[MultibandTile]] =
        Future.sequence(mayhapTiles.map(_.value)).map { maybeTiles =>
          val tiles = maybeTiles.flatten
          if (tiles.nonEmpty)
            Option(tiles.reduce(_ merge _))
          else
            Option.empty[MultibandTile]
        }

      OptionT(futureMergeTile)
    }
  }

  /** Mosaic tiles from TMS pyramids given that they are in the same projection.
    *   If a layer does not go up to requested zoom it will be up-sampled.
    *   Layers missing color correction in the mosaic definition will be excluded.
    *
    *   @param rgbOnly  This parameter determines whether or not the mosaic should return an RGB
    *                    MultibandTile or all available bands, regardless of their semantics.
    */
  def apply(
    projectId: UUID,
    zoom: Int, col: Int, row: Int,
    tag: Option[String] = None,
    rgbOnly: Boolean = true
  )(
    implicit db: Database
  ): OptionT[Future, MultibandTile] = {
    // Lookup project definition
    // tag present, include in lookup to re-use cache
    // no tag to control cache rollover, so don't cache
    mosaicDefinition(projectId, tag.map(s => TagWithTTL(tag=s, ttl=60.seconds))).flatMap { mosaic =>
      val mayhapTiles: Seq[OptionT[Future, MultibandTile]] =
        mosaic.definition.flatMap { case (sceneId, maybeColorCorrectParams) =>
          if (rgbOnly) {
            maybeColorCorrectParams.map { colorCorrectParams =>
              Mosaic.fetch(sceneId, zoom, col, row).flatMap { tile =>
                LayerCache.layerHistogram(sceneId, zoom).map { hist =>
                  colorCorrectParams.colorCorrect(tile, hist)
                }
              }
            }.toSeq
          } else {
            // Wrap in List so it can flattened by the same flatMap above
            List(Mosaic.fetch(sceneId, zoom, col, row))
          }
        }

      val futureMergeTile: Future[Option[MultibandTile]] =
        Future.sequence(mayhapTiles.map(_.value)).map { maybeTiles =>
          val tiles = maybeTiles.flatten
          if (tiles.nonEmpty)
            Option(tiles.reduce(_ merge _))
          else
            Option.empty[MultibandTile]
        }

      OptionT(futureMergeTile)
    }
  }
}
