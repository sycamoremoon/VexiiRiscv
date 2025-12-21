TARGET ?= svadu
#ifeq ($(TARGET), mmu_sv39)
ADDON_FLAGS :=  --fetch-l1 --fetch-l1-ways=2 --fetch-l1-ways=4 --fetch-l1-mem-data-width-min=64 \
		--lsu-l1 --lsu-l1-ways=2 --with-lsu-bypass --lsu-l1-ways=4 --lsu-l1-mem-data-width-min=64 \
		--relaxed-branch --with-rdtime
#endif

# ifeq ($(TARGET), mmu_sv39)
# ADDON_FLAGS :=  --performance-counters=0 \
# 		--fetch-l1 --fetch-l1-ways=2 --fetch-l1-ways=4 --fetch-l1-mem-data-width-min=64 \
# 		--lsu-l1 --lsu-l1-ways=2  --with-lsu-bypass --relaxed-branch \
# 		--lsu-l1-ways=4 --lsu-l1-mem-data-width-min=64 \
# 		--fma-reduced-accuracy --fpu-ignore-subnormal --with-btb --with-ras --with-gshare
# endif

all:
	make RISCV_NAME=riscv64-elf RISCV_PATH=/usr MABI=lp64d MARCH=rv64g -C ext/NaxSoftware/baremetal/$(TARGET) clean
	make RISCV_NAME=riscv64-elf RISCV_PATH=/usr MABI=lp64d MARCH=rv64g -C ext/NaxSoftware/baremetal/$(TARGET)
	sbt "Test/runMain vexiiriscv.tester.TestBench --xlen=64 --allow-bypass-from=0 \
		--with-mul --with-div --with-rvf --with-rvc --with-rvd --with-rvm --with-rva \
		--with-supervisor \
		--no-rvls-check --trace-all \
		--load-elf ext/NaxSoftware/baremetal/${TARGET}/build/${TARGET}.elf \
		$(ADDON_FLAGS)"
#--fetch-l1 --lsu-l1 
