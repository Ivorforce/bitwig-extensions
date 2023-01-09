package com.bitwig.extensions.controllers.m_audio;

import java.util.function.Consumer;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.AbsoluteHardwareControl;
import com.bitwig.extension.controller.api.AbsoluteHardwareKnob;
import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.HardwareButton;
import com.bitwig.extension.controller.api.HardwareSurface;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.MidiOut;
import com.bitwig.extension.controller.api.MultiStateHardwareLight;


public class Hardware {

    protected Hardware(ControllerExtension driver, String modelName) {
        mHost = driver.getHost();
        mHardwareSurface = mHost.createHardwareSurface();
        mApplication = mHost.createApplication();
        this.modelName = modelName;
        BANK_SIZE = modelName == "Mini" ? 4 : 8;

        mMidiIn1 = mHost.getMidiInPort(0);
        mMidiIn2 = mHost.getMidiInPort(1);
        mMidiOut1 = mHost.getMidiOutPort(0);
        mMidiOut2 = mHost.getMidiOutPort(1);

        mKnobs = new AbsoluteHardwareKnob[BANK_SIZE];
        mFaders = new AbsoluteHardwareControl[BANK_SIZE];

        mPadButtons = new HardwareButton[2][BANK_SIZE];
        mPadLights = new MultiStateHardwareLight[2][BANK_SIZE];
        mFaderButtons = new HardwareButton[BANK_SIZE];
        mFaderButtonLights = new MultiStateHardwareLight[BANK_SIZE];

        initHardwareControls();
    }

    protected void initHardwareControls() {
        mStopButton = createButton("stop", CC_STOP);
        mStopButton.setLabel("Stop");
        mPlayButton = createButton("play", CC_PLAY);
        mPlayButton.setLabel("Play");
        mRecordButton = createButton("record", CC_RECORD);
        mRecordButton.setLabel("Rec");

        mRewindButton = createButton("rewind", CC_REWIND);
        mRewindButton.setLabel("<<");
        mForwardButton = createButton("forward", CC_FORWARD);
        mForwardButton.setLabel(">>");
        mLoopButton = createButton("loop", CC_LOOP);
        mLoopButton.setLabel("Loop");

        mBankPrevButton = createButton("prevBank", CC_BANK_PREV);
        mBankPrevButton.setLabel("Bank <-");
        mBankNextButton = createButton("nextBank", CC_BANK_NEXT);
        mBankNextButton.setLabel("Bank ->");

        mVolumeLayerButton = createButton("volumeLayer", CC_LAYER_VOLUME, CC_LAYER_CHANNEL);
        mPanLayerButton = createButton("panLayer", CC_LAYER_PAN, CC_LAYER_CHANNEL);
        mDeviceLayerButton = createButton("deviceLayer", CC_LAYER_DEVICE, CC_LAYER_CHANNEL);
        mSendsLayerButton = createButton("sendsLayer", CC_LAYER_SENDS, CC_LAYER_CHANNEL);

        mSaveButton = createButton("save", CC_HOTPAD_SAVE, false);
        mQuantizeButton = createButton("quantize", CC_HOTPAD_QUANTIZE, false);
        mViewButton = createButton("view", CC_HOTPAD_VIEW, false);
        mUndoButton = createButton("undo", CC_HOTPAD_UNDO, false);

        mMetronomeButton = createButton("metronome", CC_METRONOME);
        mMetronomeButton.setLabel("Metro");

        for (int i = 0; i < BANK_SIZE; i++)
            createKnob(i);

        for (int i = 0; i < BANK_SIZE; i++)
            createFader(i);

        for (int i = 0; i < BANK_SIZE; i++) 
            createOnOffFaderButton(i, 0);
        

        for (int i = 0; i < 5; i++)
            mFaderButtonsMode[i] = createButton("FaderButtonMode" + i, 57 + i, 15);

        for (int j = 0; j < 4; j++) {
            if (modelName == "Mini") {
                createRGBPadButton(0, j, CC_PAD_START);
                createRGBPadButton(1, j, CC_PAD_START + 8);
            } else {
                createRGBPadButton(0, j, CC_PAD_START);
                createRGBPadButton(0, j+4, CC_PAD_START+4);
                createRGBPadButton(1, j, CC_PAD_START-4);
                createRGBPadButton(1, j+4, CC_PAD_START);
            }
            // mMidiOut2.sendMidi(0x90, CC_PAD_START + j, OFF);
        }

        mSceneButtons[0] = createButton("sceneLaunch1", CC_ARROW_1);
        mSceneButtons[1] = createButton("sceneLaunch2", CC_ARROW_2);

        mFader = mHardwareSurface.createAbsoluteHardwareKnob("fader");
        mFader.setLabel("Fader");
        mFader.setAdjustValueMatcher(mMidiIn2.createAbsoluteCCValueMatcher(0, CC_FADER));

        mBackButton = createButton("backShift", CC_BACK_BUTTON);
        mBackButton.isPressed().markInterested();

        mEncoderButton = createButton("encoderKnob", CC_ENCODER_PESS);
        mEncoder = mHardwareSurface.createHardwareButton("encoder");
        mEncoder.setLabel("Encoder");
        mEncoder.pressedAction().setActionMatcher(mMidiIn2.createCCActionMatcher(0, CC_ENCODER, CC_ENCODER_LEFT));
        mEncoder.releasedAction().setActionMatcher(mMidiIn2.createCCActionMatcher(0, CC_ENCODER, CC_ENCODER_RIGHT));

    }

