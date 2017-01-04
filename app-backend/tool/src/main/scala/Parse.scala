package com.azavea.rf.tool

import spray.json._

object Defines {
  /** Functions available in curren `include` scope */
  type Scope = Map[Symbol, Op]

  type FunctionFormat = JsValue => Op
}


class FunctionParser(scope: Map[Symbol, Op]) {
  // TODO: make Division
  // TODO: make Mask

  def division(args: JsValue) = args match {
    case obj: JsObject => sys.error("not supported?")
    case arr: JsArray =>
      arr.map(name => Op.Unbound(Symbol(name)))
        .reduce(_ / _)

      // TODO: is `name` actually a valid symbol ?
      // TODO: does arr have at least two members ?
      // Should this constructo be lefted to Op ?
  }
}

object Parse {
  
  type MLTool = MultibandTile => MultibandTile

  implicit object ApplyFormat extends RootJsonFormat[Op] {
    def read(json: JsValue): Op = {
      json.asObject.getFields("apply", "args") match {
        case Seq(function, args) =>
          // dispatch to function parser, give it the args

          // args can be either list of named
          // if list map JsValue => Op

      }
    }

    def write(op: Op): JsValue = {
      ???
    }
  }

  def entryPoint(blob: String): MLTool = {
    
    
  }

  // in the end I will need MultibandTile => MultibandTile ... via Op
  // but Op is already bound to something ...

  // 1. How do I solve the binding issue
  // 2. How do I work with MultibandTile
}
