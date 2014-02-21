package hwacha

import Chisel._
import Node._
import Constants._

class io_irq_to_issue extends Bundle
{
  val stall_tvec = Bool(OUTPUT)
  val stall_vf = Bool(OUTPUT)
}

class IRQ(resetSignal: Bool = null) extends Module(_reset = resetSignal)
{
  val io = new Bundle {
    val issue_to_irq = new io_issue_to_irq_handler().flip
    val vmu_to_irq = new io_vmu_to_irq_handler().flip

    val irq = Bool(OUTPUT)
    val irq_cause = UInt(OUTPUT, 5)
    val irq_aux = Bits(OUTPUT, 64)
  }

  val reg_irq_ma_inst = Reg(init=Bool(false))
  val reg_irq_fault_inst = Reg(init=Bool(false))
  val reg_irq_illegal_vt = Reg(init=Bool(false))
  val reg_irq_illegal_tvec = Reg(init=Bool(false))
  val reg_irq_pc = Reg(Bits(width = SZ_ADDR))
  val reg_irq_cmd_tvec = Reg(Bits(width = SZ_VCMD))

  val reg_irq_ma_ld = Reg(init=Bool(false))
  val reg_irq_ma_st = Reg(init=Bool(false))
  val reg_irq_faulted_ld = Reg(init=Bool(false))
  val reg_irq_faulted_st = Reg(init=Bool(false))
  val reg_mem_xcpt_addr = Reg(Bits(width = SZ_ADDR))

  when (!io.irq)
  {
    reg_irq_ma_inst := io.issue_to_irq.vt.ma_inst
    reg_irq_fault_inst := io.issue_to_irq.vt.fault_inst
    reg_irq_illegal_vt := io.issue_to_irq.vt.illegal
    reg_irq_illegal_tvec := io.issue_to_irq.tvec.illegal

    reg_irq_ma_ld := io.vmu_to_irq.ma_ld
    reg_irq_ma_st := io.vmu_to_irq.ma_st
    reg_irq_faulted_ld := io.vmu_to_irq.faulted_ld
    reg_irq_faulted_st := io.vmu_to_irq.faulted_st

    when (io.issue_to_irq.vt.ma_inst || io.issue_to_irq.vt.fault_inst || io.issue_to_irq.vt.illegal) { reg_irq_pc := io.issue_to_irq.vt.pc }
    when (io.issue_to_irq.tvec.illegal) { reg_irq_cmd_tvec := io.issue_to_irq.tvec.cmd }

    when (io.vmu_to_irq.ma_ld || io.vmu_to_irq.ma_st || io.vmu_to_irq.faulted_ld || io.vmu_to_irq.faulted_st) 
    {
      reg_mem_xcpt_addr := io.vmu_to_irq.mem_xcpt_addr
    }
  }

  val dmem_xcpt = reg_irq_ma_ld || reg_irq_ma_st || reg_irq_faulted_ld || reg_irq_faulted_st

  io.irq := reg_irq_ma_inst || reg_irq_fault_inst || reg_irq_illegal_vt || reg_irq_illegal_tvec || dmem_xcpt

  io.irq_cause :=
    Mux(reg_irq_ma_inst, UInt(4),
    Mux(reg_irq_fault_inst, UInt(5),
    Mux(reg_irq_illegal_vt, UInt(6),
    Mux(reg_irq_illegal_tvec, UInt(1),
    Mux(reg_irq_ma_ld, UInt(8),
    Mux(reg_irq_ma_st, UInt(9),
    Mux(reg_irq_faulted_ld, UInt(10),
    Mux(reg_irq_faulted_st, UInt(11),
        UInt(1)))))))))

  io.irq_aux :=
    Mux(reg_irq_ma_inst || reg_irq_fault_inst || reg_irq_illegal_vt, reg_irq_pc,
    Mux(reg_irq_illegal_tvec, reg_irq_cmd_tvec,
    Mux(dmem_xcpt, reg_mem_xcpt_addr,
        Bits(0))))
}
