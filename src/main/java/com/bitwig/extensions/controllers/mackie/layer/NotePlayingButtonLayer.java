package com.bitwig.extensions.controllers.mackie.layer;

import com.bitwig.extension.controller.api.HardwareButton;
import com.bitwig.extension.controller.api.OnOffHardwareLight;
import com.bitwig.extensions.controllers.mackie.BasicNoteOnAssignment;
import com.bitwig.extensions.controllers.mackie.section.MixControl;
import com.bitwig.extensions.controllers.mackie.section.MixerSectionHardware;
import com.bitwig.extensions.controllers.mackie.section.NoteState;
import com.bitwig.extensions.controllers.mackie.section.ScaleNoteHandler;
import com.bitwig.extensions.controllers.mackie.value.ValueObject;

public class NotePlayingButtonLayer extends ButtonLayer {

   private ScaleNoteHandler scaleHandler;
   private int blinkTicks;

   public NotePlayingButtonLayer(final MixControl mixControl) {
      super("NOTEPLAYER", mixControl, BasicNoteOnAssignment.REC_BASE);
   }

   public void init(final ScaleNoteHandler scaleNoteHandler, final MixerSectionHardware hwControls) {
      scaleHandler = scaleNoteHandler;
      for (int i = 0; i < 32; i++) {
         final HardwareButton button = hwControls.getButton(i);
         final OnOffHardwareLight light = (OnOffHardwareLight) button.backgroundLight();
         final ValueObject<NoteState> state = scaleHandler.isPlaying(i);
         bind(() -> getLightState(state), light);
      }
   }

   public boolean getLightState(final ValueObject<NoteState> state) {
      return state.get() == NoteState.BASENOTE || state.get() == NoteState.PLAYING;
   }

   @Override
   protected void onActivate() {
      scaleHandler.activate();
   }

   @Override
   protected void onDeactivate() {
      scaleHandler.deactivate();
   }

   public void notifyBlink(final int ticks) {
      blinkTicks = ticks;
   }

   public void navigateHorizontal(final int direction, final boolean pressed) {
      if (!isActive()) {
         return;
      }
      scaleHandler.navigateHorizontal(direction, pressed);
   }

}
