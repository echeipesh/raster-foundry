package com.azavea.rf.tool

import spray.json._
import geotrellis.raster.op._

object Defines {
  // Functions available in curren `include` scope
  type Scope = Map[Symbol, Op]
  type FunctionFormat = JsValue => Op
}

class FunctionParser(scope: Map[Symbol, Op]) {
  // TODO: make Division
  // TODO: make Mask

  def division(args: JsValue) = args match {
    case obj: JsObject => sys.error("not supported?")
    case arr: JsArray =>
      // TODO: is `name` actually a valid symbol ?
      // TODO: does arr have at least two members ?
      // Should this constructo be lefted to Op ?
      ???
    case _ =>
      throw new DeserializationException("bad arg format")
  }
}

object OpParser {

  implicit object ApplyFormat extends RootJsonFormat[Op] {
    def read(json: JsValue): Op = {
      json.asJsObject.getFields("apply", "args") match {
        case Seq(function, args) =>
          // dispatch to function parser, give it the args

          // args can be either list of named
          // if list map JsValue => Op
          ???
      }
    }

    def write(op: Op): JsValue = {
      ???
    }
  }

  def parse(blog: String): Op = ???

  // in the end I will need MultibandTile => MultibandTile ... via Op
  // but Op is already bound to something ...

  // 1. How do I solve the binding issue
  // 2. How do I work with MultibandTile
}
