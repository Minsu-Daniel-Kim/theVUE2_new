package com.theeyes.theVUE2.CameraController;

import android.hardware.Camera;
import android.util.Log;

import com.theeyes.theVUE2.MotionDetection.MotionDetection;

/**
 * Created by Daniel on 5/31/15.
 */
public class CameraCallback implements Camera.PreviewCallback{

//    private final String PICTURE_PREFIX = "/Pictures/pim/";
    private static final int PICTURE_DELAY = 4000;

    private static final String TAG = "CameraCallback";
    private MotionDetection mMotionDetection;
    private Camera mCamera;

    private long mReferenceTime;


    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {

        if (mMotionDetection.detect(data)) {
            // the delay is necessary to avoid taking a picture while in the
            // middle of taking another. This problem causes a Motorola
            // Milestone to reboot.
            long now = System.currentTimeMillis();
            if (now > mReferenceTime + PICTURE_DELAY) {
                mReferenceTime = now + PICTURE_DELAY;
                Log.i(TAG, "Taking picture");
//                camera.takePicture(null, null, this);
            } else {
                Log.i(TAG, "Not taking picture because not enough time has "
                        + "passed since the creation of the Surface");
            }
        }

    }
}
