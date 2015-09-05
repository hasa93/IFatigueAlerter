package ifa.ifatiguealerter;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.util.Log;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@SuppressWarnings("deprecation")
public class BlinkCounter extends Service implements Camera.PreviewCallback, Camera.ErrorCallback {

    public static final String TAG = "opencv";

    public static final int WIDTH = 320;
    public static final int HEIGHT = 240;
    private static Camera mCamera;

    private CascadeClassifier mJavaDetector;
    private CascadeClassifier mJavaDetectorEye;

    private int mAbsoluteFaceSize = 0;
    private int mAbsoluteEyesSize = 0;

    private boolean preview_flag = true;
    private boolean run_flag = true;

    private static CameraPreview2 mPreview;
    private static Context myContext;
    private static PowerManager powerManager;

    private boolean last_state = true;

    private void initOpenCV() {
        try {
            // load cascade file from application resources

            InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_alt);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File mCascadeFile = new File(cascadeDir, "haarcascade_frontalface_alt.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            // load eye classificator
            InputStream iser = getResources().openRawResource(R.raw.eye_tree);
            File cascadeDirER = getDir("cascadeER", Context.MODE_PRIVATE);
            File cascadeFileER = new File(cascadeDirER, "eye_tree.xml");
            FileOutputStream oser = new FileOutputStream(cascadeFileER);

            byte[] bufferER = new byte[4096];
            int bytesReadER;
            while ((bytesReadER = iser.read(bufferER)) != -1) {
                oser.write(bufferER, 0, bytesReadER);
            }
            iser.close();
            oser.close();

            mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
            if (mJavaDetector.empty()) {
                Log.d(TAG, "Failed to load cascade classifier");
                mJavaDetector = null;
            } else {
                Log.d(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());
            }

            mJavaDetectorEye = new CascadeClassifier(cascadeFileER.getAbsolutePath());
            if (mJavaDetectorEye.empty()) {
                Log.d(TAG, "Failed to load cascade classifier");
                mJavaDetectorEye = null;
            } else {
                Log.d(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());
            }

            //noinspection ResultOfMethodCallIgnored
            cascadeDir.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        myContext = this;
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        initialize();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (OpenCVLoader.initDebug()) {
            initOpenCV();
        } else {
            Log.e(TAG, "OpenCV initialization failed.");
            try {
                stopSelf();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        if (mCamera == null) {
            mCamera = Camera.open(1);
            Log.d(TAG, String.valueOf(Camera.getNumberOfCameras()));
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(WIDTH, HEIGHT);
            Log.i(TAG, parameters.getColorEffect() + " ");

            mCamera.setParameters(parameters);
            mPreview.refreshCamera(mCamera);
            mCamera.setPreviewCallback(this);
            mCamera.startPreview();
            mCamera.setErrorCallback(this);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (run_flag) {
                        if (powerManager.isScreenOn() && !preview_flag) {
                            Log.d(TAG, "starting preview.");
                            mCamera.startPreview();
                            preview_flag = true;
                        } else if (!powerManager.isScreenOn() && preview_flag) {
                            Log.d(TAG, "stopping preview.");
                            mCamera.stopPreview();
                            preview_flag = false;
                        }
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public void initialize() {
        mPreview = new CameraPreview2(myContext, mCamera);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        run_flag = false;
        releaseCamera();
    }

    @SuppressWarnings("unused")
    private boolean hasCamera(Context context) {
        //check if the device has camera
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    private void releaseCamera() {
        // stop and release camera
        if (mCamera != null) {
            mCamera.stopPreview();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        mCamera.release();
                        mCamera = null;
                    } catch (Exception e) {
                        // ignore
                    }
                    Log.d(TAG, "preview stopped.");
                }
            }).start();

            Log.d(TAG, "Releasing camera.");
//			mCamera.lock();
//            mCamera.release();
//            mCamera = null;
        }
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {

        Log.d(TAG, "frame");
        Mat mat = new Mat(HEIGHT, WIDTH, CvType.CV_8UC1);
        mat.put(0, 0, bytes);
        mat = rotationAffineTutorial(mat);

        if (mAbsoluteFaceSize == 0) {
            int height = mat.rows();
            float mRelativeFaceSize = 0.2f;
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
        }

        MatOfRect faces = new MatOfRect();

        if (mJavaDetector != null) {
            mJavaDetector.detectMultiScale(mat, faces, 1.1, 2, 2, new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        }

        Rect[] facesArray = faces.toArray();

        if (facesArray.length > 0)
            Log.d(TAG, "nooffaces :: " + facesArray.length);
        else {
            last_state = true;
            return;
        }
        for (int i = 0; i < 1; i++) {

            Rect r = facesArray[i];
            // compute the eye area
            Rect eyearea = new Rect(r.x + r.width / 8, (int) (r.y + (r.height / 4.5)), r.width - 2 * r.width / 8,
                    (int) (r.height / 3.0));
            // ***
            if (mAbsoluteEyesSize == 0) {
                int height = eyearea.height;
                float mRelativeEyeSize = 0.1f;
                if (Math.round(height * mRelativeEyeSize) > 0) {
                    mAbsoluteEyesSize = Math.round(height * mRelativeEyeSize);
                }
            }
            MatOfRect eyes = new MatOfRect();
            mJavaDetectorEye.detectMultiScale(mat.submat(eyearea), eyes, 1.1, 2, 2, new Size(mAbsoluteEyesSize, mAbsoluteEyesSize), new Size());
            Rect[] eyesArray = eyes.toArray();
            Log.d(TAG, "no of eyes: " + eyesArray.length);
            if (eyesArray.length > 0 && !last_state) {
                WindowChangeDetectingService.BLINKS_COUNT++;
                Log.d(TAG, "blink");
            }
            last_state = eyesArray.length > 0;
        }
    }

    @SuppressWarnings({"SuspiciousNameCombination", "unused"})
    private Mat rotate(Mat mat) {
        double ratio = mat.height() / (double) mat.width();

        int rotatedHeight = mat.height();
        int rotatedWidth = (int) Math.round(mat.height() * ratio);

        Mat mIntermediateMat = new Mat(new Size(rotatedHeight, rotatedWidth), CvType.CV_8UC1);
        Imgproc.resize(mat, mIntermediateMat, new Size(rotatedHeight, rotatedWidth));

        Core.flip(mat.t(), mIntermediateMat, 0);

        Mat ROI = mat.submat(0, mat.rows(), 0, mat.cols());

        mIntermediateMat.copyTo(ROI);
        return mIntermediateMat;
    }

    private Mat rotationAffineTutorial(Mat source) {
        Mat mIntermediateMat;
        // assuming source image's with and height are a pair value:
        int centerX = Math.round(source.width() / 2);
        int centerY = Math.round(source.height() / 2);

        //noinspection SuspiciousNameCombination
        Point center = new Point(centerY, centerX);
        double angle = 90;
        double scale = 1.0;

        double ratio = source.height() / (double) source.width();

        int rotatedHeight = Math.round(source.height());
        int rotatedWidth = (int) Math.round(source.height() * ratio);

        Mat mapMatrix = Imgproc.getRotationMatrix2D(center, angle, scale);

        Size rotatedSize = new Size(rotatedWidth, rotatedHeight);
        mIntermediateMat = new Mat(rotatedSize, source.type());

        Imgproc.warpAffine(source, mIntermediateMat, mapMatrix, mIntermediateMat.size(), Imgproc.INTER_LINEAR);

        Mat ROI = source.submat(0, source.rows(), 0, source.cols());

        mIntermediateMat.copyTo(ROI);

        Mat ret = new Mat(ROI.rows(), ROI.cols(), CvType.CV_8UC1);
        Core.flip(ROI, ret, 0);

        return ROI;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onError(int i, Camera camera) {
        Log.d(TAG, "cam err");
    }
}