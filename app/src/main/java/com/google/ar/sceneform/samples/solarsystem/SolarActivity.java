/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.sceneform.samples.solarsystem;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.SharedCamera;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseTransformableNode;
import com.google.ar.sceneform.ux.FootprintSelectionVisualizer;
import com.google.ar.sceneform.ux.SelectionVisualizer;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.ar.sceneform.ux.TransformationSystem;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore and Sceneform APIs.
 */
public class SolarActivity extends AppCompatActivity implements ImageReader.OnImageAvailableListener{

    private final String TAG = SolarActivity.class.getSimpleName();

    //AR 뷰
    private ArSceneView arSceneView;
    private ArFragment arFragment;

    //ModelRenderable
    ModelRenderable robotRenderable;

    //카메라
    SharedCamera sharedCamera;                      // ARCore shared camera instance, obtained from ARCore session that supports sharing.
    private String cameraId;                        // Camera ID for the camera used by ARCore.
    private ImageReader cpuImageReader;             // Image reader that continuously processes CPU images.
    private Handler backgroundHandler;              // Looper handler.
    private HandlerThread backgroundThread;         // Looper handler thread.
    private int cpuImagesProcessed;                 // Total number of CPU images processed.
    private CameraDevice cameraDevice;              // Camera device. Used by both non-AR and AR modes;

    //카메라 테스트용
    private final AtomicBoolean automatorRun = new AtomicBoolean(false);


    //데이터들
    boolean isArFragmentMode = true;


    private boolean installRequested;

    private GestureDetector gestureDetector;
    private Snackbar loadingMessageSnackbar = null;

    TransformationSystem transformationSystem;

    //쇼핑카트
    ImageView ivCart;




