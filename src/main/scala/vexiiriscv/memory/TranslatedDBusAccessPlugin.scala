package vexiiriscv.memory

import spinal.core._
import spinal.lib._
import spinal.lib.misc.plugin.FiberPlugin
import spinal.lib.fsm.StateMachine
import spinal.lib.fsm.State
import vexiiriscv.Global
import vexiiriscv.misc.{PerformanceCounterService, TrapPlugin}

class TranslatedDBusAccessPlugin(translationStorageParameter: MmuStorageParameter) extends FiberPlugin with TranslatedDBusAccessService {
  override def accessRefillCount: Int = 0
  override def accessWake: Bits = B(0)

  val logic = during setup new Area{
    val access = host[DBusAccessService]
    val ats = host.find[AddressTranslationService](_.isShadowMmu)
    val accessLock = retains(access.accessRetainer)
    val withAtsRedo = ats.mayNeedRedo
    val enableQuery = translationStorageParameter != null
    val withQuery = ats.allowInternalTranslation && withAtsRedo && enableQuery
    val atsPortsLock = retains(ats.portsLock)
    val atsStorageLock = retains(ats.storageLock)

    awaitBuild()

    val accessBus = access.newDBusAccess()
    accessLock.release()

    val cmd = accessBus.cmd
    val rsp = accessBus.rsp

    cmd.valid     := False
    cmd.address   := U(0)
    cmd.size      := U(0)

    val queryStorage = withQuery generate ats.newStorage(translationStorageParameter, PerformanceCounterService.DCACHE_TLB_CYCLES)
    val storageId = withQuery generate ats.getStorageId(queryStorage)
    atsStorageLock.release()

    val tcmdCached = Reg(TranslatedDBusAccessCmd(true))

    val req = InternalAddressTranslationReq(
      address = tcmdCached.address,
      load    = True,
      store   = False,
      execute = False
    )

    val atsPort = withAtsRedo generate ats.newRefillPort()
    val queryPort = withQuery generate ats.newInternalTranslationPort(
      req         = req,
      storageSpec = queryStorage
    )
    atsPortsLock.release()

    accessRetainer.await()

    if (withAtsRedo) {
      atsPort.cmd.valid               := False
      atsPort.cmd.address             := U(0)
      atsPort.cmd.indirect            := True
      atsPort.cmd.storageEnable       := True
      atsPort.cmd.storageId           := withQuery.mux(U(storageId), U(0))
      atsPort.cmd.permission.read     := True
      atsPort.cmd.permission.write    := False
      atsPort.cmd.permission.execute  := False
      atsPort.rsp.ready               := False
    }

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
      val ATS, TLB = new State
      val tcmd = tda.cmd
      val trsp = tda.rsp
      val size = generateTransPort generate Reg(cloneOf(tcmd.size))
      val counter = Counter(1)
      val counterFlow = RegNext(counter.willOverflow)

      setEntry(CMD)

      tcmd.ready := False

      def guestCmd(): Unit = if (generateTransPort) {
        val tcmdPayload = if (withQuery) tcmdCached else tcmd.payload
        atsPort.cmd.valid   := True
        atsPort.cmd.address := tcmdPayload.address.resized
        when(atsPort.cmd.ready) {
          if (!withQuery) tcmd.ready := True
          size := tcmdPayload.size
          goto(ATS)
        }
      }

      def guestTLB(): Unit = {
        tcmdCached := tcmd.payload
        tcmd.ready := True
        counter.clear()
        goto(TLB)
      }

      CMD whenIsActive {
        when(tcmd.valid) {
          val guestCtx = WhenBuilder()
          if(generateTransPort) guestCtx.when(tcmd.guest) {
            if (withQuery) guestTLB else guestCmd
          }
          guestCtx.otherwise {
            cmd.valid     := True
            cmd.address   := tcmd.address
            cmd.size      := tcmd.size
            when (cmd.ready) {
              tcmd.ready  := True
              goto(RSP)
            }
          }
        }
      }

      if (withQuery) TLB whenIsActive {
        when (!counterFlow) {
          counter.increment()
        }
        when (counterFlow) {
          when (queryPort.hit) {
            when (queryPort.pageFault || queryPort.accessFault) {
              trsp.valid      := True
              trsp.data       := req.address.asBits.resized
              trsp.error(1)   := queryPort.pageFault
              trsp.error(0)   := queryPort.accessFault
              trsp.redo       := False
              trsp.waitSlot   := B(0)
              trsp.waitAny    := False
              when (rsp.valid) {
                goto(CMD)
              }
            } otherwise {
              cmd.valid       := True
              cmd.address     := queryPort.translated.resized
              cmd.size        := tcmdCached.size
              when (cmd.ready) {
                goto(RSP)
              }
            }
          } otherwise {
            guestCmd
          }
        }
      }

      if (generateTransPort) ATS whenIsActive {
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
            cmd.valid     := True
            cmd.address   := atsPort.rsp.address
            cmd.size      := size
            when (cmd.ready) {
              atsPort.rsp.ready := True
              goto(RSP)
            }
          }
        }
      }

      RSP whenIsActive {
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
