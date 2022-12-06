package com.bitwig.extensions.controllers.mackie.definition;

import java.util.UUID;

public class IconQconProG2X2ExtensionDefinition extends IconQconProG2ExtensionDefinition {
   private static final UUID DRIVER_ID = UUID.fromString("d32bead3-83e4-4dc2-90f8-eb3a1a847bf1");

   public IconQconProG2X2ExtensionDefinition() {
      super(2);
   }

   @Override
   public UUID getId() {
      return IconQconProG2X2ExtensionDefinition.DRIVER_ID;
   }
}
