package fifo_pkg

import chisel3._
import chisel3.util._

class WriterIO (DataWidth: Int) extends Bundle {
  val WriteEn   = Input(Bool())
  val WriteData = Input(UInt(DataWidth.W))
  val Full      = Output(Bool())
}

class ReaderIO (DataWidth: Int) extends Bundle {
  val ReadEn   = Input(Bool())
  val ReadData = Output(UInt(DataWidth.W))
  val Empty   = Output(Bool())
}

class SyncFifo (DataWidth: Int, FifoDepth: Int) extends Module {
  val io = IO(new Bundle {
    val Writer = new WriterIO(DataWidth)
    val Reader = new ReaderIO(DataWidth)
  } )

  val ADDR_WIDTH = unsignedBitLength(FifoDepth);

  val WritePtr = RegInit(0.U((ADDR_WIDTH).W))
  val ReadPtr = RegInit(0.U((ADDR_WIDTH).W))
  val FifoCell = Reg(Vec((FifoDepth), UInt(DataWidth.W)))

  io.Writer.Full  := (  (WritePtr(ADDR_WIDTH - 1) =/= ReadPtr(ADDR_WIDTH - 1)) && 
                        (WritePtr(ADDR_WIDTH - 2, 0) === ReadPtr(ADDR_WIDTH - 2, 0)))
  io.Reader.Empty := (WritePtr === ReadPtr)
  
  when (io.Writer.Full === 0.U && io.Writer.WriteEn === 1.U) {
    WritePtr := WritePtr + 1.U
  }

  when (io.Reader.Empty === 0.U && io.Reader.ReadEn === 1.U) {
    ReadPtr := ReadPtr + 1.U
  }
  
  when (io.Writer.Full === 0.U && io.Writer.WriteEn === 1.U) {
    FifoCell(WritePtr(ADDR_WIDTH - 2, 0)) := io.Writer.WriteData;
  }

  io.Reader.ReadData := FifoCell(ReadPtr(ADDR_WIDTH - 2, 0))
}