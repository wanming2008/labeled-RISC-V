package uncore.pard

import chisel3._
import chisel3.util._


/**
 * Provide generic method to construct
 * pipeline with given depth.
 */
trait HasPipeline {
  this: Module =>

  def zeroInit[D <: Data](data: D) = {
    val init = 0.U(data.getWidth.W).asTypeOf(data)
    require(init.isWidthKnown)
    init
  }

  // from_outer => ... => to_stab
  def pipelined[D <: Data](from: D, deep: Int) = {
    val init = zeroInit(from)
    val regs = from +: Seq.fill(deep){ RegInit(from.cloneType, init) }
    for (i <- 1 until regs.size) {
      regs(i) := regs(i - 1)
    }
    regs.last
  }
}
