.option arch, +zicsr

.global _start
_start:
#define MSTATUS_MPP_SUPERVISOR         0x00000808
#define MSTATUS_MPP_USER               0x00000000
#define EX_INT_ENABLE                  0x00000008
#define CAUSE_EXTERNAL_INT             0x8000000b

    la x1, m_mode_vec; csrw mtvec, x1
    li x1, MSTATUS_MPP_SUPERVISOR; csrw mstatus, x1
    li a0, EX_INT_ENABLE
    csrw mie, a0

    csrw mip, a0
    add x0, x0, x0
    j fail

m_mode_vec:
    li x1, 42
    csrr x1, mcause
    li x2, CAUSE_EXTERNAL_INT; bne x1, x2, fail
    csrr x1, mcause

pass:
    j pass
fail:
    j fail