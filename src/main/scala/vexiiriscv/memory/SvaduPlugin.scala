package vexiiriscv.memory
import vexiiriscv.Global._
import vexiiriscv.riscv._
import spinal.core
import spinal.core._
import spinal.core.fiber.Retainer
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.misc.plugin.FiberPlugin

case class SvaduCmd()  extends Bundle {
    val permission = AddressTranslationRefillCmdPerm()
    val address = UInt(PHYSICAL_WIDTH bits)
    val data = Bits(Riscv.XLEN bits)
}

class SvaduPlugin extends FiberPlugin {

    val logic = during setup new Area {
        val dbs = host[DBusAccessService]
        val storeLock = retains(dbs.storeRetainer)

        awaitBuild()

        val storeBus = dbs.newDBusStore()
        storeLock.release()

        val cmd = Stream(SvaduCmd())
        cmd.data.assignDontCare()
        cmd.address.assignDontCare()
        cmd.permission.assignDontCare()
        cmd.valid.clear()
        cmd.ready.clear()
        storeBus.cmd.valid := False
        storeBus.cmd.size := U(log2Up(8))
        storeBus.cmd.address := cmd.address
        storeBus.cmd.data := cmd.data
        storeBus.cmd.data(6).set // PTE_A bit
        storeBus.cmd.data(7).setWhen(cmd.permission.write) // PTE_D bit
        val update = new StateMachine {
            val IDLE, CMD, RSP, DONE = new State
            setEntry(IDLE)
            IDLE whenIsActive {
                when(cmd.valid) {
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
                cmd.ready := True
                goto(IDLE)
            }
        }
    }
}
