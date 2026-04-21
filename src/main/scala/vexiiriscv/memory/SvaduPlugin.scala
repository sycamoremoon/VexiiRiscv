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

import scala.collection.mutable.ArrayBuffer
case class SvaduStore(requestGuest : Boolean) extends Bundle {
  val cmd = Stream(SvaduCmd(requestGuest))
  val rsp = Stream(SvaduRsp())
}

case class SvaduCmd(requestGuest : Boolean)  extends Bundle {
    val permission = AddressTranslationRefillCmdPerm()
    val address = UInt(PHYSICAL_WIDTH bits)
    val data = Bits(Riscv.XLEN bits)
    val isTwoStage = requestGuest generate Bool()
}

case class SvaduRsp()  extends Bundle {
    val error = Bits(2 bits)
}

class SvaduPlugin extends FiberPlugin {
    def newPort(requestGuest : Boolean) : SvaduStore = svaduStores.addRet(new SvaduStore(requestGuest))
    val svaduStores = ArrayBuffer[SvaduStore]()
    val svaduRetainer = Retainer()
    val storeBuses = ArrayBuffer[TranslatedDBusAccess]()

    val logic = during setup new Area {
        val tdbs = host[TranslatedDBusAccessService]
        val priv = host[PrivilegedPlugin]
        val storeLock = retains(tdbs.accessRetainer)

        awaitBuild()
        svaduRetainer.await()
        for ((store, idx) <- svaduStores.zipWithIndex) {
            val mode = if(store.requestGuest) "guest" else "host"
            val bus = tdbs.newDBusAccess(store.requestGuest).setName(s"svaduBus_${mode}_${idx}")
            storeBuses += bus
        }
        storeLock.release()

        val store = for ((store, storeBus) <- svaduStores.zip(storeBuses)) yield new Area {
            val busRsp = storeBus.rsp.toStream.stage()
            val busCmd = storeBus.cmd
            busCmd.valid   := False
            busCmd.write   := True
            busCmd.data    := B(0)
            busCmd.address := U(0)
            busCmd.size    := U(log2Up(Riscv.XLEN/8))
            busRsp.ready := False
            if(store.requestGuest) busCmd.guest := False
            store.rsp.valid := False
            store.rsp.error := B(0)

            val fsm = new StateMachine {
                val IDLE, CMD, RSP = new State
                val cmd = Reg(cloneOf(store.cmd))
                setEntry(IDLE)
                store.cmd.ready := False

                IDLE whenIsActive {
                    when(store.cmd.valid) {
                        store.cmd.ready := True
                        cmd := store.cmd
                        goto(CMD)
                    }
                }

                CMD whenIsActive {
                    busCmd.valid := True
                    busCmd.data := cmd.data
                    busCmd.data(6).set // PTE_A bit
                    busCmd.data(7).setWhen(cmd.permission.write) // PTE_D bit
                    if(store.requestGuest) busCmd.guest := cmd.isTwoStage
                    busCmd.address := cmd.address
                    when(busCmd.ready) {
                        goto(RSP)
                    }
                }

                RSP whenIsActive {
                    when(busRsp.valid){
                        when(busRsp.redo){
                            busRsp.ready := True
                            goto(CMD)
                        } otherwise {
                            store.rsp.valid := True
                            store.rsp.error := busRsp.error
                            when(store.rsp.ready) {
                                busRsp.ready := True
                                goto(IDLE)
                            }
                        }
                    }
                }
            }
        }
    }
}
