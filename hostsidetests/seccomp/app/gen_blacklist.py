#!/usr/bin/env python3
#
# This script generates syscall name to number mapping for supported
# architectures.  To update the output, runs:
#
#  $ app/gen_blacklist.py > app/assets/syscalls.json
#
# TODO: Consider generating it in Android.mk/bp.

import glob
import json
import os
import subprocess

_SUPPORTED_ARCHS = ['arm', 'arm64', 'x86', 'x86_64', 'mips', 'mips64']

_INTERESTED_SYSCALLS = {
    'swapon': 'all',
    'swapoff': 'all',
    'add_key': 'all',
    'keyctl': 'all',
    'openat': 'all',
    'syncfs': 'all',
    'inotify_init': 'arm,x86,mips',
}

def create_syscall_name_to_number_map(arch, names):
  arch_config = {
      'arm': {
          'uapi_class': 'asm-arm',
          'extra_cflags': [],
      },
      'arm64': {
          'uapi_class': 'asm-arm64',
          'extra_cflags': [],
      },
      'x86': {
          'uapi_class': 'asm-x86',
          'extra_cflags': ['-D__i386__'],
      },
      'x86_64': {
          'uapi_class': 'asm-x86',
          'extra_cflags': [],
      },
      'mips': {
          'uapi_class': 'asm-mips',
          'extra_cflags': ['-D_MIPS_SIM=_MIPS_SIM_ABI32'],
      },
      'mips64': {
          'uapi_class': 'asm-mips',
          'extra_cflags': ['-D_MIPS_SIM=_MIPS_SIM_ABI64'],
      }
  }

  # Run preprocessor over the __NR_syscall symbols, including unistd.h,
  # to get the actual numbers
  # TODO: The following code is forked from bionic/libc/tools/genseccomp.py.
  # Figure out if we can de-duplicate them crossing cts project boundary.
  prefix = '__SECCOMP_'  # prefix to ensure no name collisions
  kernel_uapi_path = os.path.join(os.getenv('ANDROID_BUILD_TOP'),
                                  'bionic/libc/kernel/uapi')
  cpp = subprocess.Popen(
      [get_latest_clang_path(),
       '-E', '-nostdinc',
       '-I' + os.path.join(kernel_uapi_path,
                           arch_config[arch]['uapi_class']),
       '-I' + os.path.join(kernel_uapi_path)
       ]
      + arch_config[arch]['extra_cflags']
      + ['-'],
      universal_newlines=True,
      stdin=subprocess.PIPE, stdout=subprocess.PIPE)
  cpp.stdin.write('#include <asm/unistd.h>\n')
  for name in names:
    # In SYSCALLS.TXT, there are two arm-specific syscalls whose names start
    # with __ARM__NR_. These we must simply write out as is.
    if not name.startswith('__ARM_NR_'):
      cpp.stdin.write(prefix + name + ', __NR_' + name + '\n')
    else:
      cpp.stdin.write(prefix + name + ', ' + name + '\n')
  content = cpp.communicate()[0].split('\n')

  # The input is now the preprocessed source file. This will contain a lot
  # of junk from the preprocessor, but our lines will be in the format:
  #
  #     __SECCOMP_${NAME}, (0 + value)
  syscalls = {}
  for line in content:
    if not line.startswith(prefix):
      continue
    # We might pick up extra whitespace during preprocessing, so best to strip.
    name, value = [w.strip() for w in line.split(',')]
    name = name[len(prefix):]
    # Note that some of the numbers were expressed as base + offset, so we
    # need to eval, not just int
    value = eval(value)
    if name in syscalls:
      raise Exception('syscall %s is re-defined' % name)
    syscalls[name] = value
  return syscalls

def get_latest_clang_path():
  candidates = sorted(glob.glob(os.path.join(os.getenv('ANDROID_BUILD_TOP'),
      'prebuilts/clang/host/linux-x86/clang-*')), reverse=True)
  for clang_dir in candidates:
    clang_exe = os.path.join(clang_dir, 'bin/clang')
    if os.path.exists(clang_exe):
      return clang_exe
  raise FileNotFoundError('Cannot locate clang executable')

def main():
  dictionary = {}
  for arch in _SUPPORTED_ARCHS:
    syscall_names = []
    for syscall in _INTERESTED_SYSCALLS.keys():
      if (arch in _INTERESTED_SYSCALLS[syscall] or
          'all' == _INTERESTED_SYSCALLS[syscall]):
        syscall_names.append(syscall)
    dictionary[arch] = create_syscall_name_to_number_map(arch, syscall_names)

  print(json.dumps(dictionary, sort_keys=True, indent=2))

if __name__ == '__main__':
  main()