    private void createKnob(int index) {
        assert index >= 0 && index < 8;

        final AbsoluteHardwareKnob knob = mHardwareSurface.createAbsoluteHardwareKnob("knob" + index);
        knob.setLabel(String.valueOf(index + 1));
        knob.setIndexInGroup(index);
        knob.setAdjustValueMatcher(mMidiIn2.createAbsoluteCCValueMatcher(KNOB_CHANNEL, CC_KNOB_START + index));

        mKnobs[index] = knob;
    }

    private void createFader(int index) {
        assert index >= 0 && index < 8;

        final AbsoluteHardwareControl fader = mHardwareSurface.createHardwareSlider("fader" + index);
        fader.setLabel(String.valueOf(index + 1));
        fader.setIndexInGroup(index);
        fader.setAdjustValueMatcher(mMidiIn2.createAbsoluteCCValueMatcher(0, CC_FADER_START + index));

        mFaders[index] = fader;
    }

    private HardwareButton createButton(final String id, final int controlNumber, final int channel) {
        final HardwareButton button = mHardwareSurface.createHardwareButton(id);

        button.pressedAction().setActionMatcher(mMidiIn2.createCCActionMatcher(channel, controlNumber, 127));
        button.releasedAction().setActionMatcher(mMidiIn2.createCCActionMatcher(channel, controlNumber, 0));

        return button;
    }

    private HardwareButton createButton(final String id, final int controlNumber) {
        final HardwareButton button = mHardwareSurface.createHardwareButton(id);

        button.pressedAction().setActionMatcher(mMidiIn2.createCCActionMatcher(0, controlNumber, 127));
        button.releasedAction().setActionMatcher(mMidiIn2.createCCActionMatcher(0, controlNumber, 0));

        return button;
    }

    private HardwareButton createButton(final String id, final int controlNumber, final boolean value) {
        final HardwareButton button = mHardwareSurface.createHardwareButton(id);

        button.pressedAction().setActionMatcher(mMidiIn2.createCCActionMatcher(0, controlNumber));

        return button;
    }

    private HardwareButton createNoteButton(final String id, final int controlNumber) {
        final HardwareButton button = mHardwareSurface.createHardwareButton(id);

        button.pressedAction().setActionMatcher(mMidiIn2.createNoteOnActionMatcher(0, controlNumber));
        button.releasedAction().setActionMatcher(mMidiIn2.createNoteOffActionMatcher(0, controlNumber));

        return button;
    }

    private void createRGBPadButton(int i, int j, int cc_start) {
        int index = i;
        int jndex = j;
        int cc = cc_start + jndex;
        int label = cc * 10 + index;
        final HardwareButton button = createNoteButton("padButton" + label, cc);
        button.setLabel(String.valueOf(cc));

        final MultiStateHardwareLight light = mHardwareSurface
                .createMultiStateHardwareLight("light" + label);

        light.state().onUpdateHardware(new Consumer<RGBLightState>() {
            @Override
            public void accept(RGBLightState state) {
                if (state != null)
                    RGBLightState.send(mMidiOut2, cc, state.getMessage(), false);
            }
        });

        light.setColorToStateFunction(color -> new RGBLightState(color));
        button.setBackgroundLight(light);

        mPadButtons[index][jndex] = button;
        mPadLights[index][jndex] = light;
    }

