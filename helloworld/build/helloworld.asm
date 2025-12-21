
build/helloworld.elf:     file format elf32-littleriscv


Disassembly of section .init:

80000000 <_start>:
80000000:	00001197          	auipc	gp,0x1
80000004:	94018193          	addi	gp,gp,-1728 # 80000940 <__global_pointer$>

80000008 <init>:
80000008:	00001117          	auipc	sp,0x1
8000000c:	15810113          	addi	sp,sp,344 # 80001160 <__freertos_irq_stack_top>
80000010:	00000517          	auipc	a0,0x0
80000014:	13450513          	addi	a0,a0,308 # 80000144 <__init_array_end>
80000018:	00000597          	auipc	a1,0x0
8000001c:	12c58593          	addi	a1,a1,300 # 80000144 <__init_array_end>
80000020:	81418613          	addi	a2,gp,-2028 # 80000154 <__bss_start>
80000024:	00c5fc63          	bgeu	a1,a2,8000003c <init+0x34>
80000028:	00052283          	lw	t0,0(a0)
8000002c:	0055a023          	sw	t0,0(a1)
80000030:	00450513          	addi	a0,a0,4
80000034:	00458593          	addi	a1,a1,4
80000038:	fec5e8e3          	bltu	a1,a2,80000028 <init+0x20>
8000003c:	81418513          	addi	a0,gp,-2028 # 80000154 <__bss_start>
80000040:	81818593          	addi	a1,gp,-2024 # 80000158 <_end>
80000044:	00b57863          	bgeu	a0,a1,80000054 <init+0x4c>
80000048:	00052023          	sw	zero,0(a0)
8000004c:	00450513          	addi	a0,a0,4
80000050:	feb56ce3          	bltu	a0,a1,80000048 <init+0x40>
80000054:	05c000ef          	jal	ra,800000b0 <__libc_init_array>
80000058:	000060b7          	lui	ra,0x6
8000005c:	3000b073          	csrc	mstatus,ra
80000060:	000020b7          	lui	ra,0x2
80000064:	3000a073          	csrs	mstatus,ra
80000068:	018000ef          	jal	ra,80000080 <main>

8000006c <pass>:
8000006c:	0000006f          	j	8000006c <pass>
80000070:	00000013          	nop

80000074 <fail>:
80000074:	0000006f          	j	80000074 <fail>
80000078:	00000013          	nop

8000007c <_init>:
8000007c:	00008067          	ret

Disassembly of section .text:

80000080 <main>:
80000080:	00a00713          	li	a4,10
80000084:	10000637          	lui	a2,0x10000
80000088:	00000797          	auipc	a5,0x0
8000008c:	0bc78793          	addi	a5,a5,188 # 80000144 <__init_array_end>
80000090:	00c0006f          	j	8000009c <main+0x1c>
80000094:	00178793          	addi	a5,a5,1
80000098:	00d62023          	sw	a3,0(a2) # 10000000 <__stack_size+0xffff000>
8000009c:	0007c683          	lbu	a3,0(a5)
800000a0:	fe069ae3          	bnez	a3,80000094 <main+0x14>
800000a4:	fff70713          	addi	a4,a4,-1
800000a8:	fe0710e3          	bnez	a4,80000088 <main+0x8>
800000ac:	00008067          	ret

800000b0 <__libc_init_array>:
800000b0:	ff010113          	addi	sp,sp,-16
800000b4:	00812423          	sw	s0,8(sp)
800000b8:	01212023          	sw	s2,0(sp)
800000bc:	00000417          	auipc	s0,0x0
800000c0:	08840413          	addi	s0,s0,136 # 80000144 <__init_array_end>
800000c4:	00000917          	auipc	s2,0x0
800000c8:	08090913          	addi	s2,s2,128 # 80000144 <__init_array_end>
800000cc:	40890933          	sub	s2,s2,s0
800000d0:	00112623          	sw	ra,12(sp)
800000d4:	00912223          	sw	s1,4(sp)
800000d8:	40295913          	srai	s2,s2,0x2
800000dc:	00090e63          	beqz	s2,800000f8 <__libc_init_array+0x48>
800000e0:	00000493          	li	s1,0
800000e4:	00042783          	lw	a5,0(s0)
800000e8:	00148493          	addi	s1,s1,1
800000ec:	00440413          	addi	s0,s0,4
800000f0:	000780e7          	jalr	a5
800000f4:	fe9918e3          	bne	s2,s1,800000e4 <__libc_init_array+0x34>
800000f8:	00000417          	auipc	s0,0x0
800000fc:	04c40413          	addi	s0,s0,76 # 80000144 <__init_array_end>
80000100:	00000917          	auipc	s2,0x0
80000104:	04490913          	addi	s2,s2,68 # 80000144 <__init_array_end>
80000108:	40890933          	sub	s2,s2,s0
8000010c:	40295913          	srai	s2,s2,0x2
80000110:	00090e63          	beqz	s2,8000012c <__libc_init_array+0x7c>
80000114:	00000493          	li	s1,0
80000118:	00042783          	lw	a5,0(s0)
8000011c:	00148493          	addi	s1,s1,1
80000120:	00440413          	addi	s0,s0,4
80000124:	000780e7          	jalr	a5
80000128:	fe9918e3          	bne	s2,s1,80000118 <__libc_init_array+0x68>
8000012c:	00c12083          	lw	ra,12(sp)
80000130:	00812403          	lw	s0,8(sp)
80000134:	00412483          	lw	s1,4(sp)
80000138:	00012903          	lw	s2,0(sp)
8000013c:	01010113          	addi	sp,sp,16
80000140:	00008067          	ret
