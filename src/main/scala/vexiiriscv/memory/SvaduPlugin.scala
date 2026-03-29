package vexiiriscv.memory
import vexiiriscv.Global._
import vexiiriscv.riscv._
import spinal.core
import spinal.core._
import spinal.core.fiber.Retainer
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.misc.plugin.FiberPlugin
import vexiiriscv.misc.PrivilegedPlugin

case class SvaduCmd(requestGuest : Boolean)  extends Bundle {
    val permission = AddressTranslationRefillCmdPerm()
    val address = UInt(PHYSICAL_WIDTH bits)
    val data = Bits(Riscv.XLEN bits)
    val isTwoStage = requestGuest generate Bool()
}

case class SvaduRsp()  extends Bundle {
    val error = Bool()
}

class SvaduPlugin extends FiberPlugin {

    val logic = during setup new Area {
        val tdbs = host[TranslatedDBusAccessService]
        val priv = host[PrivilegedPlugin]
        val storeLock = retains(tdbs.accessRetainer)

        awaitBuild()

        val storeBus = tdbs.newDBusAccess(priv.implementHypervisor)
        storeLock.release()

        val rsp = Flow(SvaduRsp())
        rsp.error := False
        rsp.valid := False

        val cmd = Stream(SvaduCmd(priv.implementHypervisor))
        cmd.data.assignDontCare()
        cmd.address.assignDontCare()
        cmd.permission.assignDontCare()
        cmd.valid := False
        cmd.ready := False
        if(priv.implementHypervisor) cmd.isTwoStage.clear()
        storeBus.cmd.valid := False
        storeBus.cmd.size := U(log2Up(8))
        storeBus.cmd.address := cmd.address
        storeBus.cmd.data := cmd.data
        storeBus.cmd.data(6).set // PTE_A bit
        storeBus.cmd.data(7).setWhen(cmd.permission.write) // PTE_D bit
        storeBus.cmd.write := True
        if(priv.implementHypervisor) storeBus.cmd.guest := cmd.isTwoStage
        val update = new StateMachine {
            val IDLE, CMD, RSP, DONE = new State
            setEntry(IDLE)
            IDLE whenIsActive {
                when(cmd.valid) {
                    cmd.ready := True
                    goto(CMD)
                }
            }

            CMD whenIsActive {
                storeBus.cmd.valid := True
                when(storeBus.cmd.ready) {
                    goto(RSP)
                }
            }

            RSP whenIsActive {
                when(storeBus.rsp.valid){
                    when(storeBus.rsp.redo){
                        goto(CMD)
                    } otherwise {
                        goto(DONE)
                    }
                }
            }

            DONE whenIsActive {
                rsp.valid := True
                rsp.error := storeBus.rsp.error(1) // Only care about guest-page fault
                goto(IDLE)
            }
        }
    }
}
