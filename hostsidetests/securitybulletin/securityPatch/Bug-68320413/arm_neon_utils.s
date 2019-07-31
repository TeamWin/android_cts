.text
.align 4

.globl write_to_callee_saved_registers
.type write_to_callee_saved_registers, %function
write_to_callee_saved_registers:

    push        {lr}
    vld1.64      d8,[r0]!
    vld1.64      d9,[r0]!
    vld1.64      d10,[r0]!
    vld1.64      d11,[r0]!
    vld1.64      d12,[r0]!
    vld1.64      d13,[r0]!
    vld1.64      d14,[r0]!
    vld1.64      d15,[r0]!
    pop         {pc}

.globl read_from_callee_saved_registers
.type read_from_callee_saved_registers, %function
read_from_callee_saved_registers:

    push        {lr}
    vst1.64      d8,[r0]!
    vst1.64      d9,[r0]!
    vst1.64      d10,[r0]!
    vst1.64      d11,[r0]!
    vst1.64      d12,[r0]!
    vst1.64      d13,[r0]!
    vst1.64      d14,[r0]!
    vst1.64      d15,[r0]!
    pop         {pc}
