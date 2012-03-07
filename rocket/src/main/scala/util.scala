package rocket

import Chisel._
import Node._
import scala.math._


object foldR
{
  def apply[T <: Bits](x: Seq[T])(f: (T, T) => T): T =
    if (x.length == 1) x(0) else f(x(0), foldR(x.slice(1, x.length))(f))
}

object log2up
{
  def apply(in: Int) = if (in == 1) 1 else ceil(log(in)/log(2)).toInt
}

object ispow2
{
  def apply(in: Int) = in > 0 && ((in & (in-1)) == 0)
}

object FillInterleaved
{
  def apply(n: Int, in: Bits) =
  {
    var out = Fill(n, in(0))
    for (i <- 1 until in.getWidth)
      out = Cat(Fill(n, in(i)), out)
    out
  }
}

// http://aggregate.ee.engr.uky.edu/MAGIC/#Population%20Count%20%28Ones%20Count%29
// http://bits.stephan-brumme.com/countBits.html
object PopCount
{
  def apply(in: Bits) =
  {
    require(in.width <= 32)
    val w = log2up(in.width+1)
    var x = in
    if(in.width == 2) { 
      x = x - ((x >> UFix(1)) & Bits("h_5555_5555"))
    } else if(in.width <= 4) {
      x = x - ((x >> UFix(1)) & Bits("h_5555_5555"))
      x = (((x >> UFix(2)) & Bits("h_3333_3333")) + (x & Bits("h_3333_3333")))
    } else if(in.width <= 8) {
      x = x - ((x >> UFix(1)) & Bits("h_5555_5555"))
      x = (((x >> UFix(2)) & Bits("h_3333_3333")) + (x & Bits("h_3333_3333")))
      x = ((x >> UFix(4)) + x) 
    } else {
      // count bits of each 2-bit chunk
      x = x - ((x >> UFix(1)) & Bits("h_5555_5555"))
      // count bits of each 4-bit chunk
      x = (((x >> UFix(2)) & Bits("h_3333_3333")) + (x & Bits("h_3333_3333")))
      // count bits of each 8-bit chunk
      x = ((x >> UFix(4)) + x) 
      // mask junk in upper bits
      x = x & Bits("h_0f0f_0f0f")
      // add all four 8-bit chunks
      x = x + (x >> UFix(8))
      x = x + (x >> UFix(16))
    }
    x(w-1,0)
  }
}

object Reverse
{
  def apply(in: Bits) =
  {
    var out = in(in.getWidth-1)
    for (i <- 1 until in.getWidth)
      out = Cat(in(in.getWidth-i-1), out)
    out
  }
}

object OHToUFix
{
  def apply(in: Bits): UFix = 
  {
    val out = MuxCase( UFix(0), (0 until in.getWidth).map( i => (in(i).toBool, UFix(i))))
    out.toUFix
  }
  def apply(in: Seq[Bool]): UFix = 
  {
    val out = MuxCase( UFix(0), in.zipWithIndex map {case (b,i) => (b, UFix(i))})
    out.toUFix
  }
}

object UFixToOH
{
  def apply(in: UFix, width: Int): Bits =
  {
    (UFix(1) << in(log2up(width)-1,0))
  }
}

object LFSR16
{
  def apply(increment: Bool = Bool(true)) =
  {
    val width = 16
    val lfsr = Reg(resetVal = UFix(1, width))
    when (increment) { lfsr := Cat(lfsr(0)^lfsr(2)^lfsr(3)^lfsr(5), lfsr(width-1,1)).toUFix }
    lfsr
  }
}

object ShiftRegister
{
  def apply [T <: Data](n: Int, in: T): T =
    if (n > 0) Reg(apply(n-1, in)) else in
}

object Mux1H 
{
//TODO: cloning in(0) is unsafe if other elements have different widths, but
//is that even allowable?
  def apply [T <: Data](n: Int, sel: Vec[Bool], in: Vec[T]): T = {
    MuxCase(in(0), (0 until n).map( i => (sel(i), in(i))))
//    val mux = (new Mux1H(n)){ in(0).clone }
//    mux.io.sel <> sel
//    mux.io.in <> in
//    mux.io.out.asInstanceOf[T]
  }

  def apply [T <: Data](n: Int, sel: Seq[Bool], in: Vec[T]): T = {
    MuxCase(in(0), (0 until n).map( i => (sel(i), in(i))))
//    val mux = (new Mux1H(n)){ in(0).clone }
//    for(i <- 0 until n) {
//      mux.io.sel(i) := sel(i)
//    }
//    mux.io.in <> in.asOutput
//    mux.io.out.asInstanceOf[T]
  }

