package com.bitwig.extensions.controllers.devine;

public class EzCreatorCommon
{
   public static final byte[] INIT_SYSEX = new byte[] {
      (byte)0xF0, 0x7E, 0x7F, 0x60, 0x01, 0x00, 0x7E, 0x01, 0x01, (byte)0xF7
   };

   public static final byte[] DEINIT_SYSEX = new byte[] {
      (byte)0xF0, 0x7E, 0x7F, 0x60, 0x01, 0x00, 0x7E, 0x01, 0x00, (byte)0xF7
   };

   public static final int REQUIRED_API_VERSION = 11;
}
