package com.bitwig.extensions.controllers.novation.launchpad_pro_mk3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.AbsoluteHardwareControlBinding;
import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.Clip;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorDevice;
import com.bitwig.extension.controller.api.CursorDeviceFollowMode;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.DrumPadBank;
import com.bitwig.extension.controller.api.HardwareButton;
import com.bitwig.extension.controller.api.HardwareSlider;
import com.bitwig.extension.controller.api.HardwareSurface;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.MidiOut;
import com.bitwig.extension.controller.api.MultiStateHardwareLight;
import com.bitwig.extension.controller.api.NoteInput;
import com.bitwig.extension.controller.api.Parameter;
import com.bitwig.extension.controller.api.PlayingNote;
import com.bitwig.extension.controller.api.Scene;
import com.bitwig.extension.controller.api.SceneBank;
import com.bitwig.extension.controller.api.Send;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extension.controller.api.Transport;
import com.bitwig.extensions.framework.Layer;
import com.bitwig.extensions.framework.Layers;

public class LaunchpadProMK3 extends ControllerExtension {

    // SYSEX
    private final String DAW_MODE = "F0 00 20 29 02 0E 10 01 F7";
    private final String STANDALONE_MODE = "F0 00 20 29 02 0E 10 00 F7";

    private final String SESSION_LAYOUT = "F0 00 20 29 02 0E 00 00 00 00 F7";
    private final String SESSION_MODE_PREFIX = "f0002029020e00000000";

    private final String PRINT_TO_CLIP_ON = "F0 00 20 29 02 0E 18 01 F7";
    private final String PRINT_TO_CLIP_OFF = "F0 00 20 29 02 0E 18 00 F7";

    private final String DAW_VOLUME_FADER = "F0 00 20 29 02 0E 01 00 00 00 00 00 00 01 00 01 00 02 00 02 00 03 00 03 00 04 00 04 00 05 00 05 00 06 00 06 00 07 00 07 00 F7";
    private final String DAW_PAN_FADER = "F0 00 20 29 02 0E 01 01 01 00 01 08 00 01 01 09 00 02 01 0A 00 03 01 0B 00 04 01 0C 00 05 01 0D 00 06 01 0E 00 07 01 0F 00 F7";
    private final String DAW_SENDS_FADER = "F0 00 20 29 02 0E 01 02 00 00 00 10 00 01 00 11 00 02 00 12 00 03 00 13 00 04 00 14 00 05 00 15 00 06 00 16 00 07 00 17 00 F7";
    private final String DAW_DEVICE_FADER = "F0 00 20 29 02 0E 01 03 00 00 00 18 00 01 00 19 00 02 00 1A 00 03 00 1B 00 04 00 1C 00 05 00 1D 00 06 00 1E 00 07 00 1F 00 F7";

    private final String DAW_FADER_ON = "F0 00 20 29 02 0E 00 01";
    private final String DAW_FADER_OFF = "F0 00 20 29 02 0E 00 00";

    private final String DAW_VOLUME = " 00 00 F7";
    private final String DAW_PAN = " 01 00 F7";
    private final String DAW_SENDS = " 02 00 F7";
    private final String DAW_DEVICE = " 03 00 F7";

    private final String DAW_DRUM = "F0 00 20 29 02 0E 00 02 F7";
    private final String DAW_NOTE = "F0 00 20 29 02 0E 00 01 F7";
    private final String CHORD_MODE_PREFIX = "f0002029020e00020000";
    private final String NOTE_MODE_PREFIX = "f0002029020e00040000";

    // Print to Clip Constants
    private final String PRINT_TO_CLIP_PREFIX = "f0002029020e0302000000000000000";
    private final int START_SLICE = 2;
    private final int LENGTH_INDEX = 2;
    private final int LENGTH_SLICE = 2;
    private final int PITCH_INDEX = 4;
    private final int VELOCITY_INDEX = 5;
    private final int PAYLOAD_OFFSET = 21;
    private final int NOTE_BYTES = 6;

    private final int MICROSTEPS = 6;
    private final double TIMEFACTOR = 500.0;

    private Boolean isFixedLengthEnabled = false;
    private Boolean FIXED_LENGTH_PRESS_DELAY = false;
    private int FIXED_LENGTH_VALUE = 1;

    private Boolean isTemporarySwitch = false;
    private Boolean updateNoteTableIsInUse = false;
    private Boolean longPressedIsInUse = false;

    public LaunchpadProMK3(ControllerExtensionDefinition definition, ControllerHost host) {
        super(definition, host);
    }

