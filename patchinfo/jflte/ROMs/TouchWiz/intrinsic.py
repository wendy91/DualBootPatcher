from fileinfo import FileInfo
import os

file_info = FileInfo()

file_info.ramdisk        = os.path.join('jflte', 'TouchWiz', 'touchwiz.dualboot.cpio')
file_info.patch          = os.path.join('jflte', 'ROMs', 'TouchWiz', 'intrinsic-20130806.dualboot.patch')

def matches(filename):
  if filename == "iNTriNsiC 8-6-13.zip":
    return True
  else:
    return False

def print_message():
  print("Detected iNTriNsiC 20130806 ROM zip")
  print("Using patched TouchWiz ramdisk")

def get_file_info():
  return file_info