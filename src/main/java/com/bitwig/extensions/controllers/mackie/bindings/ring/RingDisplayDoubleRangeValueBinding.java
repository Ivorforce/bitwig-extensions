package com.bitwig.extensions.controllers.mackie.bindings.ring;

import com.bitwig.extensions.controllers.mackie.display.RingDisplay;
import com.bitwig.extensions.controllers.mackie.display.RingDisplayType;
import com.bitwig.extensions.controllers.mackie.value.DoubleRangeValue;

public class RingDisplayDoubleRangeValueBinding extends RingDisplayBinding<DoubleRangeValue> {

   public RingDisplayDoubleRangeValueBinding(final DoubleRangeValue source, final RingDisplay target,
                                             final RingDisplayType type) {
      super(target, source, type);
      source.addDoubleValueObserver(v -> valueChange(type.getOffset() + source.scale(v, type.getRange())));
   }

   private void valueChange(final int value) {
      if (isActive()) {
         getTarget().sendValue(value, false);
      }
   }

   @Override
   protected int calcValue() {
      return type.getOffset() + getSource().scale(getSource().getRawValue(), type.getRange());
   }


}