    @Override
    public void init() {
        mHost = getHost();
        mApplication = mHost.createApplication();
        mApplication.recordQuantizeNoteLength().markInterested();
        mApplication.recordQuantizationGrid().markInterested();
        mApplication.recordQuantizationGrid().addValueObserver(g -> {
            mHost.println(g);
            if (g == "1/32" || g == "1/16" || g == "1/8" || g == "1/4")
                QUANTIZATION_GRID_SIZE = g;
        });

        mHardwareSurface = mHost.createHardwareSurface();

        mHardwareSurface.setPhysicalSize(300, 300);

        initNoteTable();
        initMIDI();

        mNoteInput = mMidiIn1.createNoteInput("Note/Sequencer Ch. 1", "90????", "D0??", "80????");
        mMidiIn1.createNoteInput("Sequencer Ch. 2", "91????", "D0??", "81????");
        mMidiIn1.createNoteInput("Sequencer Ch. 3", "92????", "D0??", "82????");
        mMidiIn1.createNoteInput("Sequencer Ch. 4", "93????", "D0??", "83????");
        mDrumInput = mMidiIn0.createNoteInput("Drum", "98????");
        mDrumInput.setKeyTranslationTable(noteTable);

        mTransport = mHost.createTransport();
        mTransport.isArrangerRecordEnabled().markInterested();
        mTransport.isPlaying().markInterested();
        mTransport.isClipLauncherOverdubEnabled().markInterested();
        mTransport.isMetronomeEnabled().markInterested();
        mTransport.isClipLauncherOverdubEnabled().markInterested();
        mTransport.clipLauncherPostRecordingAction().markInterested();
        mTransport.getClipLauncherPostRecordingTimeOffset().markInterested();
        mTransport.timeSignature().numerator().markInterested();
        mTransport.getClipLauncherPostRecordingTimeOffset().addValueObserver(v -> {
            FIXED_LENGTH_VALUE = (int) v / mTransport.timeSignature().numerator().get();
        });
        mTransport.playPositionInSeconds().markInterested();

        mCursorTrack = mHost.createCursorTrack(8, 8);
        mCursorClip = mHost.createLauncherCursorClip(192, 128);
        mCursorClip.getLoopLength().markInterested();
        mCursorClip.clipLauncherSlot().isRecordingQueued().markInterested();
        mCursorTrack.canHoldNoteData().markInterested();
        mCursorTrack.canHoldNoteData().addValueObserver(b -> {
            if (b)
                mMidiOut.sendSysex(PRINT_TO_CLIP_ON);
            else
                mMidiOut.sendSysex(PRINT_TO_CLIP_OFF);

        });
        mCursorTrack.playingNotes().addValueObserver(notes -> mPlayingNotes = notes);

        mDeviceBank = mCursorTrack.createDeviceBank(8);
        mDeviceBank.cursorIndex().markInterested();

        mCursorDevice = mCursorTrack.createCursorDevice();
        mCursorDevice.position().markInterested();
        mCursorDevice.deviceType().markInterested();
        mCursorDevice.hasDrumPads().markInterested();

        CursorDevice mInstrument = mCursorTrack.createCursorDevice("01", "track", 8,
                CursorDeviceFollowMode.FIRST_INSTRUMENT);

        mCursorRemoteControlsPage = mCursorDevice.createCursorRemoteControlsPage(8);
        for (int i = 0; i < 8; i++) {
            mCursorRemoteControlsPage.getParameter(i).markInterested();
            mCursorRemoteControlsPage.getParameter(i).exists().markInterested();
            mDeviceBank.getDevice(i).exists().markInterested();
        }
        mTrackBank = mHost.createTrackBank(8, 8, 8);
        mTrackBank.followCursorTrack(mCursorTrack);
        mTrackBank.cursorIndex().markInterested();
        mTrackBank.canScrollBackwards().markInterested();
        mTrackBank.canScrollForwards().markInterested();
        mTrackBank.scrollPosition().markInterested();

        mSessionOverviewTrackBank = mHost.createTrackBank(64, 64, 64);
        mSessionOverviewTrackBank.followCursorTrack(mCursorTrack); // Keep?
        mSessionOverviewTrackBank.cursorIndex().markInterested();
        mSessionOverviewTrackBank.sceneBank().cursorIndex().markInterested();
        mSessionOverviewTrackBank.sceneBank().scrollPosition().markInterested();
        mSessionOverviewTrackBank.sceneBank().canScrollBackwards().markInterested();
        mSessionOverviewTrackBank.sceneBank().canScrollForwards().markInterested();
        mSessionOverviewTrackBank.canScrollBackwards().markInterested();
        mSessionOverviewTrackBank.canScrollForwards().markInterested();

        mSessionOverviewTrackBank.canScrollBackwards().markInterested();
        mSessionOverviewTrackBank.canScrollForwards().markInterested();

        mDrumPadBank = mInstrument.createDrumPadBank(64);
        mDrumPadBank.exists().markInterested();
        mDrumPadBank.scrollPosition().markInterested();
        mDrumPadBank.scrollPosition().addValueObserver(position -> {
            if (updateNoteTableIsInUse)
                return;
            globalOffset = position - 36;
            if (globalOffset == 0)
                mDrumInput.setKeyTranslationTable(baseNoteTable);
            else if (globalOffset >= -36 && globalOffset <= 28) {
                for (int i = 36; i < 100; i++)
                    noteTable[i] = i + globalOffset;
                mDrumInput.setKeyTranslationTable(noteTable);
            }
        });
        for (int i = 0; i < 64; i++) {
            mDrumPadBank.getItemAt(i).color().markInterested();
            mDrumPadBank.getItemAt(i).exists().markInterested();
            mDrumPadBank.getItemAt(i).isMutedBySolo().markInterested();
        }

        initHardwareControlls();
        initLayers();
        initPadMatrix();
        initVolumeLayer();
        initPanLayer();

        initScenes();
        initDeviceLayer();
        initSendsLayer();
        initNavigation();
        initTransport();
        initFunctionButtons();
        initBottomButtons();

        initSessionOverview();

        mSessionLayer.activate();

        mHost.showPopupNotification("Launchpad Pro Mk3 initialized...");
    }

    // DRUM
    private void initNoteTable() {
        for (int i = 0; i < 128; i++) {
            noteTable[i] = i;
            baseNoteTable[i] = i;
        }
    }

    // WORK!!!
    private Boolean updateNoteTable(String s) {
        if (updateNoteTableIsInUse)
            return false;
        updateNoteTableIsInUse = true;
        int position = mDrumPadBank.scrollPosition().get();
        mHost.println(String.valueOf(position));
        if (s == "UP") {
            if (position >= 0 && position <= 60)
                mDrumPadBank.scrollBy(4);
        }

        if (s == "UP_PAGE") {
            if (position >= 0 && position < 4)
                mDrumPadBank.scrollPosition().set(4);
            else if (position >= 4 && position <= 36)
                mDrumPadBank.scrollBy(16);
            else if (position > 36 && position < 64)
                mDrumPadBank.scrollPosition().set(64);
        }

        if (s == "DOWN") {
            if (position <= 64 && position >= 4)
                mDrumPadBank.scrollBy(-4);
        }

        if (s == "DOWN_PAGE") {
            if (position <= 64 && position > 52)
                mDrumPadBank.scrollPosition().set(52);
            else if (position <= 52 && position >= 20)
                mDrumPadBank.scrollBy(-16);
            else if (position < 20 && position > 0)
                mDrumPadBank.scrollPosition().set(0);
        }
        updateNoteTableIsInUse = false;
        return true;
    }

    private double absoluteBeatTime(byte[] b, int startindex, int length) {
        double x = 0.0;
        for (int i = 0; i < length; i++) {
            x += b[(startindex + length) - i - 1] << (i * 7);
        }
        x = x / TIMEFACTOR;
        mHost.println(String.valueOf(x));
        return Math.abs(x);
    }

    private void sysexToNotes(String sysex) {
        byte[] s = new byte[sysex.length() / 2];
        for (int i = 0; i < s.length; i++) {
            int index = i * 2;
            int val = Integer.parseInt(sysex.substring(index, index + 2), 16);
            s[i] = (byte) val;
        }

        notes = new double[(int) ((s.length - 20) / NOTE_BYTES)][4];
        notesList.add(notes);

        for (int i = 0; i < (s.length - 20) / NOTE_BYTES; i++) {
            int offset = PAYLOAD_OFFSET + i * NOTE_BYTES;
            double packetOffset = absoluteBeatTime(s, 18, 3);
            double start = absoluteBeatTime(s, offset, START_SLICE) + packetOffset;
            double length = absoluteBeatTime(s, offset + LENGTH_INDEX, LENGTH_SLICE);
            int pitch = s[offset + PITCH_INDEX];
            int velocity = s[offset + VELOCITY_INDEX];
            notes[i][0] = start;
            notes[i][1] = pitch;
            notes[i][2] = velocity;
            notes[i][3] = length;
        }
    }

    private void printToClip(String sysex) {
        if (notesList.size() == 0)
            return;

        byte[] s = new byte[sysex.length() / 2];
        for (int i = 0; i < s.length; i++) {
            int index = i * 2;
            int val = Integer.parseInt(sysex.substring(index, index + 2), 16);
            s[i] = (byte) val;
        }

        double beatTime = absoluteBeatTime(s, 18, 3);
        double stepSize = notesList.get(0)[0][3] / MICROSTEPS;
        mHost.println("Beattime: " + String.valueOf(beatTime));

        mCursorTrack.createNewLauncherClip(0);
        Clip clip = mCursorClip;
        clip.getPlayStop().set(beatTime);
        clip.getLoopLength().set(beatTime);
        clip.setStepSize(stepSize);

        for (int j = 0; j < notesList.size(); j++) {
            for (int i = 0; i < notesList.get(j).length; i++) {
                clip.setStep((int) Math.round(notesList.get(j)[i][0] / stepSize), (int) notesList.get(j)[i][1],
                        (int) notesList.get(j)[i][2], notesList.get(j)[i][3]);
            }
        }

        notes = null;
        notesList.clear();
        mHost.println("size: " + Integer.toString(notesList.size()));
    }

    private void resetLastLayer() {
        mLastLayer = null;
    }