    //Camera device state callback.
    private final CameraDevice.StateCallback cameraDeviceCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.d(TAG, "Camera device ID ["+ camera.getId()+"] opened.");
                    cameraDevice = camera;
//                    createCameraPreviewSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {

                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {

                }
            };



    //Renderable들
    private ModelRenderable sunRenderable;
    private ModelRenderable mercuryRenderable;
    private ModelRenderable venusRenderable;
    private ModelRenderable earthRenderable;
    private ModelRenderable lunaRenderable;
    private ModelRenderable marsRenderable;
    private ModelRenderable jupiterRenderable;
    private ModelRenderable saturnRenderable;
    private ModelRenderable uranusRenderable;
    private ModelRenderable neptuneRenderable;
    private ViewRenderable solarControlsRenderable;

    private final SolarSettings solarSettings = new SolarSettings();

    // True once scene is loaded
    private boolean hasFinishedLoading = false;

    // True once the scene has been placed.
    private boolean hasPlacedSolarSystem = false;

    // Astronomical units to meters ratio. Used for positioning the planets of the solar system.
    private static final float AU_TO_METERS = 0.5f;

    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    // CompletableFuture requires api level 24
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_solar);

        //사용가능한 Device인지 먼저 확인
        checkIsSupportedDeviceOrFinish();

        //권한
        CameraPermissionHelper.requestCameraPermission(this);

        ivCart = findViewById(R.id.iv_cart);

        ivCart.setOnClickListener(v -> {
            Intent intent = new Intent(SolarActivity.this, MainActivity.class);
            startActivity(intent);
        });

        if(isArFragmentMode){
            arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);

            createModelRenderable();

            setOnTapArPlaneListener();


        }else{
            //arView 셋팅
//            arSceneView = findViewById(R.id.ar_scene_view);

            //TransformationSystem
            transformationSystem = makeTransformationSystem();

            createRenderables();

            setGestureDetector();

            setSceneOnTouchListener();
            setSceneOnUpdateListener();

        }


    }

    /**
    * AR 사용가능한 Device인지 확인
    * */
    private void checkIsSupportedDeviceOrFinish(){

        if (!DemoUtils.checkIsSupportedDeviceOrFinish(this)) {
        // Not a supported device.
        return;
        }
    }

    /**
    * arFragment있을때
    * */
    private void createModelRenderable(){
        ModelRenderable.builder()
                .setSource(this, Uri.parse("Mercury.sfb"))
                .build()
                .thenAccept(renderable -> mercuryRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        }
                );

        CompletableFuture<ViewRenderable> solarControlsStage
                =  ViewRenderable.builder().setView(this, R.layout.solar_controls).build();

        CompletableFuture.allOf(
                solarControlsStage)
                .handle(
                        (notUsed, throwable) -> {
                            // When you build a Renderable, Sceneform loads its resources in the background while
                            // returning a CompletableFuture. Call handle(), thenAccept(), or check isDone()
                            // before calling get().

                            if (throwable != null) {
                                DemoUtils.displayError(this, "Unable to load renderable", throwable);
                                return null;
                            }

                            try {
                                solarControlsRenderable = solarControlsStage.get();

                                // Everything finished loading successfully.
                                hasFinishedLoading = true;

                            } catch (InterruptedException | ExecutionException ex) {
                                DemoUtils.displayError(this, "Unable to load renderable", ex);
                            }

                            return null;
                        });

    }

    private void setOnTapArPlaneListener(){
        arFragment.setOnTapArPlaneListener(
                ((hitResult, plane, motionEvent) -> {
                    if(mercuryRenderable==null){
                        return;
                    }

                    Anchor anchor = hitResult.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setParent(arFragment.getArSceneView().getScene());

                    TransformableNode mercury = new TransformableNode(arFragment.getTransformationSystem());
                    mercury.setParent(anchorNode);
                    mercury.setLocalScale(new Vector3(0.5f, 0.5f, 0.5f));
                    mercury.setRenderable(mercuryRenderable);
                    mercury.select();

                    Node infoCard = new Node();
                    infoCard.setParent(anchorNode);
                    infoCard.setRenderable(solarControlsRenderable);
                    infoCard.setLocalScale(new Vector3(0.7f, 0.7f, 0.7f));
                    infoCard.setLocalPosition(new Vector3(0.0f, 0.6f, 0.0f));

                    View solarControlsView = solarControlsRenderable.getView();

                    TextView tv = solarControlsView.findViewById(R.id.tv);
//                    SeekBar sb = solarControlsView.findViewById(R.id.sb);
//
//                    sb.setProgress(1000);
//                    sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//                        @Override
//                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                            tv.setText(progress+"원");
//                        }
//
//                        @Override
//                        public void onStartTrackingTouch(SeekBar seekBar) {
//
//                        }
//
//                        @Override
//                        public void onStopTrackingTouch(SeekBar seekBar) {
//
//                        }
//                    });
                })
        );
    }

    private void openCameraForSharing(){

        Session sharedSession = arFragment.getArSceneView().getSession();

        sharedCamera = sharedSession.getSharedCamera();

        cameraId = sharedSession.getCameraConfig().getCameraId();

        Size desiredCpuImageSize = sharedSession.getCameraConfig().getImageSize();
        cpuImageReader = ImageReader.newInstance(
                desiredCpuImageSize.getWidth(),
                desiredCpuImageSize.getHeight(),
                ImageFormat.YUV_420_888,
                2
        );
        cpuImageReader.setOnImageAvailableListener(this, backgroundHandler);

        sharedCamera.setAppSurfaces(cameraId, Arrays.asList(cpuImageReader.getSurface()));

//        try{
//            CameraDevice.StateCallback wrappedCallback =
//                    sharedCamera.createARDeviceStateCallback(camera);
//        }

    }

    /**
    * backgroundHandler관리
    * */
    private void startBackgroundThread(){
        backgroundThread = new HandlerThread(Data.NAME_BACKGROUND_HANDLER);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread(){
        if(backgroundThread != null) {
            backgroundThread.quitSafely();
            try{
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
    * ModelRenderable 생성
    * */
    private void createRenderables(){

          // Build all the planet models.
          CompletableFuture<ModelRenderable> sunStage =
                  ModelRenderable.builder().setSource(this, Uri.parse("Sol.sfb")).build();
          CompletableFuture<ModelRenderable> mercuryStage =
                  ModelRenderable.builder().setSource(this, Uri.parse("Mercury.sfb")).build();
          CompletableFuture<ModelRenderable> venusStage =
                  ModelRenderable.builder().setSource(this, Uri.parse("Venus.sfb")).build();
          CompletableFuture<ModelRenderable> earthStage =
                  ModelRenderable.builder().setSource(this, Uri.parse("Earth.sfb")).build();
          CompletableFuture<ModelRenderable> lunaStage =
                  ModelRenderable.builder().setSource(this, Uri.parse("Luna.sfb")).build();
          CompletableFuture<ModelRenderable> marsStage =
                  ModelRenderable.builder().setSource(this, Uri.parse("Mars.sfb")).build();
          CompletableFuture<ModelRenderable> jupiterStage =
                  ModelRenderable.builder().setSource(this, Uri.parse("Jupiter.sfb")).build();
          CompletableFuture<ModelRenderable> saturnStage =
                  ModelRenderable.builder().setSource(this, Uri.parse("Saturn.sfb")).build();
          CompletableFuture<ModelRenderable> uranusStage =
                  ModelRenderable.builder().setSource(this, Uri.parse("Uranus.sfb")).build();
          CompletableFuture<ModelRenderable> neptuneStage =
                  ModelRenderable.builder().setSource(this, Uri.parse("Neptune.sfb")).build();

          //2D View
          CompletableFuture<ViewRenderable> solarControlsStage
                  =  ViewRenderable.builder().setView(this, R.layout.solar_controls).build();

          CompletableFuture.allOf(
                  sunStage,
                  mercuryStage,
                  venusStage,
                  earthStage,
                  lunaStage,
                  marsStage,
                  jupiterStage,
                  saturnStage,
                  uranusStage,
                  neptuneStage,
                  solarControlsStage)
                  .handle(
                          (notUsed, throwable) -> {
                              // When you build a Renderable, Sceneform loads its resources in the background while
                              // returning a CompletableFuture. Call handle(), thenAccept(), or check isDone()
                              // before calling get().

                              if (throwable != null) {
                                  DemoUtils.displayError(this, "Unable to load renderable", throwable);
                                  return null;
                              }

                              try {
                                  sunRenderable = sunStage.get();
                                  mercuryRenderable = mercuryStage.get();
                                  venusRenderable = venusStage.get();
                                  earthRenderable = earthStage.get();
                                  lunaRenderable = lunaStage.get();
                                  marsRenderable = marsStage.get();
                                  jupiterRenderable = jupiterStage.get();
                                  saturnRenderable = saturnStage.get();
                                  uranusRenderable = uranusStage.get();
                                  neptuneRenderable = neptuneStage.get();
                                  solarControlsRenderable = solarControlsStage.get();

                                  // Everything finished loading successfully.
                                  hasFinishedLoading = true;

                              } catch (InterruptedException | ExecutionException ex) {
                                  DemoUtils.displayError(this, "Unable to load renderable", ex);
                              }

                              return null;
                          });

    }

    /**
    * GestureDetectore
    * */
    private void setGestureDetector(){
        // Set up a tap gesture detector.
        gestureDetector =
                new GestureDetector(
                        this,
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapUp(MotionEvent e) {
                                onSingleTap(e);
                                return true;
                            }

                            @Override
                            public boolean onDown(MotionEvent e) {
                                return true;
                            }
                        });
    }

    /**
    * SceneOnTouchListener
    * */
    private void setSceneOnTouchListener(){
        // Set a touch listener on the Scene to listen for taps.
        arSceneView
            .getScene()
            .setOnTouchListener(
                    (HitTestResult hitTestResult, MotionEvent event) -> {
                        // If the solar system hasn't been placed yet, detect a tap and then check to see if
                        // the tap occurred on an ARCore plane to place the solar system.
                        if (!hasPlacedSolarSystem) {
                            return gestureDetector.onTouchEvent(event);
                        }

                        // Otherwise return false so that the touch event can propagate to the scene.
                        return false;
                    });
    }

    /**
    * SceneOnUpdateListener
    * */

    private void setSceneOnUpdateListener(){
        // Set an update listener on the Scene that will hide the loading message once a Plane is
        // detected.
        arSceneView
            .getScene()
            .addOnUpdateListener(
                frameTime -> {
                    if (loadingMessageSnackbar == null) {
                        return;
                    }

                    Frame frame = arSceneView.getArFrame();
                    if (frame == null) {
                        return;
                    }

                    if (frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
                        return;
                    }

                    for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
                        if (plane.getTrackingState() == TrackingState.TRACKING) {
                            hideLoadingMessage();
                        }
                    }
                });
    }





    @Override
    protected void onResume() {
        super.onResume();
        if (arSceneView == null) {
          return;
        }

        if (arSceneView.getSession() == null) {
            // If the session wasn't created yet, don't resume rendering.
            // This can happen if ARCore needs to be updated or permissions are not granted yet.
            try {
                Session session = DemoUtils.createArSession(this, installRequested);
                if (session == null) {
                    installRequested = DemoUtils.hasCameraPermission(this);
                    return;
                } else {
                    arSceneView.setupSession(session);
                }
            } catch (UnavailableException e) {
                DemoUtils.handleSessionException(this, e);
            }
        }

        try {
            arSceneView.resume();
        } catch (CameraNotAvailableException ex) {
            DemoUtils.displayError(this, "Unable to get camera", ex);
            finish();
            return;
        }

        if (arSceneView.getSession() != null) {
            showLoadingMessage();
        }

        startBackgroundThread();

        openCameraForSharing();


    }

    @Override
    public void onPause() {
        super.onPause();
        if (arSceneView != null) {
          arSceneView.pause();
        }

        stopBackgroundThread();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (arSceneView != null) {
          arSceneView.destroy();
        }
    }

    @Override
    public void onRequestPermissionsResult( int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        if (!DemoUtils.hasCameraPermission(this)) {
            if (!DemoUtils.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                DemoUtils.launchPermissionSettings(this);
            } else {
                Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG).show();
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Standard Android full-screen functionality.
            getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /**
    * 탭 되었을때
    * */
    private void onSingleTap(MotionEvent tap) {
        if (!hasFinishedLoading) {
            // We can't do anything yet.
            return;
        }

        Frame frame = arSceneView.getArFrame();
        if (frame != null) {
            if (!hasPlacedSolarSystem && tryPlaceSolarSystem(tap, frame)) {
                hasPlacedSolarSystem = true;
            }
        }
    }

    /**
    * 객체 하나 생성
    * */
    private boolean tryPlaceSolarSystem(MotionEvent tap, Frame frame) {
        if (tap != null && frame.getCamera().getTrackingState() == TrackingState.TRACKING) {
            for (HitResult hit : frame.hitTest(tap)) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    // Create the Anchor.
                    Anchor anchor = hit.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setParent(arSceneView.getScene());
                    Node chair = createChair();
                    anchorNode.addChild(chair);
                    return true;
                }
            }
        }

        return false;
    }

    private Node createChair(){

        Node base = new Node();

        Node sun = new Node();
        sun.setParent(base);
        sun.setLocalPosition(new Vector3(0.0f, 0.5f, 0.0f));

        Node sunVisual = new Node();
//        Node sunVisual = new TransformableNode(transformationSystem);
        sunVisual.setParent(sun);

//        ModelRenderable.builder()
//                .setSource(this, Uri.parse("Sol.sfb"))
//                .build()
//                .thenAccept(renderable -> sunRenderable = renderable)
//                .exceptionally(
//                        throwable -> {
//                            Toast toast =
//                                    Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG);
//                            toast.setGravity(Gravity.CENTER, 0, 0);
//                            toast.show();
//                            return null;
//                        }
//                );
        sunVisual.setRenderable(sunRenderable);
        sunVisual.setLocalScale(new Vector3(0.5f, 0.5f, 0.5f));


        Node infoCard = new Node();
        infoCard.setParent(sun);
        infoCard.setRenderable(solarControlsRenderable);
        infoCard.setLocalScale(new Vector3(0.5f, 0.5f, 0.5f));
        infoCard.setLocalPosition(new Vector3(0.0f, 0.25f, 0.0f));

        View solarControlsView = solarControlsRenderable.getView();

        TextView tv = solarControlsView.findViewById(R.id.tv);
        SeekBar sb = solarControlsView.findViewById(R.id.sb);

        sb.setProgress(1000);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tv.setText(progress+"원");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

//        sunVisual.setOnTapListener(
//                ((hitTestResult, motionEvent) -> {
//                    infoCard.setEnabled(!infoCard.isEnabled());
//                })
//        );




        return base;
    }

    /**
     * ImageReader.OnImageAvailableListener 상속받은거
     * */
    @Override
    public void onImageAvailable(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if(image == null) {
            Log.w(TAG, "onImageAvailable: Skipping null Image.");
            return;
        }

        image.close();
        cpuImagesProcessed++;

        // Reduce the screen update to once every two seconds with 30fps if running as automated test.
//        if (!automatorRun.get() || (automatorRun.get() && cpuImagesProcessed % 50 == 0)) {
//            runOnUiThread(
//                    () ->
//                            statusTextView.setText(
//                                    "CPU images processed: "
//                                            + cpuImagesProcessed
//                                            + "\n\nMode: "
//                                            + (arMode ? "AR" : "non-AR")
//                                            + " \nARCore active: "
//                                            + arcoreActive
//                                            + " \nShould update surface texture: "
//                                            + shouldUpdateSurfaceTexture.get()));
//        }
    }

    /**
     * Creates the transformation system used by this fragment. Can be overridden to create a custom
     * transformation system.
     */
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    protected TransformationSystem makeTransformationSystem() {
        FootprintSelectionVisualizer selectionVisualizer = new FootprintSelectionVisualizer();

        TransformationSystem transformationSystem =
                new TransformationSystem(getResources().getDisplayMetrics(), selectionVisualizer);

       /* ModelRenderable.builder()
                .setSource(SolarActivity.this, R.raw.sceneform_footprint)
                .build()
                .thenAccept(
                        renderable -> {
                            // If the selection visualizer already has a footprint renderable, then it was set to
                            // something custom. Don't override the custom visual.
                            if (selectionVisualizer.getFootprintRenderable() == null) {
                                selectionVisualizer.setFootprintRenderable(renderable);
                            }
                        })
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(
                                            this, "Unable to load footprint renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });*/

        return transformationSystem;
    }


    public Bitmap getBitmapFromURL(String src) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(src);
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);
            return myBitmap;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }



