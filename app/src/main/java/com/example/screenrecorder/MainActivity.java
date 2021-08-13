package com.example.screenrecorder;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.github.barteksc.pdfviewer.PDFView;
import com.iammert.library.readablebottombar.ReadableBottomBar;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.app.NotificationManager.IMPORTANCE_NONE;
import static androidx.core.app.ServiceCompat.stopForeground;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends AppCompatActivity implements ImageReader.OnImageAvailableListener {

    ImageView mimage_from_gallary;
    int SELECT_PICTURE = 200;
    int SELECT_PDF= 100;
    private boolean isFragmentDisplayed = false;
    PDFView pdfView;
    private boolean isRecording= false;
    FrameLayout frame;
    private static final int CAST_PERMISSION = 20;
    private DisplayMetrics mDisplayMetrics;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaRecorder mMediaRecorder;
    private MediaProjectionManager mediaProjectionManager;
    private int screenDensity;
    Button upload_image,upload_pdf,camera,record;
    Surface surface;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //show live camera
        if(grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            //live camera
            setFragment();
        }else
        {
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mimage_from_gallary = findViewById(R.id.image_from_gallery);
        frame = findViewById(R.id.container);
        pdfView= findViewById(R.id.pdfView);
        mMediaRecorder = new MediaRecorder();
        mDisplayMetrics = new DisplayMetrics();


        requestcamera();
        upload_image= findViewById(R.id.upload_image);
        upload_pdf= findViewById(R.id.upload_pdf);
        camera= findViewById(R.id.camera);
        record= findViewById(R.id.record);

        upload_image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageChooser();
            }
        });

        upload_pdf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectPdfFromStroage();
            }
        });

        camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isFragmentDisplayed) {
                    closeFragment();
                } else {
                    setFragment();
                }
            }
        });
        record.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onClick(View v) {
                if(isRecording){
                    stopRecording();
                    stopForeground(null,ServiceCompat.STOP_FOREGROUND_REMOVE);
                }
                else{
                    mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    getWindowManager().getDefaultDisplay().getMetrics(mDisplayMetrics);
                    prepareRecording();
                    Intent intent = new Intent(MainActivity.this, BackgroundService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent);
                    }
                   else{
                        startService(intent);
                    }
                    startRecording();
                }
            }
        });

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startRecording() {
        // If mMediaProjection is null that means we didn't get a context, lets ask the user
        if (mMediaProjection == null) {
                // This asks for user permissions to capture the screen
                startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), CAST_PERMISSION);
                return;
        }
        mVirtualDisplay = getVirtualDisplay();
        mMediaRecorder.start();
        isRecording = true;
    }

    private void stopRecording() {
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
        }
        isRecording= false;
    }

    public String getCurSysDate() {
        return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
    }

    private void prepareRecording() {



        final String directory = Environment.getExternalStorageDirectory() + File.separator + "Recordings";
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Toast.makeText(this, "Failed to get External Storage", Toast.LENGTH_SHORT).show();
            return;
        }
        final File folder = new File(directory);
        boolean success = true;
        if (!folder.exists()) {
            success = folder.mkdir();
        }
        String filePath;
        if (success) {
            String videoName = ("capture_" + getCurSysDate() + ".mp4");
            filePath = directory + File.separator + videoName;
        } else {
            Toast.makeText(this, "Failed to create Recordings directory", Toast.LENGTH_SHORT).show();
            return;
        }

        int width = mDisplayMetrics.widthPixels;
        int height = mDisplayMetrics.heightPixels;

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mMediaRecorder.setVideoEncodingBitRate(512 * 1000);
        mMediaRecorder.setVideoSize(854, 480);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setOutputFile(filePath);

        try {
            mMediaRecorder.prepare();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        surface = mMediaRecorder.getSurface();

    }
    void requestcamera(){
        if(Build.VERSION.SDK_INT>= Build.VERSION_CODES.M){
            if(checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED){
                ActivityCompat.requestPermissions(this, new String[] {
                        Manifest.permission.CAMERA},121);
            }else{
                //show live footage
                setFragment();
            }
        }else{
            //show live footage
            setFragment();
        }

    }

    void selectPdfFromStroage(){
        Intent browseStroge = new Intent(Intent.ACTION_GET_CONTENT);
        browseStroge.setType("application/pdf");
        browseStroge.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(browseStroge,"Select PDF"),SELECT_PDF);
    }
    
    protected void closeFragment(){
        FragmentManager fragmentManager= getSupportFragmentManager();
        CameraFragment fragment = (CameraFragment) fragmentManager.findFragmentById(R.id.container);
        if(fragment != null){
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.remove(fragment).commit();
            frame.setVisibility(View.GONE);
        }
        isFragmentDisplayed = false;
    }
    int previewHeight =0,previewWidth = 0;
    int sensorOrientation;
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    protected void setFragment(){
        frame.setVisibility(View.VISIBLE);
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        String cameraId = null;
        try {
            cameraId = manager.getCameraIdList()[1];
        }catch (CameraAccessException e) {
            e.printStackTrace();
        }
        CameraFragment fragment;
        CameraFragment camera2fragment = CameraFragment.newInstance(new CameraFragment.ConnectionCallback(){
            @Override
            public void onPreviewSizeChosen(final Size size, final int rotation) {
                previewHeight = size.getHeight();
                previewWidth = size.getWidth();
                Log.d("tryOrientation","rotation: "+rotation+"   orientation: "+getScreenOrientation()+"  "+previewWidth+"   "+previewHeight);
                sensorOrientation = rotation - getScreenOrientation();
                }
                },
                this,
                R.layout.fragment_camera,
                new Size(640, 480));

        camera2fragment.setCamera(cameraId);
        fragment = camera2fragment;
        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
        isFragmentDisplayed= true;
    }

    //TODO getting frames of live camera footage and passing them to model
    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    private Bitmap rgbFrameBitmap;
    @Override
    public void onImageAvailable(ImageReader reader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }
        if (rgbBytes == null) {
            rgbBytes = new int[previewWidth * previewHeight];
        }
        try {
            final Image image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (isProcessingFrame) {
                image.close();
                return;
            }
            isProcessingFrame = true;
            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageUtils.convertYUV420ToARGB8888(
                                    yuvBytes[0],
                                    yuvBytes[1],
                                    yuvBytes[2],
                                    previewWidth,
                                    previewHeight,
                                    yRowStride,
                                    uvRowStride,
                                    uvPixelStride,
                                    rgbBytes);
                        }
                    };

            postInferenceCallback =
                    new Runnable() {
                        @Override
                        public void run() {
                            image.close();
                            isProcessingFrame = false;
                        }
                    };

            processImage();

        } catch (final Exception e) {

            return;
        }

    }


    private void processImage() {
        imageConverter.run();
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);
        postInferenceCallback.run();
    }

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }
    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
        }


    void imageChooser() {

        mimage_from_gallary.setVisibility(View.VISIBLE);
        pdfView.setVisibility(View.GONE);
        // create an instance of the
        // intent of the type image
        Intent i = new Intent();
        i.setType("image/*");
        i.setAction(Intent.ACTION_GET_CONTENT);

        // pass the constant to compare it
        // with the returned requestCode
        startActivityForResult(Intent.createChooser(i, "Select Picture"), SELECT_PICTURE);
    }

    // this function is triggered when user
    // selects the image from the imageChooser
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK ) {

            // compare the resultCode with the
            // SELECT_PICTURE constant
            if (requestCode == SELECT_PICTURE) {
                // Get the url of the image from data
                Uri selectedImageUri = data.getData();
                if (null != selectedImageUri) {
                    // update the preview image in the layout
                    mimage_from_gallary.setImageURI(selectedImageUri);
                    //progressBar.setVisibility(View.GONE);
                }
            }

            //for Pdf
            if(requestCode== SELECT_PDF && data != null){
                mimage_from_gallary.setVisibility(View.GONE);
                pdfView.setVisibility(View.VISIBLE);
                Uri selectPdfUri = data.getData();
                if(selectPdfUri != null) {
                    pdfView.fromUri(selectPdfUri)
                            .defaultPage(0)
                            .spacing(10)
                            .load();
                }
            }

            //for Projection
            if(requestCode == CAST_PERMISSION){
                MediaProjectionCallback mMediaProjectionCallback = new MediaProjectionCallback();
                
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mMediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                        // TODO Register a callback that will listen onStop and release & prepare the recorder for next recording
                        mMediaProjection.registerCallback(mMediaProjectionCallback, null);
                        mVirtualDisplay = getVirtualDisplay();
                        mMediaRecorder.start();


                    }
                },1000);


            }
        }
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                Log.v("callback", "Recording Stopped");
            mMediaProjection = null;
            stopRecording();
        }
    }

    private VirtualDisplay getVirtualDisplay() {
        screenDensity = mDisplayMetrics.densityDpi;
        int width = mDisplayMetrics.widthPixels;
        int height = mDisplayMetrics.heightPixels;

        return mMediaProjection.createVirtualDisplay(this.getClass().getSimpleName(),
                width, height, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.getSurface(), null /*Callbacks*/, null /*Handler*/);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}