    // Rework for Performance!!
    private void midiCallback(String s) {
        // mHost.println(s);
        if (isNoteModeActive && mPlayingNotes != null) {
            sendNotesToDevice();
        }

        if (s.startsWith(SESSION_MODE_PREFIX) && (mLastLayer == null || mLastLayer == mSessionLayer)) {
            isNoteModeActive = false;
            List<Layer> l = mLayers.getLayers();
            for (int i = 0; i < l.size(); i++) {
                l.get(i).deactivate();
            }
            mSessionLayer.activate();
        }

        if (s.startsWith(NOTE_MODE_PREFIX) || s.startsWith(CHORD_MODE_PREFIX)) {
            isNoteModeActive = true;
            mHost.println("Note or Chord Mode");
            List<Layer> l = mLayers.getLayers();
            for (int i = 0; i < l.size(); i++) {
                l.get(i).deactivate();
            }
            mSessionLayer.activate();

        }

        if (s.startsWith(PRINT_TO_CLIP_PREFIX)) {
            if (s.length() == 44)
                printToClip(s);
            else
                sysexToNotes(s);
        }

        if ((isTrackBankNavigated || mLastLayer != null) && (mVolumeLayer.isActive() || mPanLayer.isActive()
                || mSendsLayer.isActive() || mDeviceLayer.isActive())) {
            for (int i = 0; i < 8; i++) {
                final Track track = mTrackBank.getItemAt(i);

                if (mVolumeLayer.isActive()) {
                    mMidiOut.sendMidi(0xb5, i,
                            track.exists().get() ? new RGBState(track.color().get()).getMessage() : 0);
                    final Parameter volume = track.volume();
                    mMidiOut.sendMidi(0xb4, i, (int) (volume.get() * 127));
                }
                if (mPanLayer.isActive()) {
                    mMidiOut.sendMidi(0xb5, i + 8,
                            track.exists().get() ? new RGBState(track.color().get()).getMessage() : 0);
                    final Parameter pan = track.pan();
                    mMidiOut.sendMidi(0xb4, i + 8, (int) (pan.get() * 127));
                }
                if (mSendsLayer.isActive()) {
                    RGBState r = new RGBState(track.sendBank().getItemAt(mSendIndex).sendChannelColor().get());
                    r = r.getMessage() == 0 ? RGBState.WHITE : r;
                    mMidiOut.sendMidi(0xb5, i + 16,
                            track.exists().get() && track.trackType().get() != "Master" ? r.getMessage() : 0);
                    final Parameter send = track.sendBank().getItemAt(mSendIndex);
                    mMidiOut.sendMidi(0xb4, i + 16, (int) (send.get() * 127));
                }
                if (mDeviceLayer.isActive()) {
                    mMidiOut.sendMidi(0xb5, i, mCursorRemoteControlsPage.getParameter(i).exists().get() ? 79 : 0);
                    final Parameter device = mCursorRemoteControlsPage.getParameter(i);
                    mMidiOut.sendMidi(0xb4, i + 24, (int) (device.get() * 127));
                }
            }
        }
    }

    private void sendNotesToDevice() {
        if (!mDrumPadBank.exists().get()) {
            for (int i = 0; i < 88; i++) {
                mMidiOut.sendMidi(0x8f, i, 21);
                if (mPlayingNotes.length != 0 && mPlayingNotes != null) {
                    mHost.println("PLayingNotes");
                    for (PlayingNote n : mPlayingNotes) {
                        mMidiOut.sendMidi(0x9f, n.pitch(), 21);
                    }
                }
            }
        }
        if (mDrumPadBank.exists().get()) {
            mMidiOut.sendSysex(DAW_DRUM);
            for (int i = 0; i < 64; i++) {
                if (mDrumPadBank.getItemAt(i).exists().get()) {
                    mMidiOut.sendMidi(0x98, 36 + i,
                            new RGBState(mDrumPadBank.getItemAt(i).color().get()).getMessage());
                } else
                    mMidiOut.sendMidi(0x98, 36 + i, RGBState.OFF.getMessage());
                if (mPlayingNotes.length != 0 && mPlayingNotes != null) {
                    for (PlayingNote n : mPlayingNotes) {
                        mMidiOut.sendMidi(0x98, n.pitch() - globalOffset, RGBState.WHITE.getMessage());
                    }
                }
            }

        } else
            mMidiOut.sendSysex(DAW_NOTE);
    }

    private void initMIDI() {
        mMidiIn0 = mHost.getMidiInPort(0);
        mMidiIn1 = mHost.getMidiInPort(1);
        mMidiOut = mHost.getMidiOutPort(0);

        mMidiOut.sendSysex(DAW_MODE);
        mMidiOut.sendSysex(SESSION_LAYOUT);

        mMidiOut.sendMidi(0x90, 99, 3); // Logo Light

        mMidiOut.sendSysex(PRINT_TO_CLIP_ON);
        mMidiOut.sendSysex(DAW_VOLUME_FADER);
        mMidiOut.sendSysex(DAW_PAN_FADER);
        mMidiOut.sendSysex(DAW_SENDS_FADER);
        mMidiOut.sendSysex(DAW_DEVICE_FADER);

        mMidiIn0.setSysexCallback(s -> {
            midiCallback(s);
        });

    }