  def apply [T <: Data](n: Int, sel: Bits, in: Vec[T]): T = {
    MuxCase(in(0), (0 until n).map( i => (sel(i).toBool, in(i))))
//    val mux = (new Mux1H(n)){ in(0).clone }
//    for(i <- 0 until n) {
//      mux.io.sel(i) := sel(i).toBool
//    }
//    mux.io.in := in
//    mux.io.out
  }
}

class Mux1H [T <: Data](n: Int)(gen: => T) extends Component
{
  val io = new Bundle {
    val sel = Vec(n) { Bool(dir = INPUT) }
    val in  = Vec(n) { gen }.asInput
    val out = gen.asOutput
  }

  def buildMux(sel: Bits, in: Vec[T], i: Int, n: Int): T = {
    if (n == 1)
      in(i)
    else
    {
      val half_n = (1 << log2up(n))/2
      val left = buildMux(sel, in, i, half_n)
      val right = buildMux(sel, in, i + half_n, n - half_n)
      Mux(sel(i+n-1,i+half_n).orR, right, left)
    }
  }

  io.out := buildMux(io.sel.toBits, io.in, 0, n)
}


class ioDecoupled[+T <: Data]()(data: => T) extends Bundle
{
  val ready = Bool(INPUT)
  val valid = Bool(OUTPUT)
  val bits  = data.asOutput
}

class ioPipe[+T <: Data]()(data: => T) extends Bundle
{
  val valid = Bool(OUTPUT)
  val bits = data.asOutput
}

class ioArbiter[T <: Data](n: Int)(data: => T) extends Bundle {
  val in  = Vec(n) { (new ioDecoupled()) { data } }.flip
  val out = (new ioDecoupled()) { data }
}

class Arbiter[T <: Data](n: Int)(data: => T) extends Component {
  val io = new ioArbiter(n)(data)

  io.in(0).ready := io.out.ready
  for (i <- 1 to n-1) {
    io.in(i).ready := !io.in(i-1).valid && io.in(i-1).ready
  }

  var dout = io.in(n-1).bits
  for (i <- 1 to n-1)
    dout = Mux(io.in(n-1-i).valid, io.in(n-1-i).bits, dout)

  var vout = io.in(0).valid
  for (i <- 1 to n-1)
    vout = vout || io.in(i).valid

  vout <> io.out.valid
  dout <> io.out.bits
}

class ioLockingArbiter[T <: Data](n: Int)(data: => T) extends Bundle {
  val in   = Vec(n) { (new ioDecoupled()) { data } }.flip
  val lock = Vec(n) { Bool() }.asInput
  val out  = (new ioDecoupled()) { data }
}

class LockingArbiter[T <: Data](n: Int)(data: => T) extends Component {
  val io = new ioLockingArbiter(n)(data)
  val locked = Vec(n) { Reg(resetVal = Bool(false)) }
  var dout = io.in(0).bits
  var vout = Bool(false)

  for (i <- 0 until n) {
    io.in(i).ready := io.out.ready
  }

  val any_lock_held = (locked.toBits & io.lock.toBits).orR
  when(any_lock_held) {
    vout = io.in(0).valid && locked(0)
    for (i <- 0 until n) {
      io.in(i).ready := io.out.ready && locked(i)
      dout = Mux(locked(i), io.in(i).bits, dout)
      vout = vout || io.in(i).valid && locked(i)
    }
  } .otherwise {
    io.in(0).ready := io.out.ready
    locked(0) := io.out.ready && io.lock(0)
    for (i <- 1 until n) {
      io.in(i).ready := !io.in(i-1).valid && io.in(i-1).ready
      locked(i) := !io.in(i-1).valid && io.in(i-1).ready && io.lock(i)
    }

    dout = io.in(n-1).bits
    for (i <- 1 until n)
      dout = Mux(io.in(n-1-i).valid, io.in(n-1-i).bits, dout)

    vout = io.in(0).valid
    for (i <- 1 until n)
      vout = vout || io.in(i).valid
  }

  vout <> io.out.valid
  dout <> io.out.bits
}

object PriorityEncoder
{
  def apply(in: Bits, n: Int = 0): UFix = {
    if (n >= in.getWidth-1)
      UFix(n)
    else
      Mux(in(n), UFix(n), PriorityEncoder(in, n+1))
  }
}