//  private Node createSolarSystem() {
//    Node base = new Node();
//
//    Node sun = new Node();
//    sun.setParent(base);
//    sun.setLocalPosition(new Vector3(0.0f, 0.5f, 0.0f));
//
//    Node sunVisual = new Node();
//    sunVisual.setParent(sun);
//    sunVisual.setRenderable(sunRenderable);
//    sunVisual.setLocalScale(new Vector3(0.5f, 0.5f, 0.5f));
//
//    Node solarControls = new Node();
//    solarControls.setParent(sun);
//    solarControls.setRenderable(solarControlsRenderable);
//    solarControls.setLocalPosition(new Vector3(0.0f, 0.25f, 0.0f));
//
//    View solarControlsView = solarControlsRenderable.getView();
//    SeekBar orbitSpeedBar = solarControlsView.findViewById(R.id.orbitSpeedBar);
//    orbitSpeedBar.setProgress((int) (solarSettings.getOrbitSpeedMultiplier() * 10.0f));
//    orbitSpeedBar.setOnSeekBarChangeListener(
//        new SeekBar.OnSeekBarChangeListener() {
//          @Override
//          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//            float ratio = (float) progress / (float) orbitSpeedBar.getMax();
//            solarSettings.setOrbitSpeedMultiplier(ratio * 10.0f);
//          }
//
//          @Override
//          public void onStartTrackingTouch(SeekBar seekBar) {}
//
//          @Override
//          public void onStopTrackingTouch(SeekBar seekBar) {}
//        });
//
//    SeekBar rotationSpeedBar = solarControlsView.findViewById(R.id.rotationSpeedBar);
//    rotationSpeedBar.setProgress((int) (solarSettings.getRotationSpeedMultiplier() * 10.0f));
//    rotationSpeedBar.setOnSeekBarChangeListener(
//        new SeekBar.OnSeekBarChangeListener() {
//          @Override
//          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//            float ratio = (float) progress / (float) rotationSpeedBar.getMax();
//            solarSettings.setRotationSpeedMultiplier(ratio * 10.0f);
//          }
//
//          @Override
//          public void onStartTrackingTouch(SeekBar seekBar) {}
//
//          @Override
//          public void onStopTrackingTouch(SeekBar seekBar) {}
//        });
//
//    // Toggle the solar controls on and off by tapping the sun.
//    sunVisual.setOnTapListener(
//        (hitTestResult, motionEvent) -> solarControls.setEnabled(!solarControls.isEnabled()));
//
//    createPlanet("Mercury", sun, 0.4f, 47f, mercuryRenderable, 0.019f, 0.03f);
//
//    createPlanet("Venus", sun, 0.7f, 35f, venusRenderable, 0.0475f, 2.64f);
//
//    Node earth = createPlanet("Earth", sun, 1.0f, 29f, earthRenderable, 0.05f, 23.4f);
//
//    createPlanet("Moon", earth, 0.15f, 100f, lunaRenderable, 0.018f, 6.68f);
//
//    createPlanet("Mars", sun, 1.5f, 24f, marsRenderable, 0.0265f, 25.19f);
//
//    createPlanet("Jupiter", sun, 2.2f, 13f, jupiterRenderable, 0.16f, 3.13f);
//
//    createPlanet("Saturn", sun, 3.5f, 9f, saturnRenderable, 0.1325f, 26.73f);
//
//    createPlanet("Uranus", sun, 5.2f, 7f, uranusRenderable, 0.1f, 82.23f);
//
//    createPlanet("Neptune", sun, 6.1f, 5f, neptuneRenderable, 0.074f, 28.32f);
//
//    return base;
//  }

  private Node createPlanet(
      String name,
      Node parent,
      float auFromParent,
      float orbitDegreesPerSecond,
      ModelRenderable renderable,
      float planetScale,
      float axisTilt) {
    // Orbit is a rotating node with no renderable positioned at the sun.
    // The planet is positioned relative to the orbit so that it appears to rotate around the sun.
    // This is done instead of making the sun rotate so each planet can orbit at its own speed.
    RotatingNode orbit = new RotatingNode(solarSettings, true, false, 0);
    orbit.setDegreesPerSecond(orbitDegreesPerSecond);
    orbit.setParent(parent);

    // Create the planet and position it relative to the sun.
    Planet planet =
        new Planet(
            this, name, planetScale, orbitDegreesPerSecond, axisTilt, renderable, solarSettings);
    planet.setParent(orbit);
    planet.setLocalPosition(new Vector3(auFromParent * AU_TO_METERS, 0.0f, 0.0f));

    return planet;
  }

  private void showLoadingMessage() {
    if (loadingMessageSnackbar !=

            null && loadingMessageSnackbar.isShownOrQueued()) {
      return;
    }

    loadingMessageSnackbar =
        Snackbar.make(
            SolarActivity.this.findViewById(android.R.id.content),
            R.string.plane_finding,
            Snackbar.LENGTH_INDEFINITE);
    loadingMessageSnackbar.getView().setBackgroundColor(0xbf323232);
    loadingMessageSnackbar.show();
  }

  private void hideLoadingMessage() {
    if (loadingMessageSnackbar == null) {
      return;
    }

    loadingMessageSnackbar.dismiss();
    loadingMessageSnackbar = null;
  }


}