    private void initHardwareControlls() {
        // FUNCTION BUTTONS
        mShiftButton = createCCButton("shift", 90);
        mShiftButton.isPressed().markInterested();
        mClearButton = createCCButton("clear", 60);
        mClearButton.isPressed().markInterested();
        mDuplicateButton = createCCButton("duplicate", 50);
        mDuplicateButton.isPressed().markInterested();
        mQuantizeButton = createCCButton("quantize", 40);
        mQuantizeButton.isPressed().markInterested();
        mFixedLengthButton = createCCButton("fixed length", 30);
        mFixedLengthButton.isPressed().markInterested();

        // SESSION BUTTON
        mSessionButton = createCCButton("Session", 93);
        mSessionButton.isPressed().markInterested();

        // TRANSPORT BUTTONS
        mPlayButton = createCCButton("play", 20);
        mRecButton = createCCButton("record", 10);

        // NAVIGATION BUTTONS
        mUpButton = createCCButton("up", 80);
        mDownButton = createCCButton("down", 70);
        mLeftButton = createCCButton("left", 91);
        mRightButton = createCCButton("right", 92);
        mUpButton.isPressed().markInterested();
        mDownButton.isPressed().markInterested();
        mLeftButton.isPressed().markInterested();
        mRightButton.isPressed().markInterested();

        // SCENE BUTTONS
        for (int i = 0; i < 8; i++) {
            mRightButtons[i] = createCCButton("scene" + (7 - i), (i + 1) * 10 + 9);
            mRightLights[i] = createLight("scene_led" + (7 - i), (i + 1) * 10 + 9);
            mRightButtons[i].setBackgroundLight(mRightLights[i]);
        }

        // BUTTON MATRIX
        for (int i = 0; i < 8; i++)
            mLeftLights[i] = createLight("left_led" + i, (i + 1) * 10);
        mLeftLight = createLight("left_arrow_led", 91);
        mRightLight = createLight("right_arrow_led", 92);
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                mButtons[i][j] = createNoteButton("" + (i * 10 + (7 - j)), (1 + i) + (j + 1) * 10);
                mPadLights[i][j] = createLight("led" + (i * 10 + (7 - j)), (1 + i) + (j + 1) * 10);
                mButtons[i][j].setBackgroundLight(mPadLights[i][j]);
            }
        }

        // FADER
        for (int i = 0; i < 32; i++) {
            mFader[i] = mHardwareSurface.createHardwareSlider("fader" + i);
            mFader[i].setAdjustValueMatcher(mMidiIn0.createAbsoluteCCValueMatcher(4, i));
        }

        // BOTTOM ROW BUTTONS
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 8; j++) {
                mBottomButtons[i][j] = createCCButton("bottom" + i + "" + j, (i * 100) + (j + 1));
                mBottomLights[i][j] = createLight("bottom_led" + i + "" + j, (i * 100) + (j + 1));
                mBottomButtons[i][j].setBackgroundLight(mBottomLights[i][j]);
                mBottomButtons[i][j].isPressed().markInterested();
            }
        }
    }

    private void initBottomButtons() {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 8; j++) {
                int jx = j;
                mSessionLayer.bindLightState(() -> {
                    return RGBState.DARKGREY;
                }, mBottomLights[i][j]);
                if (i == 1) {
                    mSessionLayer.bindLightState(() -> {
                        if (mFixedLengthButton.isPressed().get() && FIXED_LENGTH_PRESS_DELAY) {
                            if (FIXED_LENGTH_VALUE >= (jx + 1))
                                return RGBState.BLUE_PULS;
                            else
                                return RGBState.DARKGREY_PULS;
                        }

                        if (mTrackBank.cursorIndex().get() == jx) {
                            if (new RGBState(mTrackBank.getItemAt(jx).color().get()).getMessage() == RGBState.GREY
                                    .getMessage())
                                return RGBState.WHITE;
                            return new RGBState(mTrackBank.getItemAt(jx).color().get());
                        }
                        if (mTrackBank.getItemAt(jx).trackType().get() == "Master"
                                || mTrackBank.getItemAt(jx).trackType().get() == "Effect")
                            return RGBState.GREY;
                        if (mTrackBank.getItemAt(jx).exists().get())
                            return RGBState.DARKGREY;
                        return RGBState.OFF;
                    }, mBottomLights[i][j]);
                    mArmRecLayer.bindLightState(() -> {
                        if (mTrackBank.getItemAt(jx).arm().getAsBoolean())
                            return RGBState.RED;
                        if (mTrackBank.getItemAt(jx).exists().get()
                                && mTrackBank.getItemAt(jx).trackType().get() != "Master"
                                && mTrackBank.getItemAt(jx).trackType().get() != "Effect")
                            return RGBState.DARKRED;
                        return RGBState.OFF;
                    }, mBottomLights[i][j]);
                    mSoloLayer.bindLightState(() -> {
                        if (mTrackBank.getItemAt(jx).solo().getAsBoolean())
                            return RGBState.YELLOW;
                        if (mTrackBank.getItemAt(jx).exists().get())
                            return new RGBState(15);
                        return RGBState.OFF;
                    }, mBottomLights[i][j]);
                    mMuteLayer.bindLightState(() -> {
                        if (mTrackBank.getItemAt(jx).mute().getAsBoolean())
                            return RGBState.ORANGE;
                        if (mTrackBank.getItemAt(jx).exists().get())
                            return new RGBState(11);
                        return RGBState.OFF;
                    }, mBottomLights[i][j]);
                    mStopLayer.bindLightState(() -> {
                        if (mTrackBank.getItemAt(jx).trackType().get() != "Master"
                                && mTrackBank.getItemAt(jx).exists().get()
                                && !mTrackBank.getItemAt(jx).isStopped().get())
                            return RGBState.RED;
                        return RGBState.OFF;
                    }, mBottomLights[i][j]);
                    mSessionLayer.bindPressed(mBottomButtons[i][j], () -> {
                        if (mFixedLengthButton.isPressed().get()) {
                            FIXED_LENGTH_VALUE = jx + 1;
                            int num = mTransport.timeSignature().numerator().get();
                            mTransport.getClipLauncherPostRecordingTimeOffset().set(FIXED_LENGTH_VALUE * num);
                            return;
                        }

                        mTrackBank.cursorIndex().set(jx);
                        if (mDuplicateButton.isPressed().getAsBoolean())
                            mCursorTrack.duplicate();
                        else if (mClearButton.isPressed().getAsBoolean())
                            mCursorTrack.deleteObject();
                    });
                    mArmRecLayer.bindPressed(mBottomButtons[i][j], mTrackBank.getItemAt(j).arm());
                    mMuteLayer.bindPressed(mBottomButtons[i][j], mTrackBank.getItemAt(j).mute().toggleAction());
                    // WORK (GET PREFERECES)
                    mSoloLayer.bindPressed(mBottomButtons[i][j], () -> {
                        Track t = mTrackBank.getItemAt(jx);
                        if (!mShiftButton.isPressed().getAsBoolean()) {
                            if (t.solo().get())
                                t.solo().set(false);
                            else
                                t.solo().set(true);
                        } else
                            mTrackBank.getItemAt(jx).solo().toggle();
                    });
                    mStopLayer.bindPressed(mBottomButtons[i][j], mTrackBank.getItemAt(j).stopAction());
                }
            }
        }
        mSessionLayer.bindPressed(mBottomButtons[0][0], () -> {
            if (mShiftButton.isPressed().getAsBoolean())
                mApplication.undo();
            else {
                mHost.scheduleTask(() -> {
                    if (mBottomButtons[0][0].isPressed().get()) {
                        isTemporarySwitch = true;
                    } else {
                        mTempLastLayer = mArmRecLayer;
                    }
                }, 300);
                switchLayer(mArmRecLayer);

            }
        });
        mSessionLayer.bindPressed(mBottomButtons[0][1], () -> {
            if (mShiftButton.isPressed().getAsBoolean())
                mApplication.redo();
            else {
                mHost.scheduleTask(() -> {
                    if (mBottomButtons[0][1].isPressed().get()) {
                        isTemporarySwitch = true;
                    } else {
                        mTempLastLayer = mMuteLayer;
                    }
                }, 300);
                switchLayer(mMuteLayer);
            }
        });
        mSessionLayer.bindPressed(mBottomButtons[0][2], () -> {
            if (mShiftButton.isPressed().getAsBoolean())
                mTransport.isMetronomeEnabled().toggle();
            else {
                mHost.scheduleTask(() -> {
                    if (mBottomButtons[0][2].isPressed().get()) {
                        isTemporarySwitch = true;
                    } else {
                        mTempLastLayer = mSoloLayer;
                    }
                }, 300);

                switchLayer(mSoloLayer);
            }
        });
        mSessionLayer.bindPressed(mBottomButtons[0][3], () -> {
            mHost.scheduleTask(() -> {
                if (mBottomButtons[0][3].isPressed().get()) {
                    isTemporarySwitch = true;
                } else {
                    mTempLastLayer = mVolumeLayer;
                }
            }, 300);

            switchLayer(mVolumeLayer);
            if (mVolumeLayer.isActive())
                mMidiOut.sendSysex(DAW_FADER_ON + DAW_VOLUME);
            else
                mMidiOut.sendSysex(DAW_FADER_OFF + DAW_VOLUME);
        });

        mSessionLayer.bindPressed(mBottomButtons[0][4], () -> {
            mHost.scheduleTask(() -> {
                if (mBottomButtons[0][4].isPressed().get()) {
                    isTemporarySwitch = true;
                } else {
                    mTempLastLayer = mPanLayer;
                }
            }, 300);

            switchLayer(mPanLayer);
            if (mPanLayer.isActive())
                mMidiOut.sendSysex(DAW_FADER_ON + DAW_PAN);
            else
                mMidiOut.sendSysex(DAW_FADER_OFF + DAW_VOLUME);
        });
        mSessionLayer.bindPressed(mBottomButtons[0][5], () -> {
            if (mShiftButton.isPressed().getAsBoolean())
                mTransport.tapTempo();
            else {
                mHost.scheduleTask(() -> {
                    if (mBottomButtons[0][5].isPressed().get()) {
                        isTemporarySwitch = true;
                    } else {
                        mTempLastLayer = mSendsLayer;
                    }
                }, 300);

                switchLayer(mSendsLayer);
                if (mSendsLayer.isActive()) {
                    mMidiOut.sendSysex(DAW_FADER_ON + DAW_SENDS);
                    for (int i = 0; i < 8; i++)
                        mActiveBindings[i] = mTrackBank.getItemAt(i).sendBank().getItemAt(mSendIndex)
                                .addBinding(mFader[i + 16]);
                } else {
                    mMidiOut.sendSysex(DAW_FADER_OFF + DAW_VOLUME);
                }
            }
        });

        mSessionLayer.bindPressed(mBottomButtons[0][6], () -> {
            mHost.scheduleTask(() -> {
                if (mBottomButtons[0][6].isPressed().get()) {
                    isTemporarySwitch = true;
                } else {
                    mTempLastLayer = mDeviceLayer;
                }
            }, 300);
            switchLayer(mDeviceLayer);

            if (mDeviceLayer.isActive())
                mMidiOut.sendSysex(DAW_FADER_ON + DAW_DEVICE);
            else
                mMidiOut.sendSysex(DAW_FADER_OFF + DAW_VOLUME);
        });

        mSessionLayer.bindPressed(mBottomButtons[0][7], () -> {
            mHost.scheduleTask(() -> {
                if (mBottomButtons[0][7].isPressed().get()) {
                    isTemporarySwitch = true;
                } else {
                    mTempLastLayer = mStopLayer;
                }
            }, 300);
            switchLayer(mStopLayer);
        });

        // Release Logic
        for (int i = 0; i < 8; i++) {
            mSessionLayer.bindReleased(mBottomButtons[0][i], () -> {
                if (isTemporarySwitch) {
                    switchLayer(mTempLastLayer);
                    if (mDeviceLayer.isActive())
                        mMidiOut.sendSysex(DAW_FADER_ON + DAW_DEVICE);
                    else if (mVolumeLayer.isActive())
                        mMidiOut.sendSysex(DAW_FADER_ON + DAW_VOLUME);
                    else if (mPanLayer.isActive())
                        mMidiOut.sendSysex(DAW_FADER_ON + DAW_PAN);
                    else if (mSendsLayer.isActive())
                        mMidiOut.sendSysex(DAW_FADER_ON + DAW_SENDS);
                    else
                        mMidiOut.sendSysex(DAW_FADER_OFF + DAW_VOLUME);
                }
                isTemporarySwitch = false;
            });
        }

        setBottomLED(mArmRecLayer, 0, RGBState.RED, true);
        setBottomLED(mMuteLayer, 1, RGBState.ORANGE, true);
        setBottomLED(mSoloLayer, 2, RGBState.YELLOW, true);
        setBottomLED(mVolumeLayer, 3, RGBState.WHITE);
        setBottomLED(mPanLayer, 4, RGBState.PURPLE);
        setBottomLED(mSendsLayer, 5, RGBState.GREEN, true);
        setBottomLED(mDeviceLayer, 6, RGBState.BLUE);
        setBottomLED(mStopLayer, 7, RGBState.RED);
    }

    private void setBottomLED(Layer l, int i, RGBState c, boolean shift) {
        mSessionLayer.bindLightState(() -> {
            if (shift && mShiftButton.isPressed().get()) {
                if (i == 2 && mTransport.isMetronomeEnabled().get())
                    return RGBState.BLUE;
                return RGBState.WHITE;
            }
            if (l.isActive())
                return c;
            return RGBState.DARKGREY;
        }, mBottomLights[0][i]);
    }

    private void setBottomLED(Layer l, int i, RGBState c) {
        mSessionLayer.bindLightState(() -> {
            if (mShiftButton.isPressed().get())
                return RGBState.DARKGREY;
            if (l.isActive())
                return c;
            return RGBState.DARKGREY;
        }, mBottomLights[0][i]);
    }

    private void initFunctionButtons() {
        mClearButton.isPressed().markInterested();
        mDuplicateButton.isPressed().markInterested();
        mQuantizeButton.isPressed().markInterested();
        mFixedLengthButton.isPressed().markInterested();

        mSessionLayer.bindPressed(mClearButton, () -> {
            if (isNoteModeActive)
                mCursorClip.clipLauncherSlot().deleteObject();
        });
        mSessionLayer.bindPressed(mDuplicateButton, () -> {
            if (isNoteModeActive && mShiftButton.isPressed().get()) {
                mCursorClip.duplicateContent();
                mCursorClip.getLoopLength().set(mCursorClip.getLoopLength().get() * 2);
            } else if (isNoteModeActive)
                mCursorClip.clipLauncherSlot().duplicateClip();
        });

        mSessionLayer.bindPressed(mQuantizeButton, () -> {
            mHost.println(QUANTIZATION_GRID_SIZE);
            if (mShiftButton.isPressed().get() && mApplication.recordQuantizationGrid().get() == "OFF")
                mApplication.recordQuantizationGrid().set(QUANTIZATION_GRID_SIZE);
            else if (mShiftButton.isPressed().get())
                mApplication.recordQuantizationGrid().set("OFF");
        });

        mSessionLayer.bindPressed(mFixedLengthButton, () -> {
            mHost.scheduleTask(() -> {
                if (mFixedLengthButton.isPressed().get())
                    FIXED_LENGTH_PRESS_DELAY = true;
            }, 300);
            if (mTransport.clipLauncherPostRecordingAction().get() != "off")
                mTransport.clipLauncherPostRecordingAction().set("off");
            else
                mTransport.clipLauncherPostRecordingAction().set("play_recorded");
        });

        mSessionLayer.bindReleased(mFixedLengthButton, () -> FIXED_LENGTH_PRESS_DELAY = false);

        mSessionLayer.bindLightState(() -> {
            if (mClearButton.isPressed().get())
                return RGBState.WHITE;
            else
                return RGBState.DARKGREY;
        }, mLeftLights[5]);
        mSessionLayer.bindLightState(() -> {
            if (mDuplicateButton.isPressed().get() || mShiftButton.isPressed().get())
                return RGBState.WHITE;
            else
                return RGBState.DARKGREY;
        }, mLeftLights[4]);
        mSessionLayer.bindLightState(() -> {
            if (mShiftButton.isPressed().get() && mApplication.recordQuantizationGrid().get() == "OFF")
                return RGBState.RED;
            if (mShiftButton.isPressed().get() && mApplication.recordQuantizationGrid().get() != "OFF")
                return RGBState.GREEN;

            if (mQuantizeButton.isPressed().get())
                return RGBState.WHITE;
            else
                return RGBState.DARKGREY;
        }, mLeftLights[3]);
        mSessionLayer.bindLightState(() -> {
            if (mTransport.clipLauncherPostRecordingAction().get() != "off")
                return RGBState.BLUE;
            else
                return RGBState.DARKGREY;
        }, mLeftLights[2]);

    }

    private void initTransport() {
        mSessionLayer.bindPressed(mPlayButton, () -> {
            mTransport.playStartPosition().set(0.0); // Usefull?
            mTransport.play();
        });
        // mSessionLayer.bindPressed(mRecButton, mTransport.recordAction());
        mSessionLayer.bindPressed(mRecButton, mTransport.isClipLauncherOverdubEnabled().toggleAction());
        mSessionLayer.bindLightState(() -> {
            if (mTransport.isPlaying().get())
                return RGBState.GREEN;
            return RGBState.DARKGREY;
        }, mLeftLights[1]);
        mSessionLayer.bindLightState(() -> {
            if (mTransport.playPositionInSeconds().get() < 0.0)
                return RGBState.RED_BLINK;
            if (mTransport.isClipLauncherOverdubEnabled().get()/* mTransport.isArrangerRecordEnabled().get() */)
                return RGBState.RED;
            return RGBState.DARKGREY;
        }, mLeftLights[0]);
    }

    private void initNavigation() {
        mSessionLayer.bindPressed(mSessionButton, () -> switchLayer(mSessionLayer));

        mSessionLayer.bindPressed(mUpButton, () -> {
            if (mSessionButton.isPressed().get())
                pressedAction(mUpButton, () -> mSessionOverviewTrackBank.sceneBank().scrollBackwards());
            else if (isNoteModeActive)
                pressedAction(mUpButton, () -> {
                    updateNoteTable("UP_PAGE");
                });
            else
                pressedAction(mUpButton, () -> mSceneBank.scrollBackwards());
        });
        mSessionLayer.bindPressed(mDownButton, () -> {
            if (mSessionButton.isPressed().get())
                pressedAction(mDownButton, () -> mSessionOverviewTrackBank.sceneBank().scrollForwards());
            else if (isNoteModeActive)
                pressedAction(mDownButton, () -> {
                    updateNoteTable("DOWN_PAGE");
                });
            else
                pressedAction(mDownButton, () -> mSceneBank.scrollForwards());
        });
        mSessionLayer.bindPressed(mLeftButton, () -> {
            if (mSessionButton.isPressed().get())
                pressedAction(mLeftButton, () -> mSessionOverviewTrackBank.scrollBackwards());
            else if (isNoteModeActive)
                pressedAction(mLeftButton, () -> {
                    updateNoteTable("DOWN");
                });
            else
                pressedAction(mLeftButton, () -> mTrackBank.scrollBackwards());
        });
        mSessionLayer.bindPressed(mRightButton, () -> {
            if (mSessionButton.isPressed().get())
                pressedAction(mRightButton, () -> mSessionOverviewTrackBank.scrollForwards());
            else if (isNoteModeActive)
                pressedAction(mRightButton, () -> {
                    updateNoteTable("UP");
                });
            else
                pressedAction(mRightButton, () -> mTrackBank.scrollForwards());
        });

        mSessionLayer.bindLightState(
                () -> {
                    if (mSessionButton.isPressed().get() && mSessionOverviewTrackBank.canScrollBackwards().get()
                            || !mSessionButton.isPressed().get() && mTrackBank.canScrollBackwards().get())
                        return RGBState.WHITE;
                    else
                        return RGBState.DARKGREY;
                }, mLeftLight);
        mSessionLayer.bindLightState(
                () -> {
                    if (mSessionButton.isPressed().get() && mSessionOverviewTrackBank.canScrollForwards().get()
                            || !mSessionButton.isPressed().get() && mTrackBank.canScrollForwards().get())
                        return RGBState.WHITE;
                    else
                        return RGBState.DARKGREY;
                }, mRightLight);
        mSessionLayer.bindLightState(
                () -> {
                    if (mSessionButton.isPressed().get()
                            && mSessionOverviewTrackBank.sceneBank().canScrollBackwards().get()
                            || !mSessionButton.isPressed().get() && mSceneBank.canScrollBackwards().get())
                        return RGBState.WHITE;
                    else
                        return RGBState.DARKGREY;
                }, mLeftLights[7]);
        mSessionLayer.bindLightState(
                () -> {
                    if (mSessionButton.isPressed().get()
                            && mSessionOverviewTrackBank.sceneBank().canScrollForwards().get()
                            || !mSessionButton.isPressed().get() && mSceneBank.canScrollForwards().get())
                        return RGBState.WHITE;
                    else
                        return RGBState.DARKGREY;
                }, mLeftLights[6]);
    }

    private void pressedAction(HardwareButton button, Runnable action) {
        action.run();
        longPressed(button, action);
    }

    private void longPressed(HardwareButton button, Runnable action) {
        longPressed(button, action, (long) 300.0);
    }

    private void longPressed(HardwareButton button, Runnable action, long timeout) {
        isTrackBankNavigated = true;
        mHost.scheduleTask(() -> isTrackBankNavigated = false, (long) 100.0);

        if (!longPressedIsInUse) {
            longPressedIsInUse = true;
            mHost.scheduleTask(() -> {
                if (button.isPressed().get()) {
                    action.run();
                    longPressed(button, action, (long) 100.0);
                } else {
                    longPressedIsInUse = false;
                }
            }, timeout);
        }
        longPressedIsInUse = false;
    }

    private void initScenes() {
        mSceneBank = mTrackBank.sceneBank();
        mSceneBank.setIndication(true);
        mSceneBank.canScrollBackwards().markInterested();
        mSceneBank.canScrollForwards().markInterested();
        mSceneBank.cursorIndex().markInterested();
        mSceneBank.scrollPosition().markInterested();

        for (int i = 0; i < 8; i++) {
            final int ix = i;
            Scene s = mSceneBank.getScene(7 - i);
            s.exists().markInterested();
            s.color().markInterested();

            mSessionLayer.bindPressed(mRightButtons[i], () -> {
                if (mShiftButton.isPressed().get())
                    s.launchWithOptions("none", "continue_immediately");
                else if (!mSessionButton.isPressed().get())
                    s.launch();
            });

            mSessionLayer.bindReleased(mRightButtons[i], () -> {
                if (mShiftButton.isPressed().get())
                    s.launchLastClipWithOptions("none", "continue_immediately");
            });
        }

        for (int i = 0; i < 8; i++) {
            int ix = i;
            mSessionLayer.bindLightState(() -> {
                if (mSessionButton.isPressed().get())
                    return RGBState.OFF;
                if (mSceneBank.getScene(7 - ix).exists().get())
                    return RGBState.DARKGREY;
                return RGBState.OFF;
            }, mRightLights[ix]);
        }
    }

    private void initPadMatrix() {
        for (int i = 0; i < 8; i++) {
            final Track track = mTrackBank.getItemAt(i);
            track.arm().markInterested();
            track.mute().markInterested();
            track.solo().markInterested();
            track.volume().markInterested();
            track.pan().markInterested();
            track.color().markInterested();
            track.exists().markInterested();
            track.name().markInterested();
            track.trackType().markInterested();
            track.isStopped().markInterested();

            for (int j = 0; j < 8; j++) {
                track.sendBank().getItemAt(j).markInterested();
                track.sendBank().getItemAt(j).exists().markInterested();
                track.sendBank().getItemAt(j).sendChannelColor().markInterested();
            }
            final ClipLauncherSlotBank slotBank = track.clipLauncherSlotBank();
            slotBank.setIndication(true);
            for (int j = 0; j < 8; j++) {
                final int ix = i;
                final int jx = j;
                final ClipLauncherSlot slot = slotBank.getItemAt(7 - j);
                slot.isSelected().markInterested();
                slot.isPlaybackQueued().markInterested();
                slot.isPlaying().markInterested();
                slot.isRecording().markInterested();
                slot.isRecordingQueued().markInterested();
                slot.isStopQueued().markInterested();
                slot.hasContent().markInterested();
                slot.color().markInterested();
                mSessionLayer.bindPressed(mButtons[i][j], () -> {
                    if (mSessionButton.isPressed().get()) {
                        mTrackBank.scrollPosition().set(ix * 8);
                        mSceneBank.scrollPosition().set((7 - jx) * 8);
                        mHost.println(String.valueOf((7 - jx) * 8));
                        return;
                    }

                    if (mClearButton.isPressed().get())
                        slot.deleteObject();
                    else if (mDuplicateButton.isPressed().get() && mShiftButton.isPressed().get()) {
                        slot.select();
                        mCursorClip.duplicateContent();
                        mCursorClip.getLoopLength().set(mCursorClip.getLoopLength().get() * 2);
                    } else if (mDuplicateButton.isPressed().get())
                        slot.duplicateClip();
                    else if (mQuantizeButton.isPressed().get()) {
                        slot.select();
                        mCursorClip.quantize(1.0);
                    } else {
                        if (mShiftButton.isPressed().get())
                            slot.select();
                        else
                            slot.launch();

                        // ALEX FEATURE
                        // if (mShiftButton.isPressed().get())
                        // slot.launchWithOptions("none", "continue_immediately");
                        // else
                        // slot.launch();
                    }
                });

                // ALEX FEATURE
                // mSessionLayer.bindReleased(mButtons[i][j], () -> {
                // if (mShiftButton.isPressed().get())
                // track.launchLastClipWithOptions("none", "continue_immediately");
                // });

                ClipTimeout[ix][jx] = true;

                mSessionLayer.bindLightState(() -> {
                    if (mSessionButton.isPressed().get()) {
                        updateSessionOverview();
                        if (mSessionOverview[ix][jx] == 3)
                            return RGBState.BLUE;
                        if (mSessionOverview[ix][jx] == 2)
                            return RGBState.GREEN_PULS;
                        if (mSessionOverview[ix][jx] == 1)
                            return RGBState.WHITE;
                        else
                            return RGBState.OFF;
                    }

                    if (slot.isRecording().getAsBoolean())
                        return RGBState.RED_PULS;
                    if (slot.isPlaybackQueued().getAsBoolean() && ClipTimeout[ix][jx]) {
                        mHost.scheduleTask(() -> ClipTimeout[ix][jx] = false, 50);
                        return new RGBState(slot.color().get());
                    } else if (slot.isPlaybackQueued().getAsBoolean() && !slot.isPlaying().get())
                        return RGBState.GREEN_BLINK;
                    if (slot.isPlaybackQueued().getAsBoolean())
                        return RGBState.GREEN_BLINK;
                    if (slot.isPlaying().getAsBoolean() && mTransport.isClipLauncherOverdubEnabled().get()) {
                        ClipTimeout[ix][jx] = true;
                        return RGBState.RED_PULS;
                    }
                    if (slot.isPlaying().getAsBoolean()) {
                        ClipTimeout[ix][jx] = true;
                        return RGBState.GREEN_PULS;
                    } else if (slot.isRecordingQueued().getAsBoolean())
                        return RGBState.RED_BLINK;
                    else if (slot.isStopQueued().getAsBoolean())
                        return RGBState.YELLOW;
                    else if (slot.hasContent().get())
                        return new RGBState(slot.color().get());
                    else if (track.arm().getAsBoolean())
                        return RGBState.TRACK_ARM;
                    else
                        return RGBState.OFF;
                }, mPadLights[i][j]);
            }
        }
    }

    private HardwareButton createCCButton(String name, int midi) {
        final HardwareButton button = mHardwareSurface.createHardwareButton(name);
        button.pressedAction().setActionMatcher(mMidiIn0.createCCActionMatcher(0, midi, 127));
        button.releasedAction().setActionMatcher(mMidiIn0.createCCActionMatcher(0, midi, 0));

        return button;
    };

    private HardwareButton createNoteButton(String name, int midi) {
        final HardwareButton button = mHardwareSurface.createHardwareButton(name);
        button.pressedAction().setActionMatcher(mMidiIn0.createNoteOnActionMatcher(0, midi));
        button.releasedAction().setActionMatcher(mMidiIn0.createNoteOffActionMatcher(0, midi));

        return button;
    };

    private MultiStateHardwareLight createLight(String name, int midi) {
        final MultiStateHardwareLight light = mHardwareSurface.createMultiStateHardwareLight(name);
        light.state().onUpdateHardware(new Consumer<RGBState>() {
            @Override
            public void accept(RGBState state) {
                if (state != null)
                    RGBState.sendSys(mMidiOut, midi, state); // TO Do: Try!
            }
        });
        light.setColorToStateFunction(color -> new RGBState(color));

        return light;
    }

    private void switchLayer(Layer layer) {
        mMidiOut.sendSysex(DAW_FADER_OFF + DAW_VOLUME);
        mLastLayer = layer;
        mHost.scheduleTask(() -> resetLastLayer(), (long) 100);
        for (int i = 0; i < 8; i++) {
            if (mActiveBindings[i] != null)
                mActiveBindings[i].removeBinding();
        }

        List<Layer> l = mLayers.getLayers();
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i) == layer && !layer.isActive())
                l.get(i).activate();
            else
                l.get(i).deactivate();
        }
        mSessionLayer.activate();
    }

    private void initLayers() {
        mSessionLayer = new Layer(mLayers, "Session");
        mArmRecLayer = new Layer(mLayers, "Arm Record");
        mMuteLayer = new Layer(mLayers, "Mute");
        mSoloLayer = new Layer(mLayers, "Solo");
        mVolumeLayer = new Layer(mLayers, "Volume");
        mPanLayer = new Layer(mLayers, "Pan");
        mSendsLayer = new Layer(mLayers, "Sends");
        mDeviceLayer = new Layer(mLayers, "Device");
        mStopLayer = new Layer(mLayers, "Stop");
    }

    private void initVolumeLayer() {
        for (int i = 0; i < 8; i++) {
            final Track track = mTrackBank.getItemAt(i);
            final Parameter parameter = track.volume();
            mVolumeLayer.bind(mFader[i], parameter);
            mVolumeLayer.bindPressed(mRightButtons[7 - i], () -> {});
            mVolumeLayer.bindLightState(() -> RGBState.OFF, mRightLights[7 - i]);
        }
    }

    private void initPanLayer() {
        for (int i = 0; i < 8; i++) {
            int ix = i;
            final Track track = mTrackBank.getItemAt(ix);
            final Parameter parameter = track.pan();
            mPanLayer.bind(mFader[ix + 8], parameter);
            mPanLayer.bindPressed(mRightButtons[7 - i], () -> {});
            mPanLayer.bindLightState(() -> RGBState.OFF, mRightLights[7 - i]);
        }
    }

    private void initSendsLayer() {
        for (int i = 0; i < 8; i++) {
            int ix = i;
            final Track track = mTrackBank.getItemAt(i);
            mSend[i] = track.sendBank().getItemAt(0);
            for (int j = 0; j < 8; j++)
                track.sendBank().getItemAt(ix).exists().markInterested();
            mSendsLayer.bindPressed(mRightButtons[7 - i], () -> {
                if (mTrackBank.getItemAt(ix).sendBank().getItemAt(ix).exists().getAsBoolean())
                    mSendIndex = ix;
                for (int j = 0; j < 8; j++) {
                    if (mActiveBindings[7 - j] != null)
                        mActiveBindings[7 - j].removeBinding();
                    mActiveBindings[7 - j] = mTrackBank.getItemAt(7 - j).sendBank().getItemAt(mSendIndex)
                            .addBinding(mFader[(7 - j) + 16]);
                }

            });
            mSendsLayer.bindLightState(() -> {
                RGBState r = new RGBState(track.sendBank().getItemAt(ix).sendChannelColor().get());
                if (mSendIndex == ix)
                    return r.getMessage() != 0 ? r : RGBState.WHITE;
                else if (track.sendBank().getItemAt(ix).exists().get())
                    return RGBState.DARKGREY;
                return RGBState.OFF;
            }, mRightLights[7 - i]);
        }

    }

    private void initDeviceLayer() {
        for (int i = 0; i < 8; i++) {
            final int ix = i;
            final Parameter parameter = mCursorRemoteControlsPage.getParameter(i);
            mDeviceLayer.bind(mFader[i + 24], parameter);
            mDeviceLayer.bindPressed(mRightButtons[7 - i], () -> {
                mDeviceBank.getItemAt(ix).selectInEditor();// set(ix);
                mDeviceBank.getItemAt(ix).isRemoteControlsSectionVisible().set(true);
            });
            mDeviceLayer.bindLightState(() -> {
                if (mCursorDevice.position().get() == ix)
                    return RGBState.WHITE;
                else if (mDeviceBank.getItemAt(ix).exists().get())
                    return RGBState.DARKGREY;
                return RGBState.OFF;
            }, mRightLights[7 - i]);
        }
    }

    private void initSessionOverview() {
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                ClipLauncherSlotBank slotBank = mSessionOverviewTrackBank.getItemAt(i).clipLauncherSlotBank();
                ClipLauncherSlot s = slotBank.getItemAt(j);
                s.hasContent().markInterested();
                s.isPlaying().markInterested();
            }
        }
        sessionOverview();
    }

    private void sessionOverview() {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                mSessionOverview[i][j] = 0;
            }
        }
    }

    private void updateSessionOverview() {
        sessionOverview();
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                ClipLauncherSlotBank slotBank = mSessionOverviewTrackBank.getItemAt(i).clipLauncherSlotBank();
                ClipLauncherSlot s = slotBank.getItemAt(j);
                if (s.isPlaying().get() && mSessionOverview[(i / 8) % 8][7 - (j / 8) % 8] != 3)
                    mSessionOverview[(i / 8) % 8][7 - (j / 8) % 8] = 2;
                else if (s.hasContent().get() && mSessionOverview[(i / 8) % 8][7 - (j / 8) % 8] != 2)
                    mSessionOverview[(i / 8) % 8][7 - (j / 8) % 8] = 1;
            }
        }

        mSessionOverview[(mTrackBank.scrollPosition().get() / 8) % 8][7
                - (mSceneBank.scrollPosition().get() / 8) % 8] = 3;
        mSessionOverview[((mTrackBank.scrollPosition().get() + 7) / 8) % 8][7
                - (mSceneBank.scrollPosition().get() / 8) % 8] = 3;
        mSessionOverview[(mTrackBank.scrollPosition().get() / 8) % 8][7
                - ((mSceneBank.scrollPosition().get() + 7) / 8) % 8] = 3;
        mSessionOverview[((mTrackBank.scrollPosition().get() + 7) / 8) % 8][7
                - ((mSceneBank.scrollPosition().get() + 7) / 8) % 8] = 3;

    }

    @Override
    public void exit() {
        mMidiOut.sendSysex(STANDALONE_MODE);
        mHost.showPopupNotification("Launchpad Pro Mk3 exited...");
    }

    @Override
    public void flush() {
        mHardwareSurface.updateHardware();
        midiCallback("flush");
    }

    //
    protected final Integer[] noteTable = new Integer[128];
    protected final Integer[] baseNoteTable = new Integer[128];
    private int globalOffset = 0;

    private double notes[][];
    private ArrayList<double[][]> notesList = new ArrayList<double[][]>();
    private Boolean isTrackBankNavigated = false;

    // API Objects
    private HardwareSurface mHardwareSurface;
    private Application mApplication;
    private ControllerHost mHost;
    private MidiIn mMidiIn0;
    private MidiIn mMidiIn1;
    private MidiOut mMidiOut;
    private NoteInput mNoteInput;
    private NoteInput mDrumInput;
    private DrumPadBank mDrumPadBank;
    private PlayingNote[] mPlayingNotes;

    private Transport mTransport;
    private TrackBank mTrackBank;
    private TrackBank mSessionOverviewTrackBank;
    private int mSessionOverview[][] = new int[8][8];
    private SceneBank mSceneBank;
    private AbsoluteHardwareControlBinding[] mActiveBindings = new AbsoluteHardwareControlBinding[8];
    private CursorTrack mCursorTrack;
    private DeviceBank mDeviceBank;
    private CursorDevice mCursorDevice;
    private CursorRemoteControlsPage mCursorRemoteControlsPage;
    private Clip mCursorClip;
    private Send[] mSend = new Send[8];
    private int mSendIndex = 0;
    private Boolean ClipTimeout[][] = new Boolean[8][8];
    private String QUANTIZATION_GRID_SIZE = "1/32";

    // Layers
    private Layers mLayers = new Layers(this);
    private Layer mSessionLayer;
    private Layer mArmRecLayer;
    private Layer mMuteLayer;
    private Layer mSoloLayer;
    private Layer mVolumeLayer;
    private Layer mPanLayer;
    private Layer mSendsLayer;
    private Layer mDeviceLayer;
    private Layer mStopLayer;
    private Layer mLastLayer;
    private Layer mTempLastLayer;
    private Boolean isNoteModeActive = false;

    // Hardware Proxy
    private HardwareButton[][] mButtons = new HardwareButton[8][8];
    private MultiStateHardwareLight[][] mPadLights = new MultiStateHardwareLight[8][8];
    private HardwareButton[] mRightButtons = new HardwareButton[8];
    private MultiStateHardwareLight[] mRightLights = new MultiStateHardwareLight[8];
    private HardwareButton mUpButton;
    private HardwareButton mDownButton;
    private HardwareButton mLeftButton;
    private MultiStateHardwareLight mLeftLight;
    private HardwareButton mRightButton;
    private MultiStateHardwareLight mRightLight;
    private HardwareButton mSessionButton;
    private HardwareButton mPlayButton;
    private HardwareButton mRecButton;
    private HardwareButton mShiftButton;
    private HardwareButton mClearButton;
    private HardwareButton mDuplicateButton;
    private HardwareButton mQuantizeButton;
    private HardwareButton mFixedLengthButton;
    private MultiStateHardwareLight[] mLeftLights = new MultiStateHardwareLight[8];
    private HardwareButton[][] mBottomButtons = new HardwareButton[2][8];
    private MultiStateHardwareLight[][] mBottomLights = new MultiStateHardwareLight[2][8];
    private HardwareSlider[] mFader = new HardwareSlider[32];
}
