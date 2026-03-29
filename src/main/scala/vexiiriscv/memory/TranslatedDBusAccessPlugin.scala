package vexiiriscv.memory

import spinal.core._
import spinal.lib._
import spinal.lib.misc.plugin.FiberPlugin
import spinal.lib.fsm.StateMachine
import spinal.lib.fsm.State

class TranslatedDBusAccessPlugin() extends FiberPlugin with TranslatedDBusAccessService {
  override def accessRefillCount: Int = 0
  override def accessWake: Bits = B(0)

  val logic = during setup new Area{
    val access = host[DBusAccessService]
    val ats = host.find[AddressTranslationService](_.isShadowMmu)
    val accessLock = retains(access.accessRetainer, access.storeRetainer)
    val withAtsRedo = ats.mayNeedRedo
    val atsPortsLock = retains(ats.portsLock)

    awaitBuild()

    val accessBus = access.newDBusAccess()
    val storeBus = access.newDBusStore()
    accessLock.release()

    val atsPort = withAtsRedo generate ats.newRefillPort()
    atsPortsLock.release()

    accessRetainer.await()

    if (withAtsRedo) {
      atsPort.cmd.valid               := False
      atsPort.cmd.address             := U(0)
      atsPort.cmd.indirect            := True
      atsPort.cmd.storageEnable       := False
      atsPort.cmd.storageId           := U(0)
      atsPort.cmd.permission.read     := True
      atsPort.cmd.permission.write    := False
      atsPort.cmd.permission.execute  := False
      atsPort.rsp.ready               := False
    }

    val cmd = accessBus.cmd
    val rsp = accessBus.rsp

    val storeCmd = storeBus.cmd
    val storeRsp = storeBus.rsp
  
    cmd.valid     := False
    cmd.address   := U(0)
    cmd.size      := U(0)

    storeCmd.valid     := False
    storeCmd.address   := U(0)
    storeCmd.size      := U(0)
    storeCmd.data.assignDontCare()

    for (tda <- dbusAccesses) {
      tda.rsp.valid := False
      tda.rsp.error := B(0)
      tda.rsp.data.assignDontCare()
      tda.rsp.redo.assignDontCare()
      tda.rsp.waitSlot.assignDontCare()
      tda.rsp.waitAny.assignDontCare()
    }

    val fsm = for (tda <- dbusAccesses) yield new StateMachine {
      val generateTransPort = withAtsRedo && tda.requestGuest
      val CMD, RSP = new State
      val ATS = new State
      val tcmd = tda.cmd
      val trsp = tda.rsp
      val size = generateTransPort generate Reg(cloneOf(tcmd.size))
      val data = generateTransPort generate Reg(cloneOf(tcmd.data))

      setEntry(CMD)

      tcmd.ready := False

      CMD whenIsActive {
        when(tcmd.valid) {
          val guestCtx = WhenBuilder()
          if(generateTransPort) guestCtx.when(tcmd.guest) {
            atsPort.cmd.valid   := True
            atsPort.cmd.address := tcmd.address.resized
            atsPort.cmd.permission.write := tcmd.write
            when(atsPort.cmd.ready) {
              tcmd.ready  := True
              size        := tcmd.size
              data        := tcmd.data
              goto(ATS)
            }
          }
          guestCtx.otherwise {
            cmd.address   := tcmd.address
            cmd.size      := tcmd.size
            storeCmd.address   := tcmd.address
            storeCmd.size      := tcmd.size
            storeCmd.data      := tcmd.data
            when(tcmd.write) {
              storeCmd.valid := True
              when (storeCmd.ready) {
                tcmd.ready := True
                goto(RSP)
              }
            } otherwise {
              cmd.valid := True
              when (cmd.ready) {
                tcmd.ready := True
                goto(RSP)
              }
            }
          }
        }
      }

      if(generateTransPort) ATS whenIsActive {
        when(atsPort.rsp.valid) {
          /* check permission */
          when (!atsPort.rsp.bypass && atsPort.rsp.pageFault || atsPort.rsp.accessFault) {
            trsp.valid          := True
            trsp.data           := atsPort.rsp.address.asBits.resized
            trsp.error(1)       := atsPort.rsp.pageFault
            trsp.error(0)       := atsPort.rsp.accessFault
            trsp.redo           := False
            trsp.waitSlot       := B(0)
            trsp.waitAny        := False

            atsPort.rsp.ready   := True
            goto(CMD)
          } otherwise {
            cmd.address   := atsPort.rsp.address
            cmd.size      := size
            storeCmd.address   := atsPort.rsp.address
            storeCmd.size      := size
            storeCmd.data      := data
            when(tcmd.write) {
              storeCmd.valid := True
              when (storeCmd.ready) {
                atsPort.rsp.ready := True
                goto(RSP)
              }
            } otherwise {
              cmd.valid := True
              when (cmd.ready) {
                atsPort.rsp.ready := True
                goto(RSP)
              }
            }
          }
        }
      }

      RSP whenIsActive {
        when(tcmd.write) {
          trsp.valid        := storeRsp.valid
          trsp.data         := B(0)
          trsp.error(0)     := storeRsp.error
          trsp.redo         := storeRsp.redo
          trsp.waitSlot     := B(0)
          trsp.waitAny      := False
          when (storeRsp.valid) {
            goto(CMD)
          }
        } otherwise {
          trsp.valid        := rsp.valid
          trsp.data         := rsp.data
          trsp.error(0)     := rsp.error
          trsp.redo         := rsp.redo
          trsp.waitSlot     := rsp.waitSlot
          trsp.waitAny      := rsp.waitAny
          when (rsp.valid) {
            goto(CMD)
          }
        }
      }
    }

  }
}
