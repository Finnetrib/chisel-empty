package eht_frame_filter_pkg

import chisel3._
import chisel3.util._

import fifo_pkg._

class calc_crc extends HasBlackBoxResource {
  val io = IO (new Bundle {
    val CRC_I   = Input(UInt(8.W))
    val DATA_I  = Input(UInt(8.W))
    val CRC_O   = Output(UInt(8.W))
  } )
  addResource("calc_crc.v")
}

class AxisBus extends Bundle {
  val Tlast = Bool()    
  val Tdata = UInt(8.W)
}

class AxisCrcChecker extends Module { 
  val io = IO(new Bundle {                
    val AxisSlv = Flipped(new DecoupledIO(new AxisBus))
    val AxisMst = new DecoupledIO(new AxisBus)
  } ) 

  val PacketReceived  = Wire(Bool())
  val RacketSended    = Wire(Bool())
  val CrcInReg        = RegInit("hFF".U(8.W))
  val CrcOut          = Wire(UInt(8.W))
  val FifoRstReg      = RegInit(1.U(1.W))

  object State extends ChiselEnum {
    val ST_RECEIVE, ST_CHECK, ST_RESET, ST_SEND = Value
  }
  import State._
  val CheckerStReg = RegInit(ST_RECEIVE)

  PacketReceived := io.AxisSlv.valid & io.AxisSlv.ready & io.AxisSlv.bits.Tlast

  RacketSended := io.AxisMst.valid & io.AxisMst.ready & io.AxisMst.bits.Tlast
  
  switch (CheckerStReg) {
    is (ST_RECEIVE) {
      when (PacketReceived === 1.U) {
        CheckerStReg := ST_CHECK
      }
    }
    is (ST_CHECK) {
      when (CrcInReg === 0.U) {
        CheckerStReg := ST_SEND
      }
      .otherwise {
        CheckerStReg := ST_RESET
      }
    }
    is (ST_RESET) {
      CheckerStReg := ST_RECEIVE
    }
    is (ST_SEND) {
      when (RacketSended === 1.U) {
        CheckerStReg := ST_RECEIVE
      }
    }
  }

  val AxisSlvTready = RegInit(0.U(1.W))
  when (PacketReceived === 1.U) {
    AxisSlvTready := 0.U
  }
  .elsewhen (CheckerStReg === ST_RECEIVE) {
    AxisSlvTready := 1.U
  }

  io.AxisSlv.ready := AxisSlvTready

  when (io.AxisSlv.valid & io.AxisSlv.ready) {
    CrcInReg := CrcOut
  }
  .elsewhen (CheckerStReg === ST_CHECK) {
    CrcInReg := "hFF".U
  }

  val u_calc_crc = Module(new calc_crc())

  u_calc_crc.io.CRC_I   := CrcInReg
  u_calc_crc.io.DATA_I  := io.AxisSlv.bits.Tdata
  CrcOut                := u_calc_crc.io.CRC_O

  FifoRstReg := (CheckerStReg === ST_RESET)

  val u_sync_fifo = Module(new SyncFifo(9, 1024))
  
  u_sync_fifo.reset := (FifoRstReg === 1.U)

  u_sync_fifo.io.Writer.WriteData := io.AxisSlv.bits.Tlast ## io.AxisSlv.bits.Tdata
  u_sync_fifo.io.Writer.WriteEn := io.AxisSlv.valid & io.AxisSlv.ready

  u_sync_fifo.io.Reader.ReadEn := (CheckerStReg === ST_SEND && io.AxisMst.ready === 1.U) 

  io.AxisMst.valid := (CheckerStReg === ST_SEND && u_sync_fifo.io.Reader.Empty === 0.U)

  io.AxisMst.bits.Tdata := u_sync_fifo.io.Reader.ReadData(7, 0)
  io.AxisMst.bits.Tlast := u_sync_fifo.io.Reader.ReadData(8)
}

object AxisCrcCheckerMain extends App {
  println("Generating the hardware")
  emitVerilog(new AxisCrcChecker(), Array("--target-dir", "generated"))
}
