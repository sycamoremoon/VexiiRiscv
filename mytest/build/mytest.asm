
build/mytest.elf:     file format elf32-littleriscv


Disassembly of section .crt_section:

80000000 <_start>:
80000000:	00000097          	auipc	ra,0x0
80000004:	02c08093          	addi	ra,ra,44 # 8000002c <m_mode_vec>
80000008:	30509073          	csrw	mtvec,ra
8000000c:	000010b7          	lui	ra,0x1
80000010:	80808093          	addi	ra,ra,-2040 # 808 <_start-0x7ffff7f8>
80000014:	30009073          	csrw	mstatus,ra
80000018:	00800513          	li	a0,8
8000001c:	30451073          	csrw	mie,a0
80000020:	34451073          	csrw	mip,a0
80000024:	00000033          	add	zero,zero,zero
80000028:	0200006f          	j	80000048 <fail>

8000002c <m_mode_vec>:
8000002c:	02a00093          	li	ra,42
80000030:	342020f3          	csrr	ra,mcause
80000034:	80000137          	lui	sp,0x80000
80000038:	00b10113          	addi	sp,sp,11 # 8000000b <fail+0xffffffc3>
8000003c:	00209663          	bne	ra,sp,80000048 <fail>
80000040:	342020f3          	csrr	ra,mcause

80000044 <pass>:
80000044:	0000006f          	j	80000044 <pass>

80000048 <fail>:
80000048:	0000006f          	j	80000048 <fail>