    private void createOnOffFaderButton(int i, int channel) {
        assert i >= 0 && i < 8;

        final HardwareButton button = createButton("faderButton" + i + "" + channel, CC_FADER_BUTTON_START + i, 0);

        mFaderButtons[i] = button;

        final MultiStateHardwareLight light = mHardwareSurface
                .createMultiStateHardwareLight("fader_light" + i + "" + channel);

        light.state().onUpdateHardware(new Consumer<RGBLightState>() {

            @Override
            public void accept(RGBLightState state) {
                if (state != null)
                    RGBLightState.send(mMidiOut2, CC_FADER_BUTTON_START + i, state.getMessage(), 0);
            }
        });

        light.setColorToStateFunction(color -> new RGBLightState(color));

        button.setBackgroundLight(light);

        mFaderButtonLights[i] = light;
    }

    protected String modelName;
    private int BANK_SIZE = 8;

    // final static int CC_VALUES
    final static int CC_PAD_CHANNEL = 0; // 0-3 Banks ~ Channels
    final static int CC_PAD_START = 40;
    final static int PAD_NOTE_OFFSET = 40;// 36;

    final static int CC_ARROW_1 = 107;
    final static int CC_ARROW_2 = 108;

    final static int CC_LAYER_CHANNEL = 15;
    final static int CC_LAYER_VOLUME = 0x53; // Channel 15
    final static int CC_LAYER_PAN = 85;
    final static int CC_LAYER_DEVICE = 86;
    final static int CC_LAYER_SENDS = 87;
    final static int CC_LAYER_CLIP = 0x3C;

    final static int CC_HOTPAD_SAVE = 0x38; // Channel 0
    final static int CC_HOTPAD_QUANTIZE = 0x39;
    final static int CC_HOTPAD_VIEW = 0x3a;
    final static int CC_HOTPAD_UNDO = 0x3b;

    final static int CC_PLAY = 118; // Channel 0
    final static int CC_STOP = 117;
    final static int CC_RECORD = 119;
    final static int CC_LOOP = 114;
    final static int CC_FORWARD = 116;
    final static int CC_REWIND = 115;
    final static int CC_BANK_PREV = 110;
    final static int CC_BANK_NEXT = 111;
    final static int CC_METRONOME = 106;

    final static int CC_ENCODER_PESS = 102; // Channel 0
    final static int CC_ENCODER = 103;
    final static int CC_ENCODER_LEFT = 63; // value
    final static int CC_ENCODER_RIGHT = 65; // value

    final static int CC_BACK_BUTTON = 104; //Back 104 channel 0 && Shift 105 channel 12

    final static int KNOB_CHANNEL = 0;
    final static int CC_KNOB_START = 22; // Channel 15

    final static int CC_FADER = 41; // Channel 0
    final static int CC_FADER_START = 12; // Channel 0

    final static int CC_FADER_BUTTON_START = 32;
    final static int CC_FADER_BUTTON_START_CHANNEL = 0;

    // Light States
    final static int OFF = 0;
    final static int BLINK = 64;
    final static int WHITE = 63;
    final static int CHARTREUSE = 14;
    final static int GREEN = 12;
    final static int AQUA = 60;
    final static int CYAN = 56;
    final static int AZURE = 44;
    final static int BLUE = 48;
    final static int VIOLET = 50;
    final static int MAGENTA = 51;
    final static int ROSE = 35;
    final static int RED = 3;
    final static int ORANGE = 11;
    final static int YELLOW = 15;

    protected HardwareSurface mHardwareSurface;
    protected ControllerHost mHost;
    protected Application mApplication;
    protected MidiIn mMidiIn1, mMidiIn2;
    protected MidiOut mMidiOut1, mMidiOut2;

    protected HardwareButton mPlayButton, mStopButton, mRecordButton, mLoopButton, mForwardButton, mRewindButton,
            mBankNextButton, mBankPrevButton, mMetronomeButton, mEncoderButton, mBackButton, mVolumeLayerButton,
            mDeviceLayerButton,
            mSendsLayerButton, mPanLayerButton, mSaveButton, mQuantizeButton, mViewButton,
            mUndoButton;
    protected AbsoluteHardwareKnob[] mKnobs = new AbsoluteHardwareKnob[8];
    protected AbsoluteHardwareKnob mFader;
    protected AbsoluteHardwareControl[] mFaders = new AbsoluteHardwareControl[8];
    protected HardwareButton mEncoder;

    protected HardwareButton[] mSceneButtons = new HardwareButton[2];

    protected HardwareButton[][] mPadButtons = new HardwareButton[2][8];
    protected MultiStateHardwareLight[][] mPadLights = new MultiStateHardwareLight[2][8];
    protected HardwareButton[] mFaderButtons = new HardwareButton[32];
    protected MultiStateHardwareLight[] mFaderButtonLights = new MultiStateHardwareLight[32];

    protected HardwareButton[] mFaderButtonsMode = new HardwareButton[5];

}
